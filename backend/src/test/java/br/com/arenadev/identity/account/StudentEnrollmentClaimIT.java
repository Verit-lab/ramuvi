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
class StudentEnrollmentClaimIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, StudentEnrollmentClaimIT.class);
    }

    @Autowired StudentAccountService accounts;
    @Autowired ClassroomService classrooms;
    @Autowired StudentService students;
    @Autowired JdbcTemplate jdbc;

    @Test
    void oneVerifiedPrincipalCanClaimAndDiscoverMultipleClassroomsOfSameStudent() {
        var first = scenario();
        var secondClassroom = classrooms.create("Outra turma " + first.suffix(), "C" + first.suffix());
        var second = classrooms.enroll(secondClassroom.id(), first.studentId());
        var identity = identity();
        UUID account = accounts.provisionVerifiedIdentity(identity);
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject())).isEmpty();

        String invitation = accounts.issueEnrollmentInvite(first.enrollmentId()).token();
        var claimed = accounts.claimEnrollmentInviteForVerifiedOidc(
                invitation, identity.issuer(), identity.subject());
        assertThat(claimed.studentId()).isEqualTo(first.studentId());
        assertThat(claimed.enrollmentId()).isEqualTo(first.enrollmentId());
        assertThat(claimed.classroomId()).isEqualTo(first.classroomId());
        assertThat(claimed.classroomName()).contains("Turma I2");
        assertThat(jdbc.queryForObject(
                "select student_id from student_accounts where id = ?", UUID.class, account))
                .isEqualTo(first.studentId());

        var memberships = accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject());
        assertThat(memberships).extracting(StudentAccountService.ActiveClassroom::classroomId)
                .containsExactlyInAnyOrder(first.classroomId(), secondClassroom.id());
        assertThat(memberships).extracting(StudentAccountService.ActiveClassroom::studentStudyAccessEnabled)
                .containsOnly(false);

        // Claiming the second enrollment is allowed, but not necessary for discovery.
        var additional = accounts.issueEnrollmentInvite(second.enrollmentId());
        assertThat(accounts.claimEnrollmentInviteForVerifiedOidc(
                additional.token(), identity.issuer(), identity.subject()).studentId())
                .isEqualTo(first.studentId());
        assertThatThrownBy(() -> accounts.claimEnrollmentInviteForVerifiedOidc(
                invitation, identity.issuer(), identity.subject()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classroomOrEnrollmentDeactivationImmediatelyHidesOnlyThatMembership() {
        var one = scenario();
        var extra = classrooms.create("Outra I2 " + one.suffix(), "E" + one.suffix());
        classrooms.enroll(extra.id(), one.studentId());
        var identity = identity();
        accounts.provisionVerifiedIdentity(identity);
        accounts.claimEnrollmentInviteForVerifiedOidc(
                accounts.issueEnrollmentInvite(one.enrollmentId()).token(),
                identity.issuer(), identity.subject());
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject()))
                .hasSize(2);
        classrooms.setEnrollmentActive(one.classroomId(), one.studentId(), false);
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject()))
                .extracting(StudentAccountService.ActiveClassroom::classroomId)
                .containsExactly(extra.id());
        classrooms.update(extra.id(), "Outra I2 " + one.suffix(), "E" + one.suffix(), false);
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject()))
                .isEmpty();
        classrooms.setEnrollmentActive(one.classroomId(), one.studentId(), true);
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identity.issuer(), identity.subject()))
                .extracting(StudentAccountService.ActiveClassroom::classroomId)
                .containsExactly(one.classroomId());
    }

    @Test
    void accountMismatchAndUnverifiedExternalIdentityDoNotConsumeInvites() {
        var first = scenario();
        var another = scenario();
        var identityA = identity();
        var identityB = identity();
        accounts.provisionVerifiedIdentity(identityA);
        accounts.provisionVerifiedIdentity(identityB);

        String token = accounts.issueEnrollmentInvite(first.enrollmentId()).token();
        assertThatThrownBy(() -> accounts.claimEnrollmentInviteForVerifiedOidc(
                token, "https://untrusted.example.test", identityA.subject()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.claimEnrollmentInviteForVerifiedOidc(
                token, identityA.issuer(), "attacker-subject"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        // No token consumed: the real verified account still succeeds.
        accounts.claimEnrollmentInviteForVerifiedOidc(token, identityA.issuer(), identityA.subject());

        String second = accounts.issueEnrollmentInvite(another.enrollmentId()).token();
        assertThatThrownBy(() -> accounts.claimEnrollmentInviteForVerifiedOidc(
                second, identityA.issuer(), identityA.subject()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outro aluno");
        assertThat(accounts.claimEnrollmentInviteForVerifiedOidc(
                second, identityB.issuer(), identityB.subject()).studentId())
                .isEqualTo(another.studentId());
        assertThat(accounts.listActiveClassroomsForVerifiedOidc(identityA.issuer(), identityA.subject()))
                .extracting(StudentAccountService.ActiveClassroom::classroomId)
                .doesNotContain(another.classroomId());
    }

    @Test
    void revokedIdentityCanNeitherClaimNorListClassroomsEvenWithAnOldBrowserSession() {
        var student = scenario();
        var verified = identity();
        UUID account = accounts.provisionVerifiedIdentity(verified);
        var token = accounts.issueEnrollmentInvite(student.enrollmentId());
        jdbc.update("update student_account_identities set revoked_at=now() where account_id=?",
                account);
        assertThatThrownBy(() -> accounts.claimEnrollmentInviteForVerifiedOidc(
                token.token(), verified.issuer(), verified.subject()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.listActiveClassroomsForVerifiedOidc(
                verified.issuer(), verified.subject()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void simultaneousClaimsUseRowLocksAndAllowExactlyOneWinner() throws Exception {
        var student = scenario();
        var verified = identity();
        accounts.provisionVerifiedIdentity(verified);
        String token = accounts.issueEnrollmentInvite(student.enrollmentId()).token();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> claim = () -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                try {
                    accounts.claimEnrollmentInviteForVerifiedOidc(
                            token, verified.issuer(), verified.subject());
                    return true;
                } catch (IllegalArgumentException conflict) {
                    return false;
                }
            };
            var first = workers.submit(claim);
            var second = workers.submit(claim);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat((first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from student_enrollment_invites
                 where enrollment_id = ? and claimed_at is not null
                """, Integer.class, student.enrollmentId())).isEqualTo(1);
    }

    private Scenario scenario() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var student = students.create("I2-" + suffix, "Aluno I2 " + suffix, null);
        var classroom = classrooms.create("Turma I2 " + suffix, "I" + suffix);
        var enrollment = classrooms.enroll(classroom.id(), student.id());
        return new Scenario(suffix, student.id(), classroom.id(), enrollment.enrollmentId());
    }

    private StudentAccountService.VerifiedIdentity identity() {
        return new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC, "https://accounts.google.com",
                UUID.randomUUID().toString());
    }

    private record Scenario(String suffix, UUID studentId, UUID classroomId, UUID enrollmentId) { }
}
