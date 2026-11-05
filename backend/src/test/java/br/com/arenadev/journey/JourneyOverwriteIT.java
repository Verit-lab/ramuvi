package br.com.arenadev.journey;

import br.com.arenadev.classroom.*;
import br.com.arenadev.support.SharedPostgres;
import br.com.arenadev.study.StudentJourneyAccess;
import br.com.arenadev.study.StudentJourneyAccessRepository;
import br.com.arenadev.study.StudentJourneyStageProgress;
import br.com.arenadev.study.StudentJourneyStageProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class JourneyOverwriteIT {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, JourneyOverwriteIT.class);
    }

    @Autowired private ClassroomRepository classrooms;
    @Autowired private StudentRepository students;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private JourneyRepository journeys;
    @Autowired private JourneyStageRepository stages;
    @Autowired private JourneyService journeyService;
    @Autowired private JourneyStageProgressRepository progress;
    @Autowired private StudentJourneyAccessRepository studentAccess;
    @Autowired private StudentJourneyStageProgressRepository redeemedProgress;
    @Autowired private JourneyReconstructionService reconstruction;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void safelyOverwritesPublishedJourneyAndArchivesStudentProgressInPostgres() {
        Classroom classroom = classrooms.save(new Classroom("Turma de Teste", "OWR-TEST"));
        Student student = students.save(new Student("OWR-001", "Aluno de Teste", "Estudante"));
        Enrollment enrollment = enrollments.save(new Enrollment(classroom, student));

        JourneyService.JourneyView current = journeyService.create(new JourneyService.JourneyInput(
                classroom.getId(), "Portaria Digital", null, List.of(
                stage("Etapa 00", "Módulo antigo"),
                stage("Etapa 01", "Módulo antigo"))));
        current = journeyService.publish(current.id());

        current = journeyService.get(current.id());
        UUID oldStageId = current.stages().getFirst().id();
        JourneyStageProgress recorded = new JourneyStageProgress(
                newStageReference(oldStageId), enrollment);
        recorded.complete(Instant.now());
        progress.saveAndFlush(recorded);
        var access = studentAccess.saveAndFlush(new StudentJourneyAccess(student, journeys.findById(current.id()).orElseThrow()));
        var redeemed = new StudentJourneyStageProgress(stages.findById(oldStageId).orElseThrow(), access);
        redeemed.complete(Instant.now());
        redeemedProgress.saveAndFlush(redeemed);

        String replacement = """
                # Portaria Digital
                ## Novo módulo
                ### Nova etapa
                Conteúdo completamente revisado, com outro percurso.
                """;
        var input = new JourneyService.MarkdownInput(replacement, current.updatedAt());
        var preview = reconstruction.overwritePreview(current.id(), input);
        assertThat(preview.enrollmentProgressToReset()).isEqualTo(1);
        assertThat(preview.redeemedProgressToReset()).isEqualTo(1);
        assertThat(preview.previousContents()).isEqualTo(2);
        assertThat(preview.newContents()).isEqualTo(1);

        var replaced = reconstruction.overwrite(current.id(),
                new JourneyReconstructionService.OverwriteInput(
                        replacement, current.updatedAt(), "Portaria Digital"));

        assertThat(replaced.journey().id()).isEqualTo(current.id());
        assertThat(replaced.journey().status()).isEqualTo(JourneyStatus.PUBLISHED);
        assertThat(replaced.journey().stages()).hasSize(1);
        assertThat(replaced.journey().stages().getFirst().id()).isNotEqualTo(oldStageId);
        assertThat(replaced.journey().stages().getFirst().title()).isEqualTo("Nova etapa");
        assertThat(progress.findByStageJourneyIdAndEnrollmentId(current.id(), enrollment.getId())).isEmpty();
        assertThat(redeemedProgress.findByStageJourneyIdAndAccessId(current.id(), access.getId())).isEmpty();
        assertThat(studentAccess.findById(access.getId())).isPresent();

        Integer archives = jdbc.queryForObject(
                "select count(*) from journey_overwrite_snapshots where id = ?",
                Integer.class, replaced.snapshotId());
        assertThat(archives).isEqualTo(1);
        Integer oldContents = jdbc.queryForObject(
                "select jsonb_array_length(snapshot->'stages') from journey_overwrite_snapshots where id = ?",
                Integer.class, replaced.snapshotId());
        Integer oldProgress = jdbc.queryForObject(
                "select jsonb_array_length(snapshot->'enrollmentProgress') from journey_overwrite_snapshots where id = ?",
                Integer.class, replaced.snapshotId());
        assertThat(oldContents).isEqualTo(2);
        assertThat(oldProgress).isEqualTo(1);
        Integer oldRedeemed = jdbc.queryForObject(
                "select jsonb_array_length(snapshot->'redeemedProgress') from journey_overwrite_snapshots where id = ?",
                Integer.class, replaced.snapshotId());
        assertThat(oldRedeemed).isEqualTo(1);
    }

    private JourneyStage newStageReference(UUID id) {
        return stages.findById(id).orElseThrow();
    }

    private JourneyService.StageInput stage(String title, String module) {
        return new JourneyService.StageInput(
                null, title, null, null, JourneyUnlockRule.PREVIOUS_STAGE_COMPLETED,
                JourneyCompletionRule.MANUAL, null, module, JourneyItemType.CONTENT, null);
    }
}
