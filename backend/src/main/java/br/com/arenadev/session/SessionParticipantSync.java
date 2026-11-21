package br.com.arenadev.session;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Late enrollment (ADR-0076 D3): a student enrolled after the class started must be able to
 * become a SessionParticipant of the CURRENT active session. The row is created idempotently
 * (unique(session_id, student_id) + ON CONFLICT DO NOTHING) and always with present=false:
 * presence is recorded only when the student actually joins or the teacher marks it.
 */
@Component
public class SessionParticipantSync {
    private final JdbcTemplate jdbc;

    public SessionParticipantSync(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds missing participants for every active enrollment of an ACTIVE session. */
    @Transactional
    public int ensureActiveEnrollments(UUID sessionId) {
        return jdbc.update("""
                insert into session_participants (id, session_id, student_id, present, connected)
                select gen_random_uuid(), s.id, e.student_id, false, false
                  from class_sessions s
                  join enrollments e on e.classroom_id = s.classroom_id and e.active
                 where s.id = ? and s.status = 'ACTIVE'
                on conflict (session_id, student_id) do nothing
                """, sessionId);
    }

    /** Adds ONE student to the active session of the classroom, if any and if actively enrolled. */
    @Transactional
    public int ensureStudentInActiveSession(UUID classroomId, UUID studentId) {
        return jdbc.update("""
                insert into session_participants (id, session_id, student_id, present, connected)
                select gen_random_uuid(), s.id, e.student_id, false, false
                  from class_sessions s
                  join enrollments e on e.classroom_id = s.classroom_id and e.active
                 where s.classroom_id = ? and s.status = 'ACTIVE' and e.student_id = ?
                on conflict (session_id, student_id) do nothing
                """, classroomId, studentId);
    }
}
