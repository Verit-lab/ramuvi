package br.com.arenadev.classroom.hub;

import br.com.arenadev.shared.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model of the classroom hub (ADR-0076 §4.1): one query set over the existing tables,
 * no new aggregate. It never returns join codes, device tokens or student lists; the Arena
 * keeps its own endpoints for that. A "stale" active session is only a hint for the UI:
 * nothing here (or anywhere) finishes a session by age, network loss or timer.
 */
@Service
public class ClassroomHubService {
    /** Same as the join code TTL: after this the projected code no longer works. */
    static final Duration STALE_AFTER = Duration.ofHours(12);
    static final int HISTORY_LIMIT = 20;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ClassroomHubService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ClassroomHubService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HubView hub(UUID classroomId) {
        var classrooms = jdbc.query("select id, name, active from classrooms where id = ?",
                (rs, row) -> new ClassroomSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getBoolean("active")), classroomId);
        if (classrooms.isEmpty()) throw new ResourceNotFoundException("Turma não encontrada.");
        Instant now = clock.instant();

        List<SessionSummary> sessions = jdbc.query("""
                select s.id, s.title, s.status, s.started_at, s.ended_at,
                       count(p.id) as participants,
                       count(p.id) filter (where p.present) as present,
                       count(p.id) filter (where p.connected) as connected,
                       (select bool_or(c.active and c.expires_at > now())
                          from session_join_codes c where c.session_id = s.id) as code_usable
                  from class_sessions s
                  left join session_participants p on p.session_id = s.id
                 where s.classroom_id = ?
                 group by s.id
                 order by (s.status = 'ACTIVE') desc, s.started_at desc
                 limit ?
                """, (rs, row) -> {
            Instant startedAt = rs.getTimestamp("started_at").toInstant();
            Timestamp ended = rs.getTimestamp("ended_at");
            boolean active = "ACTIVE".equals(rs.getString("status"));
            boolean codeUsable = rs.getBoolean("code_usable");
            boolean stale = active && (startedAt.plus(STALE_AFTER).isBefore(now) || !codeUsable);
            return new SessionSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                    rs.getString("status"), startedAt, ended == null ? null : ended.toInstant(),
                    rs.getInt("participants"), rs.getInt("present"), rs.getInt("connected"),
                    active ? Duration.between(startedAt, now).toMinutes() : null, stale);
        }, classroomId, HISTORY_LIMIT + 1);

        SessionSummary activeSession = sessions.stream()
                .filter(item -> "ACTIVE".equals(item.status())).findFirst().orElse(null);
        List<SessionSummary> history = sessions.stream()
                .filter(item -> !"ACTIVE".equals(item.status())).limit(HISTORY_LIMIT).toList();

        Totals totals = jdbc.queryForObject("""
                select
                  (select count(*) from enrollments e where e.classroom_id = ? and e.active) as students,
                  (select count(*) from journeys j where j.classroom_id = ? and j.status = 'PUBLISHED') as published,
                  (select count(*) from journeys j where j.classroom_id = ? and j.status = 'DRAFT') as drafts,
                  (select count(*) from activities a where a.classroom_id = ?) as activities,
                  (select count(*) from class_sessions s where s.classroom_id = ?) as sessions
                """, (rs, row) -> new Totals(rs.getInt("students"), rs.getInt("published"),
                rs.getInt("drafts"), rs.getInt("activities"), rs.getInt("sessions")),
                classroomId, classroomId, classroomId, classroomId, classroomId);

        return new HubView(classrooms.getFirst(), activeSession, history, totals);
    }

    public record HubView(ClassroomSummary classroom, SessionSummary activeSession,
                          List<SessionSummary> recentSessions, Totals totals) { }

    public record ClassroomSummary(UUID id, String name, boolean active) { }

    public record SessionSummary(UUID id, String title, String status, Instant startedAt, Instant endedAt,
                                 int participantCount, int presentCount, int connectedCount,
                                 Long activeMinutes, boolean stale) { }

    public record Totals(int activeStudents, int publishedJourneys, int draftJourneys, int activities,
                         int sessions) { }
}
