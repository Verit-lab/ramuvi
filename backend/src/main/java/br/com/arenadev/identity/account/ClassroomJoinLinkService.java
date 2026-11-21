package br.com.arenadev.identity.account;

import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ADR-0074: dedicated classroom join link + verified OIDC identity.
 *
 * <ul>
 *   <li>The link token is 160 random bits, shown once to the teacher, stored only as SHA-256,
 *       with expiry, revocation/rotation and optional teacher approval. {@code classrooms.code}
 *       is never accepted as join proof.</li>
 *   <li>Issuer/subject come only from the Spring OIDC principal. An e-mail is only passed in when
 *       the controller saw {@code email_verified=true} from the Google issuer.</li>
 *   <li>Linking an existing student by e-mail requires the explicit
 *       {@code auto-email-link-enabled} flag, an EXACT match on a teacher-entered address
 *       (unique index), an active and unowned record, and an ACTIVE enrollment in the very
 *       classroom of the presented link. Everything else becomes a teacher review request or a
 *       refusal; names are only a collision signal, never proof.</li>
 * </ul>
 */
@Service
public class ClassroomJoinLinkService {
    static final int TOKEN_BYTES = 20; // 160 bits
    static final int MIN_VALID_DAYS = 1;
    static final int MAX_VALID_DAYS = 30;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final String TOKEN_PATTERN = "[A-Z2-7]{32}";

    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final boolean autoEmailLinkEnabled;

    public ClassroomJoinLinkService(
            JdbcTemplate jdbc,
            @Value("${app.student-identity.auto-email-link-enabled:false}") boolean autoEmailLinkEnabled
    ) {
        this.jdbc = jdbc;
        this.autoEmailLinkEnabled = autoEmailLinkEnabled;
    }

    // ------------------------------------------------------------------ teacher

    /** Rotates: any open link of the classroom is revoked before the new one is created. */
    @Transactional
    public IssuedLink issue(UUID classroomId, int validDays, boolean requiresApproval) {
        if (validDays < MIN_VALID_DAYS || validDays > MAX_VALID_DAYS) {
            throw new IllegalArgumentException("A validade do link deve ficar entre 1 e 30 dias.");
        }
        var classroom = lockClassroom(classroomId);
        if (!classroom.active()) {
            throw new IllegalArgumentException("Reative a turma antes de gerar um link de ingresso.");
        }
        jdbc.update("""
                update classroom_join_links set revoked_at = now()
                 where classroom_id = ? and revoked_at is null
                """, classroomId);
        byte[] secret = new byte[TOKEN_BYTES];
        random.nextBytes(secret);
        String token = base32(secret);
        UUID linkId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(validDays));
        jdbc.update("""
                insert into classroom_join_links
                    (id, classroom_id, token_hash, created_at, expires_at, requires_approval)
                values (?, ?, ?, ?, ?, ?)
                """, linkId, classroomId, digest(token), Timestamp.from(now), Timestamp.from(expiresAt),
                requiresApproval);
        return new IssuedLink(linkId, token, expiresAt, requiresApproval);
    }

    /** Never returns the token (only its hash exists). */
    @Transactional(readOnly = true)
    public LinkStatus status(UUID classroomId) {
        lockFreeClassroom(classroomId);
        int pending = count("""
                select count(*) from classroom_join_requests
                 where classroom_id = ? and status = 'PENDING'
                """, classroomId);
        var open = jdbc.query("""
                select id, created_at, expires_at, requires_approval, accepted_count, last_accepted_at
                  from classroom_join_links
                 where classroom_id = ? and revoked_at is null
                """, (rs, row) -> new LinkStatus(true,
                rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                !rs.getTimestamp("expires_at").toInstant().isAfter(Instant.now()),
                rs.getBoolean("requires_approval"),
                rs.getInt("accepted_count"),
                rs.getTimestamp("last_accepted_at") == null ? null
                        : rs.getTimestamp("last_accepted_at").toInstant(),
                pending), classroomId);
        return open.isEmpty()
                ? new LinkStatus(false, null, null, null, false, false, 0, null, pending)
                : open.getFirst();
    }

    @Transactional
    public LinkStatus setRequiresApproval(UUID classroomId, boolean requiresApproval) {
        lockClassroom(classroomId);
        int updated = jdbc.update("""
                update classroom_join_links set requires_approval = ?
                 where classroom_id = ? and revoked_at is null
                """, requiresApproval, classroomId);
        if (updated != 1) throw new IllegalArgumentException("Não há link de ingresso ativo nesta turma.");
        return status(classroomId);
    }

    /** Idempotent. Already-created enrollments and pending requests are preserved. */
    @Transactional
    public void revoke(UUID classroomId) {
        lockClassroom(classroomId);
        jdbc.update("""
                update classroom_join_links set revoked_at = now()
                 where classroom_id = ? and revoked_at is null
                """, classroomId);
    }

    @Transactional(readOnly = true)
    public ReviewQueue reviewQueue(UUID classroomId) {
        lockFreeClassroom(classroomId);
        var requests = jdbc.query("""
                select r.id, r.reason, r.display_name, r.verified_email, r.created_at,
                       a.student_id as linked_student_id
                  from classroom_join_requests r
                  join student_accounts a on a.id = r.account_id
                 where r.classroom_id = ? and r.status = 'PENDING'
                 order by r.created_at, r.id
                """, (rs, row) -> new PendingRequest(
                rs.getObject("id", UUID.class), RequestReason.valueOf(rs.getString("reason")),
                rs.getString("display_name"), rs.getString("verified_email"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("linked_student_id", UUID.class) != null,
                emailMatchCandidate(rs.getString("verified_email"))), classroomId);
        // Teacher-only: roster entries that can still receive an account.
        var candidates = jdbc.query("""
                select s.id, s.name, s.registration, e.preferred_name, s.email is not null as has_email
                  from enrollments e
                  join students s on s.id = e.student_id
                 where e.classroom_id = ? and e.active and s.active and s.anonymized_at is null
                   and not exists (select 1 from student_accounts a where a.student_id = s.id)
                 order by lower(s.name), s.id
                """, (rs, row) -> new LinkCandidate(
                rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("registration"),
                rs.getString("preferred_name"), rs.getBoolean("has_email")), classroomId);
        return new ReviewQueue(requests, candidates);
    }

    private UUID emailMatchCandidate(String email) {
        if (email == null) return null;
        return jdbc.query("""
                select s.id from students s
                 where lower(s.email) = ? and s.active and s.anonymized_at is null
                   and not exists (select 1 from student_accounts a where a.student_id = s.id)
                """, (rs, row) -> rs.getObject("id", UUID.class), email).stream().findFirst().orElse(null);
    }

    /**
     * Teacher decision. {@code LINK_EXISTING} needs a student chosen by the teacher; the
     * student must be active, unowned and must not have a deactivated enrollment here.
     */
    @Transactional
    public Decision approve(UUID classroomId, UUID requestId, ApprovalMode mode, UUID studentId) {
        Objects.requireNonNull(mode, "mode");
        var classroom = lockClassroom(classroomId);
        if (!classroom.active()) throw new IllegalArgumentException("A turma está arquivada.");
        var request = lockPendingRequest(classroomId, requestId);
        var account = jdbc.query("""
                select a.id, a.student_id from student_accounts a
                 where a.id = ? and a.status = 'ACTIVE'
                   and exists (select 1 from student_account_identities i
                                where i.account_id = a.id and i.revoked_at is null)
                 for update of a
                """, (rs, row) -> new AccountRow(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class)), request.accountId());
        if (account.size() != 1) {
            throw new IllegalArgumentException("A conta do aluno não está mais ativa. Recuse o pedido.");
        }
        UUID resolvedStudent = account.getFirst().studentId();
        if (resolvedStudent == null) {
            if (mode == ApprovalMode.LINK_EXISTING) {
                if (studentId == null) throw new IllegalArgumentException("Escolha o aluno a vincular.");
                lockLinkableStudent(studentId);
                resolvedStudent = studentId;
            } else {
                resolvedStudent = createStudent(request.displayName(), request.verifiedEmail());
            }
            linkAccount(account.getFirst().accountId(), resolvedStudent);
        } else if (mode == ApprovalMode.LINK_EXISTING && studentId != null
                && !studentId.equals(resolvedStudent)) {
            throw new IllegalArgumentException("Esta conta já pertence a outro aluno; aprove como ingresso.");
        }
        UUID enrollmentId = ensureActiveEnrollment(classroomId, resolvedStudent);
        jdbc.update("""
                update classroom_join_requests
                   set status = 'APPROVED', decided_at = now(), resolved_student_id = ?,
                       verified_email = null
                 where id = ? and status = 'PENDING'
                """, resolvedStudent, requestId);
        return new Decision(requestId, RequestStatus.APPROVED, resolvedStudent, enrollmentId);
    }

    @Transactional
    public Decision reject(UUID classroomId, UUID requestId) {
        lockClassroom(classroomId);
        lockPendingRequest(classroomId, requestId);
        jdbc.update("""
                update classroom_join_requests
                   set status = 'REJECTED', decided_at = now(), verified_email = null
                 where id = ? and status = 'PENDING'
                """, requestId);
        return new Decision(requestId, RequestStatus.REJECTED, null, null);
    }

    // ------------------------------------------------------------------ student

    /** Read-only: lets the student confirm WHICH classroom before anything changes. */
    @Transactional(readOnly = true)
    public LinkPreview preview(String issuer, String subject, String token) {
        var account = activeAccount(issuer, subject);
        var link = findUsableLink(token, false);
        boolean member = account.studentId() != null && count("""
                select count(*) from enrollments
                 where classroom_id = ? and student_id = ? and active
                """, link.classroomId(), account.studentId()) == 1;
        boolean pending = count("""
                select count(*) from classroom_join_requests
                 where classroom_id = ? and account_id = ? and status = 'PENDING'
                """, link.classroomId(), account.accountId()) == 1;
        return new LinkPreview(link.classroomName(), link.expiresAt(), link.requiresApproval(),
                member, pending);
    }

    @Transactional
    public JoinOutcome accept(String issuer, String subject, String token,
                              String googleVerifiedEmail, String displayName) {
        var account = lockAccount(issuer, subject);
        var link = findUsableLink(token, true); // row lock serializes accepts per classroom link
        String email = normalizeEmail(googleVerifiedEmail);
        String name = normalizeDisplayName(displayName, email);

        if (account.studentId() != null) {
            var enrollment = enrollment(link.classroomId(), account.studentId());
            if (enrollment.isPresent()) {
                if (!enrollment.get().active()) throw deactivatedEnrollment();
                return outcome(JoinStatus.ALREADY_MEMBER, link, null);
            }
            if (link.requiresApproval()) {
                return pending(account, link, RequestReason.APPROVAL_REQUIRED, name, email);
            }
            ensureActiveEnrollment(link.classroomId(), account.studentId());
            markAccepted(link);
            return outcome(JoinStatus.JOINED, link, null);
        }

        if (email != null) {
            var matches = jdbc.query("""
                    select s.id, s.email_source, s.active, s.anonymized_at is not null as anonymized,
                           exists (select 1 from student_accounts a where a.student_id = s.id) as owned
                      from students s
                     where lower(s.email) = ?
                     for update of s
                    """, (rs, row) -> new EmailMatch(rs.getObject("id", UUID.class),
                    rs.getString("email_source"), rs.getBoolean("active"),
                    rs.getBoolean("anonymized"), rs.getBoolean("owned")), email);
            if (matches.size() > 1) {
                // Unique index makes this unreachable; fail closed if it ever is not.
                return pending(account, link, RequestReason.EMAIL_REVIEW, name, email);
            }
            if (matches.size() == 1) {
                EmailMatch match = matches.getFirst();
                if (match.owned()) {
                    throw new IllegalStateException(
                            "Este e-mail já está vinculado a outra conta da Ramuvi. Peça ajuda ao professor.");
                }
                if (!match.active() || match.anonymized()) {
                    throw new IllegalStateException(
                            "Seu cadastro está desativado. Fale com o professor antes de entrar.");
                }
                var enrollment = enrollment(link.classroomId(), match.studentId());
                if (enrollment.isPresent() && !enrollment.get().active()) throw deactivatedEnrollment();
                boolean provenInThisClassroom = enrollment.isPresent()
                        && "TEACHER".equals(match.emailSource());
                if (autoEmailLinkEnabled && provenInThisClassroom) {
                    linkAccount(account.accountId(), match.studentId());
                    markAccepted(link);
                    return outcome(JoinStatus.LINKED_EXISTING_STUDENT, link, null);
                }
                // Flag OFF, student-typed e-mail or record from another classroom:
                // the teacher decides; never create a duplicate for a known address.
                return pending(account, link, RequestReason.EMAIL_REVIEW, name, email);
            }
        }

        if (hasPossibleDuplicate(link.classroomId(), name)) {
            return pending(account, link, RequestReason.POSSIBLE_DUPLICATE, name, email);
        }
        if (link.requiresApproval()) {
            return pending(account, link, RequestReason.APPROVAL_REQUIRED, name, email);
        }
        UUID studentId = createStudent(name, email);
        linkAccount(account.accountId(), studentId);
        ensureActiveEnrollment(link.classroomId(), studentId);
        markAccepted(link);
        return outcome(JoinStatus.JOINED_NEW_STUDENT, link, null);
    }

    @Transactional(readOnly = true)
    public List<OwnRequest> ownRequests(String issuer, String subject) {
        var account = activeAccount(issuer, subject);
        return jdbc.query("""
                select r.id, r.status, r.created_at, r.decided_at, c.name as classroom_name
                  from classroom_join_requests r
                  join classrooms c on c.id = r.classroom_id
                 where r.account_id = ?
                 order by r.created_at desc, r.id
                 limit 10
                """, (rs, row) -> new OwnRequest(rs.getObject("id", UUID.class),
                RequestStatus.valueOf(rs.getString("status")), rs.getString("classroom_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant()),
                account.accountId());
    }

    // ------------------------------------------------------------------ internals

    /**
     * A new account is NOT created as a new student when this classroom still has roster
     * entries without an account that are either name-only (no e-mail to match) or carry the
     * same normalized name. The teacher reconciles them instead of splitting XP (F-08).
     */
    private boolean hasPossibleDuplicate(UUID classroomId, String displayName) {
        String wanted = normalizeName(displayName);
        var roster = jdbc.query("""
                select s.name, s.nickname, e.preferred_name, s.email
                  from enrollments e
                  join students s on s.id = e.student_id
                 where e.classroom_id = ? and e.active and s.active and s.anonymized_at is null
                   and not exists (select 1 from student_accounts a where a.student_id = s.id)
                """, (rs, row) -> new RosterEntry(rs.getString("name"), rs.getString("nickname"),
                rs.getString("preferred_name"), rs.getString("email")), classroomId);
        for (RosterEntry entry : roster) {
            if (entry.email() == null) return true;
            if (!wanted.isEmpty() && (wanted.equals(normalizeName(entry.name()))
                    || wanted.equals(normalizeName(entry.nickname()))
                    || wanted.equals(normalizeName(entry.preferredName())))) {
                return true;
            }
        }
        return false;
    }

    private JoinOutcome pending(AccountRow account, UsableLink link, RequestReason reason,
                                String name, String email) {
        var existing = jdbc.query("""
                select id from classroom_join_requests
                 where classroom_id = ? and account_id = ? and status = 'PENDING'
                """, (rs, row) -> rs.getObject("id", UUID.class), link.classroomId(), account.accountId());
        if (!existing.isEmpty()) return outcome(JoinStatus.PENDING_APPROVAL, link, existing.getFirst());
        int rejected = count("""
                select count(*) from classroom_join_requests
                 where link_id = ? and account_id = ? and status = 'REJECTED'
                """, link.linkId(), account.accountId());
        if (rejected > 0) {
            throw new IllegalStateException(
                    "O professor recusou seu pedido para este link. Fale com ele ou peça um novo link.");
        }
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
                insert into classroom_join_requests
                    (id, classroom_id, account_id, link_id, reason, display_name, verified_email)
                values (?, ?, ?, ?, ?, ?, ?)
                """, requestId, link.classroomId(), account.accountId(), link.linkId(), reason.name(),
                name, email);
        return outcome(JoinStatus.PENDING_APPROVAL, link, requestId);
    }

    private JoinOutcome outcome(JoinStatus status, UsableLink link, UUID requestId) {
        return new JoinOutcome(status, link.classroomId(), link.classroomName(),
                link.studyAccessEnabled(), requestId);
    }

    private void markAccepted(UsableLink link) {
        jdbc.update("""
                update classroom_join_links
                   set accepted_count = accepted_count + 1, last_accepted_at = now()
                 where id = ?
                """, link.linkId());
    }

    private UsableLink findUsableLink(String token, boolean lock) {
        String normalized = normalizeToken(token);
        if (normalized == null) throw invalidLink();
        var links = jdbc.query("""
                select l.id, l.classroom_id, c.name, l.expires_at, l.requires_approval,
                       c.student_study_access_enabled
                  from classroom_join_links l
                  join classrooms c on c.id = l.classroom_id
                 where l.token_hash = ? and l.revoked_at is null and l.expires_at > now()
                   and c.active
                """ + (lock ? " for update of l" : ""),
                ps -> ps.setBytes(1, digest(normalized)),
                (rs, row) -> new UsableLink(rs.getObject("id", UUID.class),
                        rs.getObject("classroom_id", UUID.class), rs.getString("name"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getBoolean("requires_approval"),
                        rs.getBoolean("student_study_access_enabled")));
        if (links.size() != 1) throw invalidLink();
        return links.getFirst();
    }

    private Optional<EnrollmentRow> enrollment(UUID classroomId, UUID studentId) {
        return jdbc.query("""
                select id, active from enrollments
                 where classroom_id = ? and student_id = ?
                 for update
                """, (rs, row) -> new EnrollmentRow(rs.getObject("id", UUID.class), rs.getBoolean("active")),
                classroomId, studentId).stream().findFirst();
    }

    /** Never reactivates an enrollment a teacher deactivated. */
    private UUID ensureActiveEnrollment(UUID classroomId, UUID studentId) {
        var existing = enrollment(classroomId, studentId);
        if (existing.isPresent()) {
            if (!existing.get().active()) throw deactivatedEnrollment();
            return existing.get().id();
        }
        UUID enrollmentId = UUID.randomUUID();
        jdbc.update("insert into enrollments (id, classroom_id, student_id) values (?, ?, ?)",
                enrollmentId, classroomId, studentId);
        return enrollmentId;
    }

    private void lockLinkableStudent(UUID studentId) {
        var rows = jdbc.query("""
                select s.id from students s
                 where s.id = ? and s.active and s.anonymized_at is null
                 for update of s
                """, (rs, row) -> rs.getObject("id", UUID.class), studentId);
        if (rows.size() != 1) throw new IllegalArgumentException("Aluno indisponível para vínculo.");
        if (count("select count(*) from student_accounts where student_id = ?", studentId) != 0) {
            throw new IllegalArgumentException("Este aluno já está vinculado a outra conta.");
        }
    }

    private void linkAccount(UUID accountId, UUID studentId) {
        int updated = jdbc.update("""
                update student_accounts set student_id = ?
                 where id = ? and student_id is null and status = 'ACTIVE'
                """, studentId, accountId);
        // unique(student_accounts.student_id) rejects a second owner at commit time too.
        if (updated != 1) throw new IllegalStateException("A conta mudou durante o ingresso. Tente novamente.");
    }

    private UUID createStudent(String name, String email) {
        UUID studentId = UUID.randomUUID();
        String storedEmail = email != null && count(
                "select count(*) from students where lower(email) = ?", email) == 0 ? email : null;
        jdbc.update("""
                insert into students (id, name, email, email_source, name_pending)
                values (?, ?, ?, ?, ?)
                """, studentId, name, storedEmail, storedEmail == null ? null : "STUDENT",
                "Aluno".equals(name));
        return studentId;
    }

    private PendingRow lockPendingRequest(UUID classroomId, UUID requestId) {
        var rows = jdbc.query("""
                select id, account_id, display_name, verified_email from classroom_join_requests
                 where id = ? and classroom_id = ? and status = 'PENDING'
                 for update
                """, (rs, row) -> new PendingRow(rs.getObject("id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getString("display_name"),
                rs.getString("verified_email")), requestId, classroomId);
        if (rows.size() != 1) throw new IllegalArgumentException("Pedido inexistente ou já decidido.");
        return rows.getFirst();
    }

    private ClassroomRow lockClassroom(UUID classroomId) {
        var rows = jdbc.query("select id, active from classrooms where id = ? for no key update",
                (rs, row) -> new ClassroomRow(rs.getObject("id", UUID.class), rs.getBoolean("active")),
                classroomId);
        if (rows.size() != 1) throw new ResourceNotFoundException("Turma não encontrada.");
        return rows.getFirst();
    }

    private void lockFreeClassroom(UUID classroomId) {
        if (count("select count(*) from classrooms where id = ?", classroomId) != 1) {
            throw new ResourceNotFoundException("Turma não encontrada.");
        }
    }

    private AccountRow activeAccount(String issuer, String subject) {
        requireIdentity(issuer, subject);
        var rows = jdbc.query("""
                select a.id, a.student_id
                  from student_account_identities i
                  join student_accounts a on a.id = i.account_id
                  left join students s on s.id = a.student_id
                 where i.issuer = ? and i.subject = ? and i.method = 'OIDC'
                   and i.revoked_at is null and a.status = 'ACTIVE'
                   and (a.student_id is null or (s.active and s.anonymized_at is null))
                """, (rs, row) -> new AccountRow(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class)), issuer, subject);
        if (rows.size() != 1) throw new AccessDeniedException("Conta indisponível.");
        return rows.getFirst();
    }

    private AccountRow lockAccount(String issuer, String subject) {
        requireIdentity(issuer, subject);
        var rows = jdbc.query("""
                select a.id, a.student_id
                  from student_account_identities i
                  join student_accounts a on a.id = i.account_id
                 where i.issuer = ? and i.subject = ? and i.method = 'OIDC'
                   and i.revoked_at is null and a.status = 'ACTIVE'
                 for update of a
                """, (rs, row) -> new AccountRow(rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class)), issuer, subject);
        if (rows.size() != 1) throw new AccessDeniedException("Conta indisponível.");
        var account = rows.getFirst();
        if (account.studentId() != null && count("""
                select count(*) from students where id = ? and active and anonymized_at is null
                """, account.studentId()) != 1) {
            throw new AccessDeniedException("Conta indisponível.");
        }
        return account;
    }

    private static void requireIdentity(String issuer, String subject) {
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            throw new AccessDeniedException("Identidade inválida.");
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** Accepts the token as shown to humans (lowercase, spaces or hyphens between groups). */
    static String normalizeToken(String raw) {
        if (raw == null || raw.length() > 64) return null;
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return compact.matches(TOKEN_PATTERN) ? compact : null;
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private static String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return null;
        return email;
    }

    static String normalizeDisplayName(String name, String email) {
        String display = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (display.isEmpty() && email != null) display = email.substring(0, email.indexOf('@'));
        if (display.isBlank()) display = "Aluno";
        return display.length() > 140 ? display.substring(0, 140) : display;
    }

    static String normalizeName(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static IllegalArgumentException invalidLink() {
        return new IllegalArgumentException(
                "Link de ingresso inválido, expirado ou revogado. Peça um novo link ao professor.");
    }

    private static IllegalStateException deactivatedEnrollment() {
        return new IllegalStateException(
                "Sua matrícula nesta turma foi desativada pelo professor. Fale com ele para reativá-la.");
    }

    private record ClassroomRow(UUID id, boolean active) { }
    private record AccountRow(UUID accountId, UUID studentId) { }
    private record EnrollmentRow(UUID id, boolean active) { }
    private record EmailMatch(UUID studentId, String emailSource, boolean active,
                              boolean anonymized, boolean owned) { }
    private record RosterEntry(String name, String nickname, String preferredName, String email) { }
    private record UsableLink(UUID linkId, UUID classroomId, String classroomName, Instant expiresAt,
                              boolean requiresApproval, boolean studyAccessEnabled) { }
    private record PendingRow(UUID id, UUID accountId, String displayName, String verifiedEmail) { }

    public enum JoinStatus { JOINED_NEW_STUDENT, LINKED_EXISTING_STUDENT, JOINED, ALREADY_MEMBER, PENDING_APPROVAL }
    public enum RequestReason { APPROVAL_REQUIRED, POSSIBLE_DUPLICATE, EMAIL_REVIEW }
    public enum RequestStatus { PENDING, APPROVED, REJECTED }
    public enum ApprovalMode { CREATE_NEW, LINK_EXISTING }

    public record IssuedLink(UUID linkId, String token, Instant expiresAt, boolean requiresApproval) { }
    public record LinkStatus(boolean active, UUID linkId, Instant createdAt, Instant expiresAt, boolean expired,
                             boolean requiresApproval, int acceptedCount, Instant lastAcceptedAt,
                             int pendingRequests) { }
    public record LinkPreview(String classroomName, Instant expiresAt, boolean requiresApproval,
                              boolean alreadyMember, boolean pendingRequest) { }
    /** classroomStudyAccessEnabled is the classroom toggle only; Journeys also need the I3 flag. */
    public record JoinOutcome(JoinStatus status, UUID classroomId, String classroomName,
                              boolean classroomStudyAccessEnabled, UUID requestId) { }
    public record PendingRequest(UUID requestId, RequestReason reason, String displayName,
                                 String verifiedEmail, Instant createdAt, boolean accountAlreadyLinked,
                                 UUID emailMatchStudentId) { }
    public record LinkCandidate(UUID studentId, String name, String registration, String preferredName,
                                boolean hasEmail) { }
    public record ReviewQueue(List<PendingRequest> requests, List<LinkCandidate> candidates) { }
    public record OwnRequest(UUID requestId, RequestStatus status, String classroomName,
                             Instant createdAt, Instant decidedAt) { }
    public record Decision(UUID requestId, RequestStatus status, UUID studentId, UUID enrollmentId) { }
}
