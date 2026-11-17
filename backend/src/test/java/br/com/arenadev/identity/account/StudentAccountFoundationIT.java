package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentAnonymizationService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StudentAccountFoundationIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, StudentAccountFoundationIT.class);
    }

    @Autowired private StudentAccountService accounts;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private StudentAnonymizationService anonymization;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;

    @Test
    void invitationApiIsDisabledByDefault() {
        assertThat(context.getBeansOfType(StudentAccountInvitationController.class)).isEmpty();
    }

    @Test
    void verifiedExternalIdentityDoesNotAutoLinkEmailOrDeviceClaim() {
        var s = scenario();
        String email = "student." + s.suffix + "@escola.org";
        students.updateEmail(s.studentId, email);
        var verified = new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.EMAIL_OTP, "urn:ramuvi:email", email);
        UUID accountId = accounts.provisionVerifiedIdentity(verified);
        assertThat(accounts.provisionVerifiedIdentity(verified)).isEqualTo(accountId);
        assertThat(jdbc.queryForObject(
                "select student_id from student_accounts where id = ?", UUID.class, accountId)).isNull();
        assertThatThrownBy(() -> new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC, " ", "sub")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashedInvitationCanOnlyBeClaimedOnceByOneVerifiedAccount() {
        var s = scenario();
        UUID account = verifiedAccount();
        var invite = accounts.issueEnrollmentInvite(s.enrollmentId);
        assertThat(invite.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(invite.expiresAt()).isAfter(Instant.now());
        assertThat(jdbc.queryForObject(
                "select octet_length(token_hash) from student_enrollment_invites where id = ?",
                Integer.class, invite.inviteId())).isEqualTo(32);
        assertThat(accounts.claimEnrollmentInvite(invite.token(), account)).isEqualTo(s.studentId);
        assertThat(jdbc.queryForObject(
                "select student_id from student_accounts where id = ?", UUID.class, account))
                .isEqualTo(s.studentId);
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(invite.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
        var conflicting = accounts.issueEnrollmentInvite(s.enrollmentId);
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(conflicting.token(), verifiedAccount()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vínculo");
    }

    @Test
    void issuingAnotherInvitationInvalidatesThePreviousUnclaimedToken() {
        var s = scenario();
        UUID account = verifiedAccount();
        var first = accounts.issueEnrollmentInvite(s.enrollmentId);
        var second = accounts.issueEnrollmentInvite(s.enrollmentId);
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(first.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.claimEnrollmentInvite(second.token(), account)).isEqualTo(s.studentId);
    }

    @Test
    void expiredRevokedAndInactiveInvitationsAreRejected() {
        var s = scenario();
        UUID account = verifiedAccount();
        var expired = accounts.issueEnrollmentInvite(s.enrollmentId);
        jdbc.update("update student_enrollment_invites set issued_at = now() - interval '8 days', expires_at = now() - interval '1 day' where id = ?",
                expired.inviteId());
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(expired.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
        var revoked = accounts.issueEnrollmentInvite(s.enrollmentId);
        accounts.revokeEnrollmentInvite(revoked.inviteId());
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(revoked.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
        var inactive = accounts.issueEnrollmentInvite(s.enrollmentId);
        classrooms.setEnrollmentActive(s.classroomId, s.studentId, false);
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(inactive.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.issueEnrollmentInvite(s.enrollmentId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneAccountCannotClaimTwoDifferentStudents() {
        var a = scenario();
        var b = scenario();
        UUID account = verifiedAccount();
        accounts.claimEnrollmentInvite(accounts.issueEnrollmentInvite(a.enrollmentId).token(), account);
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(
                accounts.issueEnrollmentInvite(b.enrollmentId).token(), account))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outro aluno");
    }

    @Test
    void anonymizationDisablesAccountAndRevokesIdentifiersAndInvitations() {
        var s = scenario();
        var verified = new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC, "https://id.example.test", UUID.randomUUID().toString());
        UUID account = accounts.provisionVerifiedIdentity(verified);
        accounts.claimEnrollmentInvite(accounts.issueEnrollmentInvite(s.enrollmentId).token(), account);
        var unused = accounts.issueEnrollmentInvite(s.enrollmentId);
        String name = jdbc.queryForObject("select name from students where id = ?",
                String.class, s.studentId);
        anonymization.anonymize(s.studentId, name);

        assertThat(jdbc.queryForObject(
                "select status from student_accounts where id = ?", String.class, account)).isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject(
                "select student_id from student_accounts where id = ?", UUID.class, account)).isNull();
        assertThat(jdbc.queryForObject("""
                select count(*) from student_account_identities
                 where account_id = ?
                """, Integer.class, account)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from student_enrollment_invites where enrollment_id = ?",
                Integer.class, s.enrollmentId)).isZero();
        // If the same person authenticates again, a fresh account is unlinked;
        // erasure never silently restores access to old classroom data.
        UUID replacement = accounts.provisionVerifiedIdentity(verified);
        assertThat(replacement).isNotEqualTo(account);
        assertThat(jdbc.queryForObject("select student_id from student_accounts where id = ?",
                UUID.class, replacement)).isNull();
        assertThatThrownBy(() -> accounts.claimEnrollmentInvite(unused.token(), account))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID verifiedAccount() {
        return accounts.provisionVerifiedIdentity(new StudentAccountService.VerifiedIdentity(
                StudentAccountService.Method.OIDC, "https://trusted.example.test",
                UUID.randomUUID().toString()));
    }

    private Scenario scenario() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var s = students.create("V12-" + suffix, "Aluno v12 " + suffix, null);
        var c = classrooms.create("Turma v12 " + suffix, "V12" + suffix);
        var e = classrooms.enroll(c.id(), s.id());
        return new Scenario(suffix, s.id(), c.id(), e.enrollmentId());
    }

    private record Scenario(String suffix, UUID studentId, UUID classroomId, UUID enrollmentId) { }
}
