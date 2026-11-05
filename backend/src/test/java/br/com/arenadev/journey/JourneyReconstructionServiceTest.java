package br.com.arenadev.journey;

import br.com.arenadev.classroom.Classroom;
import br.com.arenadev.content.LearningResource;
import br.com.arenadev.content.LearningResourceFormat;
import br.com.arenadev.content.LearningResourceRepository;
import br.com.arenadev.content.LearningResourceSourceType;
import br.com.arenadev.content.ingestion.ContentIngestionService;
import br.com.arenadev.content.ingestion.ContentIngestionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JourneyReconstructionServiceTest {
    private final JourneyRepository journeys = mock(JourneyRepository.class);
    private final JourneyStageProgressRepository progress = mock(JourneyStageProgressRepository.class);
    private final JourneyStageRepository stages = mock(JourneyStageRepository.class);
    private final LearningResourceRepository resources = mock(LearningResourceRepository.class);
    private final ContentIngestionService ingestion = mock(ContentIngestionService.class);
    private final JourneyService journeyService = mock(JourneyService.class);
    private final JourneyReconstructionService service =
            new JourneyReconstructionService(journeys, progress, stages, resources, ingestion, journeyService);

    private final UUID journeyId = UUID.randomUUID();
    private final Classroom classroom = mock(Classroom.class);
    private Journey journey;
    private JourneyStage kept;
    private JourneyStage removed;
    private LearningResource original;

    @BeforeEach
    void setup() {
        when(classroom.getId()).thenReturn(UUID.randomUUID());
        journey = new Journey(classroom, "Portaria Digital");
        ReflectionTestUtils.setField(journey, "id", journeyId);
        JourneySection section = new JourneySection("Módulo antigo", "Introdução", 0);
        journey.addSection(section);
        kept = stage("Etapa 00 · Git", 0, section, "Texto antigo");
        removed = stage("Etapa 01 · Janela", 1, section, "Outra etapa");
        original = kept.getLearningResource();
        journey.publish();
        when(journeys.findById(journeyId)).thenReturn(Optional.of(journey));
        when(resources.save(any(LearningResource.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stages.lockForReconstruction(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<UUID> ids = invocation.getArgument(0);
            return journey.getStages().stream().filter(stage -> ids.contains(stage.getId())).toList();
        });
        when(ingestion.preview(any())).thenReturn(draft());
    }

    private JourneyStage stage(String title, int position, JourneySection section, String text) {
        JourneyStage stage = new JourneyStage(title, position);
        ReflectionTestUtils.setField(stage, "id", UUID.randomUUID());
        stage.updateConfiguration(title, null, position, null,
                position == 0 ? JourneyUnlockRule.IMMEDIATE : JourneyUnlockRule.PREVIOUS_STAGE_COMPLETED,
                JourneyCompletionRule.MANUAL, section, JourneyItemType.CONTENT);
        LearningResource resource = new LearningResource(classroom, title);
        resource.update(title, null, text, LearningResourceFormat.MARKDOWN,
                LearningResourceSourceType.MARKDOWN, null, null);
        stage.materializeResource(resource);
        journey.addStage(stage);
        return stage;
    }

    private ContentIngestionService.DraftView draft() {
        return new ContentIngestionService.DraftView("Portaria Digital", "Nova estrutura",
                ContentIngestionSource.MARKDOWN, "Texto", List.of(
                new ContentIngestionService.DraftSection("Módulo novo", null, List.of(
                        new ContentIngestionService.DraftItem("Etapa 00 · Git", null,
                                br.com.arenadev.content.ingestion.DraftItemType.CONTENT, "Texto atualizado", null),
                        new ContentIngestionService.DraftItem("Etapa 02 · SQLite", null,
                                br.com.arenadev.content.ingestion.DraftItemType.CONTENT, "Aprenda SQL", null)))));
    }

    private JourneyService.MarkdownInput input() {
        return new JourneyService.MarkdownInput("## Módulo novo\n### Etapa 00 · Git\nTexto atualizado", journey.getUpdatedAt());
    }

    @Test
    void reconstructsSamePublishedJourneyAndKeepsMatchedIdsAndLibraryIsolation() {
        original.publishToLibrary();
        var preview = service.preview(journeyId, input());
        assertThat(preview.canApply()).isTrue();
        assertThat(preview.reusedContents()).isEqualTo(1);
        assertThat(preview.addedContents()).isEqualTo(1);
        assertThat(preview.removedContents()).isEqualTo(1);
        assertThat(preview.detachedLibraryLinks()).isEqualTo(1);

        UUID preservedId = kept.getId();
        service.rebuild(journeyId, input());

        assertThat(journey.getId()).isEqualTo(journeyId);
        assertThat(journey.getStatus()).isEqualTo(JourneyStatus.PUBLISHED);
        assertThat(journey.getStages()).hasSize(2);
        assertThat(journey.getStages().getFirst()).isSameAs(kept);
        assertThat(kept.getId()).isEqualTo(preservedId);
        assertThat(kept.getResourceSnapshotBody()).isEqualTo("Texto atualizado");
        assertThat(original.getBody()).isEqualTo("Texto antigo");
        assertThat(journey.getStages().get(1).getTitle()).isEqualTo("Etapa 02 · SQLite");
        assertThat(journey.getSections()).extracting(JourneySection::getTitle).containsExactly("Módulo novo");
        verify(stages).lockForReconstruction(anyCollection());
        verify(journeys).saveAndFlush(journey);
    }

    @Test
    void refusesRebuildWhenRemovedContentHasStudentProgress() {
        when(progress.anyStudyStarted(anyCollection())).thenReturn(true);
        var preview = service.preview(journeyId, input());
        assertThat(preview.canApply()).isFalse();
        assertThatThrownBy(() -> service.rebuild(journeyId, input()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("progresso");
        assertThat(journey.getStages()).hasSize(2);
        assertThat(kept.getResourceSnapshotBody()).isEqualTo("Texto antigo");
        verify(journeys, never()).saveAndFlush(any());
        verify(resources, never()).save(any());
    }

    @Test
    void refusesStalePreviewOrWriteBeforeChangingData() {
        var stale = new JourneyService.MarkdownInput("## Nova", journey.getUpdatedAt().minusSeconds(3600));
        assertThatThrownBy(() -> service.preview(journeyId, stale)).hasMessageContaining("Reabra");
        assertThatThrownBy(() -> service.rebuild(journeyId, stale)).hasMessageContaining("Reabra");
        verifyNoInteractions(ingestion, resources);
    }
}
