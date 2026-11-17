package br.com.arenadev.identity.account;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent identity boundary. Provisioning and claiming are intentionally not HTTP routes.
 * Callers must derive verified principals from a trusted OIDC or email-OTP adapter.
 * A device claim, manually typed email or name is NEVER a verified principal.
 */
@Service
public class StudentAccountService {
    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;

    public StudentAccountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum Method { OIDC, EMAIL_OTP }

    public record VerifiedIdentity(Method method, String issuer, String subject) {
        public VerifiedIdentity {
            Objects.requireNonNull(method, "method");
            if (invalidKey(issuer) || invalidKey(subject)) {
                throw new IllegalArgumentException("Identificador externo inválido.");
            }
        }
        private static boolean invalidKey(String s) {
            return s == null || s.isBlank() || s.length() > 254 || !s.equals(s.trim());
        }
    }

    /** Internal trusted-adapter operation; NOT a browser-input authentication method. */
    @Transactional
    public UUID provisionVerifiedIdentity(VerifiedIdentity verified) {
        Objects.requireNonNull(verified, "verified");
        var rows = jdbc.query("""
                select a.id, a.status, i.method, i.revoked_at
                  from student_account_identities i
                  join student_accounts a on a.id = i.account_id
                 where i.issuer = ? and i.subject = ?
                """, (rs, row) -> new IdentityState(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getString("method"), rs.getTimestamp("revoked_at") != null), verified.issuer(), verified.subject());
        if (!rows.isEmpty()) {
            var state = rows.getFirst();
            if (!"ACTIVE".equals(state.status()) || state.revoked()
                    || !verified.method().name().equals(state.method())) {
                throw new IllegalArgumentException("Identidade revogada ou desativada.");
            }
            return state.accountId();
        }
        UUID accountId = UUID.randomUUID();
        jdbc.update("insert into student_accounts (id, status) values (?, 'ACTIVE')", accountId);
        jdbc.update("""
                insert into student_account_identities
                   (id, account_id, method, issuer, subject, verified_at)
                values (?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), accountId, verified.method().name(),
                verified.issuer(), verified.subject());
        // UNIQUE(issuer,subject) fails closed for concurrent first authentication.
        return accountId;
    }

    /**
     * Re-check current DB state for EVERY authenticated account request.
     * Revocation and anonymization invalidate old sessions without changing
     * the teacher login or the legacy code-based participation flow.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AccountView> findActiveVerifiedOidcAccount(String issuer, String subject) {
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            return java.util.Optional.empty();
        }
        return jdbc.query("""
                select a.id, a.student_id
                  from student_accounts a
                  join student_account_identities i on i.account_id = a.id
                  left join students s on s.id = a.student_id
                 where i.issuer = ? and i.subject = ? and i.method = 'OIDC'
                   and i.revoked_at is null and a.status = 'ACTIVE'
                   and (a.student_id is null
                        or (s.active and s.anonymized_at is null))
                """, (rs, row) -> new AccountView(
                rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class)),
                issuer, subject).stream().findFirst();
    }

    public record AccountView(UUID accountId, UUID studentId) { }

    /**
     * I2: the account must come from the cryptographically verified OIDC principal
     * and be checked within the SAME transaction as the invitation consumption.
     * A shared row lock prevents a concurrent identity revocation from racing the claim.
     * Never call this with issuer/subject supplied directly in request JSON.
     */
    @Transactional
    public ClaimedEnrollment claimEnrollmentInviteForVerifiedOidc(
            String token, String issuer, String subject
    ) {
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Identidade inválida.");
        }
        var accounts = jdbc.query("""
                select a.id
                  from student_account_identities i
                  join student_accounts a on a.id = i.account_id
                  left join students s on s.id = a.student_id
                 where i.issuer = ? and i.subject = ? and i.method = 'OIDC'
                   and i.revoked_at is null and a.status = 'ACTIVE'
                   and (a.student_id is null or (s.active and s.anonymized_at is null))
                 for share of i
                """, (rs, row) -> rs.getObject("id", UUID.class), issuer, subject);
        if (accounts.size() != 1) {
            throw new org.springframework.security.access.AccessDeniedException("Conta indisponível.");
        }
        UUID accountId = accounts.getFirst();
        UUID studentId = claimEnrollmentInvite(token, accountId);
        // Reads only the just-claimed row, never a list of other students' enrollments.
        var claims = jdbc.query("""
                select e.id as enrollment_id, c.id as classroom_id, c.name as classroom_name
                  from student_enrollment_invites i
                  join enrollments e on e.id = i.enrollment_id
                  join classrooms c on c.id = e.classroom_id
                 where i.token_hash = ? and i.claimed_by_account_id = ?
                   and e.student_id = ? and i.claimed_at is not null
                """, (rs, row) -> new ClaimedEnrollment(
                studentId,
                rs.getObject("enrollment_id", UUID.class),
                rs.getObject("classroom_id", UUID.class),
                rs.getString("classroom_name")), digest(token), accountId, studentId);
        if (claims.size() != 1) throw invalidInvite();
        return claims.getFirst();
    }

    /**
     * Active memberships only, scoped to the exact current OIDC identity.
     * This endpoint is a discovery view, NOT a replacement for the existing
     * X-Study-Token access-control checks in /api/learn/**.
     */
    @Transactional(readOnly = true)
    public java.util.List<ActiveClassroom> listActiveClassroomsForVerifiedOidc(
            String issuer, String subject
    ) {
        var current = findActiveVerifiedOidcAccount(issuer, subject)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Conta indisponível."));
        if (current.studentId() == null) return java.util.List.of();
        return jdbc.query("""
                select e.id as enrollment_id, c.id as classroom_id, c.name as classroom_name,
                       c.student_study_access_enabled
                  from enrollments e
                  join classrooms c on c.id = e.classroom_id
                  join students s on s.id = e.student_id
                  join student_accounts a on a.student_id = s.id
                  join student_account_identities i on i.account_id = a.id
                 where a.id = ? and a.status = 'ACTIVE'
                   and i.issuer = ? and i.subject = ? and i.method = 'OIDC'
                   and i.revoked_at is null and e.active and c.active and s.active
                   and s.anonymized_at is null
                 order by lower(c.name), e.id
                """, (rs, row) -> new ActiveClassroom(
                rs.getObject("enrollment_id", UUID.class),
                rs.getObject("classroom_id", UUID.class),
                rs.getString("classroom_name"),
                rs.getBoolean("student_study_access_enabled")),
                current.accountId(), issuer, subject);
    }

    public record ClaimedEnrollment(UUID studentId, UUID enrollmentId, UUID classroomId,
                                    String classroomName) { }
    public record ActiveClassroom(UUID enrollmentId, UUID classroomId, String classroomName,
                                  boolean studentStudyAccessEnabled) { }

    /** Teacher-only invitation issuance. Token bytes are never stored, only their SHA-256. */
    @Transactional
    public IssuedInvite issueEnrollmentInvite(UUID enrollmentId) {
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Integer valid = jdbc.queryForObject("""
                select count(*) from enrollments e
                  join students s on s.id = e.student_id
                  join classrooms c on c.id = e.classroom_id
                 where e.id = ? and e.active and s.active
                   and s.anonymized_at is null and c.active
                """, Integer.class, enrollmentId);
        if (valid == null || valid != 1) throw invalidInvite();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        // Rotating a teacher-issued invitation invalidates older, unclaimed tokens.
        jdbc.update("""
                update student_enrollment_invites set revoked_at = now()
                 where enrollment_id = ? and claimed_at is null and revoked_at is null
                """, enrollmentId);
        UUID inviteId = UUID.randomUUID();
        Instant expiration = Instant.now().plus(INVITE_TTL);
        jdbc.update("""
                insert into student_enrollment_invites
                    (id, enrollment_id, token_hash, expires_at)
                values (?, ?, ?, ?)
                """, inviteId, enrollmentId, digest(token), Timestamp.from(expiration));
        return new IssuedInvite(inviteId, enrollmentId, token, expiration);
    }

    /**
     * Internal operation. A future authenticated controller MUST get accountId
     * from the server's verified SecurityContext, never from the request body.
     * Invitation ownership is enforced by unique(student_id) and row locking.
     */
    @Transactional
    public UUID claimEnrollmentInvite(String token, UUID authenticatedAccountId) {
        Objects.requireNonNull(authenticatedAccountId, "authenticatedAccountId");
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
        var invites = jdbc.query("""
                select i.id, e.student_id, i.expires_at, i.claimed_at, i.revoked_at,
                       e.active as enrollment_active, s.active as student_active,
                       s.anonymized_at, c.active as classroom_active
                  from student_enrollment_invites i
                  join enrollments e on e.id = i.enrollment_id
                  join students s on s.id = e.student_id
                  join classrooms c on c.id = e.classroom_id
                 where i.token_hash = ? for update of i
                """, ps -> ps.setBytes(1, digest(token)), (rs, row) -> new InviteState(
                rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("claimed_at") != null, rs.getTimestamp("revoked_at") != null,
                rs.getBoolean("enrollment_active"), rs.getBoolean("student_active"),
                rs.getTimestamp("anonymized_at") != null, rs.getBoolean("classroom_active")));
        if (invites.size() != 1) throw invalidInvite();
        InviteState invite = invites.getFirst();
        if (!invite.expiresAt().isAfter(Instant.now()) || invite.claimed() || invite.revoked()
                || !invite.enrollmentActive() || !invite.studentActive()
                || invite.anonymized() || !invite.classroomActive()) throw invalidInvite();

        var accounts = jdbc.query("""
                select student_id, status from student_accounts
                 where id = ? for update
                """, (rs, row) -> new AccountState(
                rs.getObject("student_id", UUID.class), rs.getString("status")),
                authenticatedAccountId);
        if (accounts.size() != 1 || !"ACTIVE".equals(accounts.getFirst().status())) {
            throw invalidInvite();
        }
        Integer activeIdentityCount = jdbc.queryForObject("""
                select count(*) from student_account_identities
                 where account_id = ? and revoked_at is null
                """, Integer.class, authenticatedAccountId);
        if (activeIdentityCount == null || activeIdentityCount == 0) throw invalidInvite();

        UUID existingStudent = accounts.getFirst().studentId();
        if (existingStudent != null && !existingStudent.equals(invite.studentId())) {
            throw new IllegalArgumentException("Conta vinculada a outro aluno.");
        }
        if (existingStudent == null) {
            try {
                int updated = jdbc.update("""
                        update student_accounts set student_id = ?
                         where id = ? and student_id is null and status = 'ACTIVE'
                        """, invite.studentId(), authenticatedAccountId);
                if (updated != 1) throw invalidInvite();
            } catch (DataIntegrityViolationException conflict) {
                throw new IllegalArgumentException("Vínculo de aluno já existente.", conflict);
            }
        }
        int claimed = jdbc.update("""
                update student_enrollment_invites
                   set claimed_at = now(), claimed_by_account_id = ?
                 where id = ? and claimed_at is null and revoked_at is null
                """, authenticatedAccountId, invite.inviteId());
        if (claimed != 1) throw invalidInvite();
        return invite.studentId();
    }

    @Transactional
    public void revokeEnrollmentInvite(UUID inviteId) {
        int revoked = jdbc.update("""
                update student_enrollment_invites set revoked_at = now()
                 where id = ? and claimed_at is null and revoked_at is null
                """, inviteId);
        if (revoked != 1) throw invalidInvite();
    }

    private static IllegalArgumentException invalidInvite() {
        return new IllegalArgumentException("Convite inválido ou indisponível.");
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    public record IssuedInvite(UUID inviteId, UUID enrollmentId, String token, Instant expiresAt) { }
    private record IdentityState(UUID accountId, String status, String method, boolean revoked) { }
    private record AccountState(UUID studentId, String status) { }
    private record InviteState(UUID inviteId, UUID studentId, Instant expiresAt,
                               boolean claimed, boolean revoked, boolean enrollmentActive,
                               boolean studentActive, boolean anonymized, boolean classroomActive) { }
}
