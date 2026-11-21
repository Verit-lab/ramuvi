package br.com.arenadev.identity.account;

import br.com.arenadev.session.SessionJoinService;
import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ADR-0076 D4 / I2: live classes of a verified member, discovered in the backend from the
 * OIDC identity. Every request re-checks, in ONE query: identity not revoked, account ACTIVE,
 * linked Student active and not anonymized, enrollment active, classroom active, session ACTIVE
 * and its transitory join code not REVOKED. The code's 12h TTL governs guests (/join, D5), not
 * members: an ACTIVE class whose code merely expired stays discoverable and joinable, and the
 * join renews the code (SessionJoinService#issueMemberAccess). The client never sends
 * account/student/enrollment ids; a session of another classroom is indistinguishable from a
 * missing one (404).
 */
@Service
public class MemberLiveSessionService {
    private static final String ELIGIBLE = """
            select s.id as session_id, c.id as classroom_id, c.name as classroom_name,
                   s.title as session_title, s.started_at, st.id as student_id
              from student_account_identities i
              join student_accounts a on a.id = i.account_id
              join students st on st.id = a.student_id
              join enrollments e on e.student_id = st.id and e.active
              join classrooms c on c.id = e.classroom_id and c.active
              join class_sessions s on s.classroom_id = c.id and s.status = 'ACTIVE'
              join session_join_codes j on j.session_id = s.id and j.active
             where i.issuer = ? and i.subject = ? and i.method = 'OIDC' and i.revoked_at is null
               and a.status = 'ACTIVE' and st.active and st.anonymized_at is null
            """;

    private final JdbcTemplate jdbc;
    private final SessionJoinService joins;

    public MemberLiveSessionService(JdbcTemplate jdbc, SessionJoinService joins) {
        this.jdbc = jdbc;
        this.joins = joins;
    }

    @Transactional(readOnly = true)
    public List<LiveClass> list(String issuer, String subject) {
        return jdbc.query(ELIGIBLE + " order by s.started_at desc", (rs, row) -> new LiveClass(
                rs.getObject("session_id", UUID.class), rs.getObject("classroom_id", UUID.class),
                rs.getString("classroom_name"), rs.getString("session_title"),
                rs.getTimestamp("started_at").toInstant()), issuer, subject);
    }

    @Transactional
    public SessionJoinService.JoinAccessView join(String issuer, String subject, UUID sessionId) {
        // FOR SHARE on the identity: a concurrent revocation waits for (or blocks) this join.
        var eligible = jdbc.query(ELIGIBLE + " and s.id = ? for share of i",
                (rs, row) -> rs.getObject("student_id", UUID.class), issuer, subject, sessionId);
        if (eligible.size() != 1) {
            throw new ResourceNotFoundException("Aula não encontrada ou indisponível.");
        }
        return joins.issueMemberAccess(sessionId, eligible.getFirst());
    }

    public record LiveClass(UUID sessionId, UUID classroomId, String classroomName, String sessionTitle,
                            Instant startedAt) { }
}
