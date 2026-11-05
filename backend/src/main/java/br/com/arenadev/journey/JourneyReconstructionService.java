package br.com.arenadev.journey;

import br.com.arenadev.content.LearningResource;
import br.com.arenadev.content.LearningResourceFormat;
import br.com.arenadev.content.LearningResourceRepository;
import br.com.arenadev.content.LearningResourceSourceType;
import br.com.arenadev.content.ingestion.ContentIngestionService;
import br.com.arenadev.content.ingestion.ContentIngestionSource;
import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rebuilds a Journey's structure in-place, preserving its identity and matched
 * stage identities. Never deletes student study history or mutates shared
 * Library resources. The entire operation is transactional.
 */
@Service
public class JourneyReconstructionService {
    private final JourneyRepository journeys;
    private final JourneyStageProgressRepository progress;
    private final JourneyStageRepository stages;
    private final LearningResourceRepository resources;
    private final ContentIngestionService ingestion;
    private final JourneyService journeyService;
    private final JourneyOverwriteSnapshotService snapshots;

    public JourneyReconstructionService(
            JourneyRepository journeys,
            JourneyStageProgressRepository progress,
            JourneyStageRepository stages,
            LearningResourceRepository resources,
            ContentIngestionService ingestion,
            JourneyService journeyService,
            JourneyOverwriteSnapshotService snapshots
    ) {
        this.journeys = journeys;
        this.progress = progress;
        this.stages = stages;
        this.resources = resources;
        this.ingestion = ingestion;
        this.journeyService = journeyService;
        this.snapshots = snapshots;
    }

    public record ReconstructionPreview(
            ContentIngestionService.DraftView draft,
            int previousModules,
            int previousContents,
            int newModules,
            int newContents,
            int reusedContents,
            int addedContents,
            int removedContents,
            int changedBodies,
            int detachedLibraryLinks,
            boolean blockedByProgress,
            boolean canApply,
            List<String> removedTitles,
            boolean releaseLimitAdjusted
    ) {}

    /** Full overwrite: all new stage IDs and a fresh learning path, but same journey ID. */
    public record OverwritePreview(
            ContentIngestionService.DraftView draft,
            String confirmationTitle,
            int previousModules,
            int previousContents,
            int newModules,
            int newContents,
            int enrollmentProgressToReset,
            int redeemedProgressToReset,
            int linkedActivitiesToDetach,
            boolean published
    ) {}

    public record OverwriteInput(String markdown, java.time.Instant expectedUpdatedAt, String confirmationTitle) {}
    public record OverwriteResult(JourneyService.JourneyView journey, UUID snapshotId) {}

    @Transactional(readOnly = true)
    public OverwritePreview overwritePreview(UUID journeyId, JourneyService.MarkdownInput input) {
        Journey journey = requireJourney(journeyId);
        validateVersion(journey, input);
        Plan parsed = buildPlan(journey, input.markdown());
        return describeOverwrite(journey, parsed.preview().draft());
    }

    /**
     * Deliberately destructive to the active study path. This starts everyone
     * from the beginning but preserves a transactionally consistent historical
     * snapshot (stage content plus both forms of student progress).
     */
    @Transactional
    public OverwriteResult overwrite(UUID journeyId, OverwriteInput input) {
        Journey journey = journeys.lockForOverwrite(journeyId).orElseThrow(
                () -> new ResourceNotFoundException("Jornada não encontrada."));
        if (input == null) throw new IllegalArgumentException("Solicitação de substituição obrigatória.");
        validateVersion(journey, new JourneyService.MarkdownInput(input.markdown(), input.expectedUpdatedAt()));
        if (!journey.getTitle().equals(input.confirmationTitle())) {
            throw new IllegalArgumentException("Digite o título completo e exato da jornada para confirmar a substituição.");
        }
        Plan plan = buildPlan(journey, input.markdown());
        var draft = plan.preview().draft();

        List<UUID> oldIds = journey.getStages().stream().map(JourneyStage::getId)
                .filter(Objects::nonNull).sorted().toList();
        if (!oldIds.isEmpty() && stages.lockForReconstruction(oldIds).size() != oldIds.size()) {
            throw new IllegalStateException("A estrutura mudou. Reabra a jornada antes de sobrescrever.");
        }
        UUID snapshotId = snapshots.capture(journeyId);

        journey.getStages().clear();
        journey.getSections().clear();
        int position = 0;
        int modulePosition = 0;
        for (var sectionInput : draft.sections()) {
            JourneySection section = new JourneySection(
                    sectionInput.title(), sectionInput.description(), modulePosition++);
            journey.addSection(section);
            for (var item : sectionInput.items()) {
                JourneyStage stage = new JourneyStage(item.title(), position);
                stage.updateConfiguration(item.title(), item.description(), position,
                        null, position == 0 ? JourneyUnlockRule.IMMEDIATE
                                : JourneyUnlockRule.PREVIOUS_STAGE_COMPLETED,
                        JourneyCompletionRule.MANUAL, section, JourneyItemType.CONTENT);
                LearningResource resource = new LearningResource(journey.getClassroom(), item.title());
                resource.update(item.title(), item.description(), item.body().strip(),
                        LearningResourceFormat.MARKDOWN, LearningResourceSourceType.MARKDOWN,
                        null, "Substituição integral da jornada");
                stage.materializeResource(resources.save(resource));
                journey.addStage(stage);
                position++;
            }
        }
        // A new path must not inherit a teacher release gate from the old one.
        journey.changeReleasedChapterCount(null);
        journey.update(journey.getTitle(), journey.getDescription());
        journeys.saveAndFlush(journey);
        return new OverwriteResult(journeyService.get(journeyId), snapshotId);
    }

    private OverwritePreview describeOverwrite(Journey journey, ContentIngestionService.DraftView draft) {
        var counts = snapshots.counts(journey.getId());
        return new OverwritePreview(
                draft,
                journey.getTitle(),
                journey.getChapterCount(),
                journey.getStages().size(),
                draft.sections().size(),
                draft.sections().stream().mapToInt(section -> section.items().size()).sum(),
                counts.enrollment(), counts.redeemed(),
                (int) journey.getStages().stream().filter(stage -> stage.getActivity() != null).count(),
                journey.getStatus() == JourneyStatus.PUBLISHED
        );
    }

    private record Plan(
            ReconstructionPreview preview,
            Map<ContentIngestionService.DraftItem, JourneyStage> matches,
            List<JourneyStage> removed
    ) {}

    @Transactional(readOnly = true)
    public ReconstructionPreview preview(UUID journeyId, JourneyService.MarkdownInput input) {
        Journey journey = requireJourney(journeyId);
        validateVersion(journey, input);
        return buildPlan(journey, input.markdown()).preview();
    }

    @Transactional
    public JourneyService.JourneyView rebuild(UUID journeyId, JourneyService.MarkdownInput input) {
        Journey journey = requireJourney(journeyId);
        validateVersion(journey, input);
        Plan plan = buildPlan(journey, input.markdown());
        if (!plan.preview().canApply()) {
            throw new IllegalStateException("A reconstrução removeria etapas com progresso de alunos ou atividades vinculadas. " +
                    "A operação foi bloqueada para proteger os dados. Use a atualização de textos ou preserve essas etapas.");
        }

        // Lock rows being deleted so a simultaneous student progress INSERT
        // cannot appear between the preview/check and the transaction commit.
        List<UUID> removedIds = plan.removed().stream().map(JourneyStage::getId)
                .filter(Objects::nonNull).sorted().toList();
        if (!removedIds.isEmpty()) {
            if (stages.lockForReconstruction(removedIds).size() != removedIds.size()) {
                throw new IllegalStateException("A estrutura mudou durante a reconstrução. Atualize a jornada.");
            }
            if (progress.anyStudyStarted(removedIds)) {
                throw new IllegalStateException("Alunos iniciaram conteúdos que seriam removidos. Reabra a prévia; nenhuma alteração foi aplicada.");
            }
        }

        var draft = plan.preview().draft();
        Map<String, JourneySection> existingSections = new HashMap<>();
        for (JourneySection section : journey.getSections()) {
            existingSections.putIfAbsent(key(section.getTitle()), section);
        }
        Set<JourneySection> retainedSections = new HashSet<>();
        int position = 0;
        int sectionPosition = 0;
        for (var draftSection : draft.sections()) {
            JourneySection section = existingSections.get(key(draftSection.title()));
            if (section == null || retainedSections.contains(section)) {
                section = new JourneySection(draftSection.title(), draftSection.description(), sectionPosition);
                journey.addSection(section);
            } else {
                section.update(draftSection.title(), draftSection.description(), sectionPosition);
            }
            retainedSections.add(section);
            sectionPosition++;

            for (var item : draftSection.items()) {
                JourneyStage stage = plan.matches().get(item);
                boolean existing = stage != null;
                if (!existing) {
                    stage = new JourneyStage(item.title(), position);
                    journey.addStage(stage);
                }
                var rule = position == 0 ? JourneyUnlockRule.IMMEDIATE
                        : JourneyUnlockRule.PREVIOUS_STAGE_COMPLETED;
                stage.updateConfiguration(
                        item.title(), item.description(), position,
                        existing ? stage.getActivity() : null,
                        rule,
                        existing ? stage.getCompletionRule() : JourneyCompletionRule.MANUAL,
                        section,
                        existing ? stage.getItemType() : JourneyItemType.CONTENT
                );
                String desiredBody = item.body() == null ? "" : item.body().strip();
                String previousBody = stage.getResourceSnapshotBody() == null ? "" : stage.getResourceSnapshotBody().strip();
                if (!existing || !Objects.equals(desiredBody, previousBody)
                        || stage.getLearningResource() == null
                        || stage.getResourceSnapshotFormat() == null
                        || !stage.getResourceSnapshotFormat().equals(LearningResourceFormat.MARKDOWN.name())) {
                    LearningResource isolated = new LearningResource(journey.getClassroom(), item.title());
                    isolated.update(item.title(), item.description(), desiredBody,
                            LearningResourceFormat.MARKDOWN, LearningResourceSourceType.MARKDOWN,
                            null, "Reconstrução de jornada");
                    stage.materializeResource(resources.save(isolated));
                }
                position++;
            }
        }

        // Removed stages are guaranteed not to have progress or linked activity.
        plan.removed().forEach(journey::removeStage);
        journey.getStages().sort(Comparator.comparingInt(JourneyStage::getPosition));
        journey.getSections().removeIf(section -> !retainedSections.contains(section));
        journey.getSections().sort(Comparator.comparingInt(JourneySection::getPosition));
        if (journey.getReleasedChapterCount() != null &&
                journey.getReleasedChapterCount() > draft.sections().size()) {
            journey.changeReleasedChapterCount(draft.sections().size());
        }
        journey.update(journey.getTitle(), journey.getDescription());
        journeys.saveAndFlush(journey);
        return journeyService.get(journeyId);
    }

    private Plan buildPlan(Journey journey, String markdown) {
        var draft = ingestion.preview(new ContentIngestionService.PreviewRequest(
                journey.getClassroom().getId(), ContentIngestionSource.MARKDOWN,
                journey.getTitle(), journey.getDescription(), null, markdown, null,
                "Reconstrução da própria jornada"));
        List<ContentIngestionService.DraftItem> items = draft.sections().stream()
                .flatMap(section -> section.items().stream()).toList();
        if (items.isEmpty() || draft.sections().isEmpty()) {
            throw new IllegalArgumentException("O Markdown precisa conter módulos e conteúdos reconhecíveis.");
        }
        if (draft.sections().size() > 200 || items.size() > 2000) {
            throw new IllegalArgumentException("A estrutura ultrapassa o limite de módulos ou conteúdos.");
        }
        if (items.stream().anyMatch(item -> item.title() == null || item.title().isBlank()
                || item.title().length() > 180 || item.body() == null || item.body().isBlank())
                || draft.sections().stream().anyMatch(section ->
                   section.title() == null || section.title().isBlank() || section.title().length() > 180)) {
            throw new IllegalArgumentException("Todos os módulos e conteúdos precisam de título válido e texto não vazio.");
        }

        Map<String, Long> oldFrequency = journey.getStages().stream()
                .collect(Collectors.groupingBy(stage -> key(stage.getTitle()), Collectors.counting()));
        Map<String, Long> newFrequency = items.stream()
                .collect(Collectors.groupingBy(item -> key(item.title()), Collectors.counting()));
        Map<String, JourneyStage> uniqueOld = new HashMap<>();
        for (JourneyStage stage : journey.getStages()) {
            if (oldFrequency.get(key(stage.getTitle())) == 1L) uniqueOld.put(key(stage.getTitle()), stage);
        }
        Map<ContentIngestionService.DraftItem, JourneyStage> matches = new IdentityHashMap<>();
        Set<JourneyStage> reused = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var item : items) {
            String label = key(item.title());
            if (newFrequency.get(label) != 1L) continue;
            JourneyStage stage = uniqueOld.get(label);
            if (stage != null && reused.add(stage)) matches.put(item, stage);
        }
        List<JourneyStage> removed = journey.getStages().stream().filter(stage -> !reused.contains(stage)).toList();
        List<UUID> ids = removed.stream().map(JourneyStage::getId).filter(Objects::nonNull).toList();
        boolean blocked = removed.stream().anyMatch(stage -> stage.getActivity() != null)
                || (!ids.isEmpty() && progress.anyStudyStarted(ids));
        int changes = 0;
        int detached = 0;
        for (var entry : matches.entrySet()) {
            JourneyStage stage = entry.getValue();
            var item = entry.getKey();
            String desired = item.body().strip();
            String old = stage.getResourceSnapshotBody() == null ? "" : stage.getResourceSnapshotBody().strip();
            if (!desired.equals(old)) {
                changes++;
                if (stage.getLearningResource() != null && stage.getLearningResource().isLibraryVisible()) detached++;
            }
        }
        int oldModules = journey.getChapterCount();
        int newModules = draft.sections().size();
        var preview = new ReconstructionPreview(draft, oldModules, journey.getStages().size(),
                newModules, items.size(), matches.size(), items.size() - matches.size(),
                removed.size(), changes, detached, blocked, !blocked,
                removed.stream().map(JourneyStage::getTitle).limit(15).toList(),
                journey.getReleasedChapterCount() != null && journey.getReleasedChapterCount() > newModules);
        return new Plan(preview, matches, removed);
    }

    private void validateVersion(Journey journey, JourneyService.MarkdownInput input) {
        if (journey.getStatus() == JourneyStatus.ARCHIVED) {
            throw new IllegalStateException("Jornadas arquivadas não podem ser reconstruídas.");
        }
        if (input == null || input.markdown() == null || input.markdown().isBlank()) {
            throw new IllegalArgumentException("Forneça o Markdown completo para reconstruir.");
        }
        if (input.expectedUpdatedAt() == null || !input.expectedUpdatedAt().equals(journey.getUpdatedAt())) {
            throw new IllegalStateException("A jornada mudou desde a abertura do editor. Reabra o Markdown.");
        }
    }

    private Journey requireJourney(UUID id) {
        return journeys.findById(id).orElseThrow(() -> new ResourceNotFoundException("Jornada não encontrada."));
    }

    private static String key(String value) {
        String cleaned = value == null ? "" : value.replace("\\|", "|")
                .replaceAll("<[^>]+>", "").strip().replaceAll("\\s+", " ");
        return Normalizer.normalize(cleaned, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
