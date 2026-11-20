package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.12.2 I1 (ADR-0076 D2/D3) over HTTP against PostgreSQL 17: classroom hub read model,
 * short start, typed 409 under real concurrency, confirmed "finish and start another",
 * stale-session hint without auto-finish and idempotent late enrollment without fake presence.
 * Lives in this package only to reuse the real-OIDC HTTP helpers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.classroom-hub.enabled=true",
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.security.join-rate-limit.max-failures=200",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class ClassroomHubHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, ClassroomHubHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    @Test
    void hubAndSessionLifecycleRequireTeacherRoleAndCsrf() throws Exception {
        UUID room = room();
        var anonymous = new Browser(client(), null);
        assertThat(get(anonymous, "/api/classrooms/" + room + "/hub").statusCode()).isEqualTo(401);
        var student = student("google", "sub-" + UUID.randomUUID(), null, null, "Aluna");
        assertThat(get(student, "/api/classrooms/" + room + "/hub").statusCode()).isEqualTo(403);
        assertThat(post(student, "/api/classrooms/" + room + "/sessions", "{}").statusCode()).isEqualTo(403);
        var teacher = teacher();
        assertThat(send(teacher.client(), "POST", "/api/classrooms/" + room + "/sessions", "{}", null)
                .statusCode()).as("missing CSRF").isEqualTo(403);
        assertThat(activeCount(room)).isZero();
        assertThat(get(teacher, "/api/classrooms/" + UUID.randomUUID() + "/hub").statusCode()).isEqualTo(404);
        var capabilities = get(teacher, "/api/teacher/capabilities");
        assertThat(capabilities.body()).contains("\"classroomHub\":true").contains("\"librarySharing\":false");
    }

    @Test
    void startFromTheClassroomIsOneRequestAndHubShowsTheActiveClassWithoutSecrets() throws Exception {
        UUID room = room();
        enroll(room, "Ana Hub");
        var teacher = teacher();
        var started = post(teacher, "/api/classrooms/" + room + "/sessions", "{\"title\":\"Aula 20/11\"}");
        assertThat(started.statusCode()).as(started.body()).isEqualTo(201);
        String sessionId = field(started.body(), "id");

        var hub = get(teacher, "/api/classrooms/" + room + "/hub");
        assertThat(hub.statusCode()).isEqualTo(200);
        assertThat(hub.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(hub.body()).contains("\"activeSession\":{\"id\":\"" + sessionId + "\"")
                .contains("\"participantCount\":1").contains("\"presentCount\":0")
                .contains("\"stale\":false").contains("\"activeStudents\":1");
        String joinCode = jdbc.queryForObject("select code from session_join_codes where session_id = ?::uuid",
                String.class, sessionId);
        assertThat(hub.body()).doesNotContain(joinCode).doesNotContain("accessToken").doesNotContain("Ana Hub");
    }

    @Test
    void concurrentStartsLeaveExactlyOneActiveSessionAndLosersGetTyped409() throws Exception {
        UUID room = room();
        enroll(room, "Bia Corrida");
        var teacher = teacher();
        int requests = 8;
        var pool = Executors.newFixedThreadPool(requests);
        var gate = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            final boolean legacy = i % 2 == 0;
            futures.add(pool.submit(() -> {
                gate.await();
                return legacy
                        ? post(teacher, "/api/sessions", "{\"classroomId\":\"" + room + "\",\"title\":\"Legado\"}")
                        : post(teacher, "/api/classrooms/" + room + "/sessions", "{\"title\":\"Central\"}");
            }));
        }
        gate.countDown();
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (var future : futures) responses.add(future.get());
        pool.shutdown();

        assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(1);
        String winner = responses.stream().filter(r -> r.statusCode() == 201).findFirst()
                .map(r -> field(r.body(), "id")).orElseThrow();
        for (var response : responses) {
            if (response.statusCode() == 201) continue;
            assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
            assertThat(field(response.body(), "code")).isEqualTo("ACTIVE_SESSION_EXISTS");
            assertThat(response.body()).contains("\"activeSession\":{\"id\":\"" + winner + "\"");
        }
        assertThat(activeCount(room)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from class_sessions where classroom_id = ?",
                Integer.class, room)).isEqualTo(1);
    }

    @Test
    void finishAndStartAnotherNeedsConfirmationAndTheSessionTheTeacherSaw() throws Exception {
        UUID room = room();
        UUID studentId = enroll(room, "Caio Troca");
        var teacher = teacher();
        String first = field(post(teacher, "/api/classrooms/" + room + "/sessions", "{\"title\":\"Aula 1\"}").body(), "id");
        jdbc.update("update session_participants set present = true where session_id = ?::uuid", first);
        jdbc.update("""
                insert into score_events (id, classroom_id, student_id, session_id, points, category, description, source)
                values (?, ?, ?, ?::uuid, 10, 'BONUS', 'XP da aula 1', 'MANUAL')
                """, UUID.randomUUID(), room, studentId, first);

        String path = "/api/classrooms/" + room + "/sessions/switch";
        assertThat(post(teacher, path, "{\"expectedActiveSessionId\":\"" + first + "\"}").statusCode())
                .as("no confirmation").isEqualTo(400);
        var stale = post(teacher, path, "{\"expectedActiveSessionId\":\"" + UUID.randomUUID() + "\",\"confirm\":true}");
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(field(stale.body(), "code")).isEqualTo("ACTIVE_SESSION_CHANGED");
        assertThat(stale.body()).contains("\"activeSession\":{\"id\":\"" + first + "\"");
        assertThat(sessionStatus(first)).isEqualTo("ACTIVE");

        var switched = post(teacher, path,
                "{\"expectedActiveSessionId\":\"" + first + "\",\"confirm\":true,\"title\":\"Aula 2\"}");
        assertThat(switched.statusCode()).as(switched.body()).isEqualTo(201);
        String second = switched.body().replaceFirst(".*\"started\":\\{\"id\":\"([^\"]+)\".*", "$1");
        assertThat(second).isNotEqualTo(first);
        assertThat(sessionStatus(first)).isEqualTo("FINISHED");
        assertThat(sessionStatus(second)).isEqualTo("ACTIVE");
        assertThat(activeCount(room)).isEqualTo(1);

        // The previous meeting keeps its own presence, XP and audit trail.
        assertThat(jdbc.queryForObject("select present from session_participants where session_id = ?::uuid",
                Boolean.class, first)).isTrue();
        assertThat(jdbc.queryForObject("select present from session_participants where session_id = ?::uuid",
                Boolean.class, second)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from score_events where session_id = ?::uuid",
                Integer.class, first)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select active from session_join_codes where session_id = ?::uuid",
                Boolean.class, first)).isFalse();
        assertThat(jdbc.queryForList("select event_type from session_events where session_id = ?::uuid order by sequence_no",
                String.class, first)).contains("SESSION_STARTED", "SESSION_FINISHED");
        assertThat(jdbc.queryForObject("select payload_json from session_events where session_id = ?::uuid"
                + " and event_type = 'SESSION_STARTED'", String.class, second)).contains(first);

        // Replaying the same confirmation does not finish the new class.
        assertThat(post(teacher, path, "{\"expectedActiveSessionId\":\"" + first + "\",\"confirm\":true}")
                .statusCode()).isEqualTo(409);
        assertThat(sessionStatus(second)).isEqualTo("ACTIVE");

        var hub = get(teacher, "/api/classrooms/" + room + "/hub").body();
        assertThat(hub).contains("\"activeSession\":{\"id\":\"" + second + "\"")
                .contains("\"recentSessions\":[{\"id\":\"" + first + "\"").contains("\"sessions\":2");
    }

    @Test
    void forgottenSessionIsFlaggedButNeverFinishedByAge() throws Exception {
        UUID room = room();
        var teacher = teacher();
        String sessionId = field(post(teacher, "/api/classrooms/" + room + "/sessions", "{}").body(), "id");
        jdbc.update("update class_sessions set started_at = now() - interval '3 days' where id = ?::uuid", sessionId);
        jdbc.update("update session_join_codes set expires_at = now() - interval '1 minute' where session_id = ?::uuid",
                sessionId);

        var hub = get(teacher, "/api/classrooms/" + room + "/hub").body();
        assertThat(hub).contains("\"activeSession\":{\"id\":\"" + sessionId + "\"").contains("\"stale\":true");
        assertThat(sessionStatus(sessionId)).isEqualTo("ACTIVE");
        var again = post(teacher, "/api/classrooms/" + room + "/sessions", "{}");
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(sessionStatus(sessionId)).as("no silent finish").isEqualTo("ACTIVE");
    }

    @Test
    void lateEnrollmentAddsParticipantIdempotentlyWithoutPresence() throws Exception {
        UUID room = room();
        enroll(room, "Duda Pontual");
        var teacher = teacher();
        String sessionId = field(post(teacher, "/api/classrooms/" + room + "/sessions", "{}").body(), "id");
        String registration = "LATE-" + UUID.randomUUID().toString().substring(0, 6);
        var late = students.create(registration, "Edu Atrasado", null, null);
        classrooms.enroll(room, late.id());
        classrooms.enroll(room, late.id());
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ?::uuid"
                + " and student_id = ?", Integer.class, sessionId, late.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select present from session_participants where session_id = ?::uuid"
                + " and student_id = ?", Boolean.class, sessionId, late.id())).isFalse();

        // Deactivated then reactivated: still one row, still not present.
        classrooms.setEnrollmentActive(room, late.id(), false);
        classrooms.setEnrollmentActive(room, late.id(), true);
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ?::uuid"
                + " and student_id = ?", Integer.class, sessionId, late.id())).isEqualTo(1);

        // A student enrolled by a path that bypasses ClassroomService (e.g. join link SQL) is
        // added when joining by the projected code; presence only on that effective join.
        var other = students.create("LATE2-" + UUID.randomUUID().toString().substring(0, 6), "Fê Link", null, null);
        jdbc.update("insert into enrollments (id, classroom_id, student_id, active) values (?, ?, ?, true)",
                UUID.randomUUID(), room, other.id());
        String code = jdbc.queryForObject("select code from session_join_codes where session_id = ?::uuid",
                String.class, sessionId);
        var anonymous = new Browser(client(), null);
        var joined = send(anonymous.client(), "POST", "/api/join/" + code,
                "{\"identity\":\"" + other.registration() + "\",\"rememberDevice\":false}", null);
        assertThat(joined.statusCode()).as(joined.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select present from session_participants where session_id = ?::uuid"
                + " and student_id = ?", Boolean.class, sessionId, other.id())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ?::uuid"
                + " and present", Integer.class, sessionId)).isEqualTo(1);
    }

    private UUID room() {
        return classrooms.create("Turma central " + UUID.randomUUID().toString().substring(0, 8), null).id();
    }

    private UUID enroll(UUID room, String name) {
        var student = students.create("R-" + UUID.randomUUID().toString().substring(0, 8), name, null, null);
        classrooms.enroll(room, student.id());
        return student.id();
    }

    private int activeCount(UUID room) {
        return jdbc.queryForObject("select count(*) from class_sessions where classroom_id = ? and status = 'ACTIVE'",
                Integer.class, room);
    }

    private String sessionStatus(String id) {
        return jdbc.queryForObject("select status from class_sessions where id = ?::uuid", String.class, id);
    }
}
