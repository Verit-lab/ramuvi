package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StudentSimpleOnboardingIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, StudentSimpleOnboardingIT.class);
    }

    @Autowired StudentAccountService accounts;
    @Autowired StudentService students;
    @Autowired ClassroomService classrooms;
    @Autowired JdbcTemplate jdbc;

    private StudentAccountService.VerifiedIdentity oidc() {
        return new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC,
                "https://accounts.google.com", UUID.randomUUID().toString());
    }
    private String code() { return "ON" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(); }
    private String email() { return UUID.randomUUID().toString().substring(0, 12) + "@escola.example"; }

    @Test
    void verifiedEmailLinksPreexistingTeacherStudentWithoutReplacingEnrollmentAndProgressId() {
        var student = students.create(null, "Ana Existente", null, email());
        var room = classrooms.create("Turma existente", code());
        var enrollment = classrooms.enroll(room.id(), student.id());
        var identity = oidc();
        var accountId = accounts.provisionVerifiedIdentity(identity);
        assertThat(accounts.linkExistingStudentByVerifiedEmail(identity.issuer(), identity.subject(),
                student.email().toUpperCase())).isTrue();
        assertThat(accounts.linkExistingStudentByVerifiedEmail(identity.issuer(), identity.subject(),
                student.email())).isFalse();
        assertThat(accounts.findActiveVerifiedOidcAccount(identity.issuer(), identity.subject()))
                .get().extracting(StudentAccountService.AccountView::studentId).isEqualTo(student.id());
        assertThat(jdbc.queryForObject(
                "select student_id from enrollments where id = ?", UUID.class, enrollment.enrollmentId()))
                .isEqualTo(student.id());
        assertThat(jdbc.queryForObject(
                "select count(*) from students where lower(email) = lower(?)", Integer.class, student.email())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select student_id from student_accounts where id = ?", UUID.class, accountId)).isEqualTo(student.id());
    }

    @Test
    void noNameMatchingOrSelfDeclaredEmailLinking() {
        var student = students.create(null, "Pessoa sem prova", null, email());
        var room = classrooms.create("Turma segura", code());
        classrooms.enroll(room.id(), student.id());
        jdbc.update("update students set email_source = 'STUDENT' where id = ?", student.id());
        var identity = oidc();
        accounts.provisionVerifiedIdentity(identity);
        assertThat(accounts.linkExistingStudentByVerifiedEmail(identity.issuer(), identity.subject(), student.email())).isFalse();
        assertThat(accounts.findActiveVerifiedOidcAccount(identity.issuer(), identity.subject()).get().studentId()).isNull();
        assertThat(accounts.linkExistingStudentByVerifiedEmail(identity.issuer(), identity.subject(), null)).isFalse();
    }

    @Test
    void aTeacherMustOptInAndUseAnAdequateCode() {
        var room = classrooms.create("Código curto", "SHORT1");
        assertThatThrownBy(() -> classrooms.setSelfEnrollmentEnabled(room.id(), true))
                .isInstanceOf(IllegalArgumentException.class);
        var eligible = classrooms.create("Código válido", code());
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), eligible.code(), email(), "Nova Aluna"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(classrooms.setSelfEnrollmentEnabled(eligible.id(), true).selfEnrollmentEnabled()).isTrue();
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), "NOTFOUND000", email(), "Nova Aluna"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(classrooms.setSelfEnrollmentEnabled(eligible.id(), false).selfEnrollmentEnabled()).isFalse();
    }

    @Test
    void sharedCodeLinksExistingTeacherStudentAndIsIdempotent() {
        String email = email();
        var student = students.create(null, "Aluno antes da Ramuvi", null, email);
        var classroom = classrooms.create("Turma ativa", code());
        var enrollment = classrooms.enroll(classroom.id(), student.id());
        classrooms.setSelfEnrollmentEnabled(classroom.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        var first = accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), classroom.code(), email, "Novo nome");
        var replay = accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), classroom.code(), email, "Novo nome");
        assertThat(first.studentId()).isEqualTo(student.id());
        assertThat(first.enrollmentId()).isEqualTo(enrollment.enrollmentId());
        assertThat(replay.enrollmentId()).isEqualTo(enrollment.enrollmentId());
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ?",
                Integer.class, classroom.id())).isEqualTo(1);
    }

    @Test
    void newVerifiedEmailCreatesOnlyOneStudentAndOneEnrollment() {
        var classroom = classrooms.create("Turma novos alunos", code());
        classrooms.setSelfEnrollmentEnabled(classroom.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        String email = email();
        var first = accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), classroom.code(), email, "Lia Teste");
        var replay = accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), classroom.code(), email, "Lia Teste");
        assertThat(first).isEqualTo(replay);
        assertThat(jdbc.queryForObject("select email_source from students where id = ?",
                String.class, first.studentId())).isEqualTo("STUDENT");
        assertThat(jdbc.queryForObject("select email from students where id = ?",
                String.class, first.studentId())).isEqualTo(email);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where student_id = ?",
                Integer.class, first.studentId())).isEqualTo(1);
    }

    @Test
    void inactiveStudentRecordCannotBeResurrectedByMatchingTeacherEmail() {
        var room = classrooms.create("Turma segura inativos", code());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        String email = email();
        var inactive = students.create(null, "Aluno inativo", null, email);
        jdbc.update("update students set active = false where id = ?", inactive.id());
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        assertThat(accounts.linkExistingStudentByVerifiedEmail(
                identity.issuer(), identity.subject(), email)).isFalse();
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), room.code(), email, "Aluno inativo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("professor");
        assertThat(accounts.findActiveVerifiedOidcAccount(identity.issuer(), identity.subject())
                .get().studentId()).isNull();
    }

    @Test
    void noUnverifiedEmailMayCreateNewStudent() {
        var room = classrooms.create("Turma sem email", code());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), room.code(), null, "Anônimo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualNameOnlyRecordMustBeResolvedInsteadOfDuplicatingStudentWithXp() {
        var room = classrooms.create("Turma com aluno legado", code());
        var student = students.create(null, "Aluno Presencial", null);
        var enrolled = classrooms.enroll(room.id(), student.id());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), room.code(), email(), "Aluno Presencial"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("professor");
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ?",
                Integer.class, room.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select student_id from enrollments where id = ?",
                UUID.class, enrolled.enrollmentId())).isEqualTo(student.id());
    }

    @Test
    void disabledEnrollmentCannotBeReactivatedBySharedCode() {
        var room = classrooms.create("Turma com bloqueio", code());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        var linked = accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), room.code(), email(), "Aluno bloqueado");
        classrooms.setEnrollmentActive(room.id(), linked.studentId(), false);
        assertThatThrownBy(() -> accounts.joinClassroomByVerifiedOidc(
                identity.issuer(), identity.subject(), room.code(), null, "Aluno bloqueado"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacingClassCodeOrArchivingDisablesNewSelfEnrollments() {
        var room = classrooms.create("Turma rotativa", code());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        var updated = classrooms.update(room.id(), room.name(), code(), true);
        assertThat(updated.selfEnrollmentEnabled()).isFalse();
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        assertThat(classrooms.update(room.id(), room.name(), updated.code(), false)
                .selfEnrollmentEnabled()).isFalse();
    }

    @Test
    void twoConcurrentJoinsForSameIdentityDoNotCreateDuplicateEnrollment() throws Exception {
        var room = classrooms.create("Turma concorrente", code());
        classrooms.setSelfEnrollmentEnabled(room.id(), true);
        var identity = oidc(); accounts.provisionVerifiedIdentity(identity);
        String email = email();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> {
                gate.await(); return accounts.joinClassroomByVerifiedOidc(
                        identity.issuer(), identity.subject(), room.code(), email, "Maria");
            });
            var b = pool.submit(() -> {
                gate.await(); return accounts.joinClassroomByVerifiedOidc(
                        identity.issuer(), identity.subject(), room.code(), email, "Maria");
            });
            gate.countDown();
            assertThat(a.get(30, TimeUnit.SECONDS).enrollmentId())
                    .isEqualTo(b.get(30, TimeUnit.SECONDS).enrollmentId());
        }
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ?",
                Integer.class, room.id())).isEqualTo(1);
    }
}
