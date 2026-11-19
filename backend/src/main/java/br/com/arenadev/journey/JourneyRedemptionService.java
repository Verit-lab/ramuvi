package br.com.arenadev.journey;

import br.com.arenadev.classroom.Student;
import br.com.arenadev.classroom.StudentRepository;
import br.com.arenadev.study.StudentJourneyAccess;
import br.com.arenadev.study.StudentJourneyAccessRepository;
import br.com.arenadev.study.StudyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/**
 * Journey codes ("J-XXXXXX") are a shared distribution hint, NEVER an identity proof.
 * The former public bootstrap (code + typed name/registration/email -> 180-day
 * device claim) was removed in v0.12.1 P0 (ADR-0075): it allowed anyone holding the
 * shared code to impersonate a classmate. Redemption now always requires an
 * existing study principal; new students enter through /learn (OIDC + link/invite).
 */
@Service
public class JourneyRedemptionService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final JourneyRepository journeys;
    private final JourneyRedemptionCodeRepository codes;
    private final StudentJourneyAccessRepository accesses;
    private final StudentRepository students;

    public JourneyRedemptionService(
            JourneyRepository journeys,
            JourneyRedemptionCodeRepository codes,
            StudentJourneyAccessRepository accesses,
            StudentRepository students
    ) {
        this.journeys = journeys;
        this.codes = codes;
        this.accesses = accesses;
        this.students = students;
    }

    @Transactional
    public CodeView enable(UUID journeyId) {
        Journey journey = requirePublished(journeyId);
        requireRedeemable(journey);
        JourneyRedemptionCode entity = codes.findByJourneyId(journeyId)
                .orElseGet(() -> new JourneyRedemptionCode(journey, nextCode()));
        if (!entity.isActive()) entity.regenerate(nextCode());
        return view(codes.save(entity));
    }

    @Transactional
    public CodeView regenerate(UUID journeyId) {
        Journey journey = requirePublished(journeyId);
        requireRedeemable(journey);
        JourneyRedemptionCode entity = codes.findByJourneyId(journeyId)
                .orElseGet(() -> new JourneyRedemptionCode(journey, nextCode()));
        entity.regenerate(nextCode());
        return view(codes.save(entity));
    }

    @Transactional
    public void disable(UUID journeyId) {
        codes.findByJourneyId(journeyId).ifPresent(JourneyRedemptionCode::disable);
    }

    @Transactional(readOnly = true)
    public CodeView status(UUID journeyId) {
        return codes.findByJourneyId(journeyId).map(this::view).orElse(new CodeView(null, false, 0));
    }

    @Transactional(readOnly = true)
    public RedemptionPreviewView preview(String rawCode) {
        Journey journey = requireActiveRedemption(rawCode).getJourney();
        requirePublished(journey.getId());
        requireRedeemable(journey);
        return new RedemptionPreviewView(
                journey.getId(),
                journey.getTitle(),
                journey.getClassroom().getId(),
                journey.getClassroom().getName()
        );
    }

    @Transactional
    public RedemptionView redeem(String rawCode, StudyPrincipal principal) {
        JourneyRedemptionCode redemption = requireActiveRedemption(rawCode);
        Journey journey = redemption.getJourney();
        requirePublished(journey.getId());
        requireRedeemable(journey);
        Student student = students.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Perfil do aluno não encontrado."));
        return redeemForStudent(journey, student);
    }

    private RedemptionView redeemForStudent(Journey journey, Student student) {
        boolean existed = accesses.findByStudentIdAndJourneyId(student.getId(), journey.getId()).isPresent();
        accesses.findByStudentIdAndJourneyId(student.getId(), journey.getId())
                .orElseGet(() -> accesses.save(new StudentJourneyAccess(student, journey)));
        return new RedemptionView(journey.getId(), journey.getTitle(), journey.getDescription(), existed);
    }

    private JourneyRedemptionCode requireActiveRedemption(String rawCode) {
        String code = normalizeCode(rawCode);
        return codes.findByCodeIgnoreCaseAndActiveTrue(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Código de Jornada inválido ou desativado."
                ));
    }

    private Journey requirePublished(UUID id) {
        Journey journey = journeys.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jornada não encontrada."));
        if (journey.getStatus() != JourneyStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A Jornada precisa estar publicada.");
        }
        return journey;
    }

    private void requireRedeemable(Journey journey) {
        if (journey.getStages().stream().anyMatch(stage -> stage.getCompletionRule() != JourneyCompletionRule.MANUAL)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Jornadas com conclusão dependente de atividades da turma ainda não podem ser distribuídas por código."
            );
        }
    }

    private String nextCode() {
        String value;
        do {
            StringBuilder builder = new StringBuilder("J-");
            for (int index = 0; index < 6; index++) {
                builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            value = builder.toString();
        } while (codes.existsByCodeIgnoreCase(value));
        return value;
    }

    private static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private CodeView view(JourneyRedemptionCode code) {
        return new CodeView(
                code.getCode(),
                code.isActive(),
                (int) accesses.countByJourneyId(code.getJourney().getId())
        );
    }

    public record CodeView(String code, boolean active, int redeemedCount) {}
    public record RedemptionView(UUID journeyId, String title, String description, boolean alreadyAdded) {}
    public record RedemptionPreviewView(UUID journeyId, String title, UUID classroomId, String classroomName) {}
}
