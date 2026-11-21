package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.session.SessionJoinService;
import br.com.arenadev.session.SessionService;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v0.12.2 I2 (ADR-0076 D4/D5): a verified member enters each live class of their classroom
 * without typing a code, through the REAL Spring OIDC login (FakeOidcProvider only replaces
 * the provider endpoints). Every denial is a uniform 404; the legacy bootstrap stays 410.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.live-member-join.enabled=true",
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
class LiveMemberJoinHttpIT extends OidcHttpSupport {
    private static final String LIVE = "/api/student-account/live-sessions";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, LiveMemberJoinHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private SessionService sessions;
    @Autowired private SessionJoinService joins;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    @Test
    void memberEntersTwoDifferentClassesOfTheClassroomWithoutAnyCode() throws Exception {
        UUID room = room();
        String subject = sub();
        var browser = student("google", subject, null, null, "Gabi Membro");
        UUID studentId = linkAndEnroll(browser, room, "Gabi Membro");
        int studentsBefore = count("students");

        assertThat(get(browser, LIVE).body()).isEqualTo("[]");
        UUID first = sessions.start(room, "Aula 1", Set.of()).id();
        var list = get(browser, LIVE);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String firstCode = codeOf(first);
        assertThat(list.body()).contains(first.toString()).contains("Aula 1")
                .doesNotContain(firstCode).doesNotContain("joinCode").doesNotContain("Gabi");

        var joined = post(browser, LIVE + "/" + first + "/join", "{}");
        assertThat(joined.statusCode()).as(joined.body()).isEqualTo(200);
        assertThat(field(joined.body(), "sessionId")).isEqualTo(first.toString());
        assertThat(field(joined.body(), "studentId")).isEqualTo(studentId.toString());
        String token = field(joined.body(), "token");
        assertThat(joins.validateJoinAccess(field(joined.body(), "code"), token).sessionId()).isEqualTo(first);
        assertThat(present(first, studentId)).as("presence by effective join").isTrue();

        sessions.finish(first);
        assertThat(get(browser, LIVE).body()).isEqualTo("[]");
        assertThat(post(browser, LIVE + "/" + first + "/join", "{}").statusCode()).as("finished").isEqualTo(404);

        UUID second = sessions.start(room, "Aula 2", Set.of()).id();
        assertThat(get(browser, LIVE).body()).contains(second.toString()).doesNotContain(first.toString());
        var again = post(browser, LIVE + "/" + second + "/join", "{}");
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(field(again.body(), "sessionId")).isEqualTo(second.toString());
        assertThat(field(again.body(), "studentId")).isEqualTo(studentId.toString());

        // Each meeting keeps its own participant row; no academic record was recreated.
        assertThat(present(first, studentId)).isTrue();
        assertThat(present(second, studentId)).isTrue();
        assertThat(count("students")).isEqualTo(studentsBefore);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where student_id = ?", Integer.class, studentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from enrollment_device_claims e join enrollments n"
                + " on n.id = e.enrollment_id where n.student_id = ?", Integer.class, studentId))
                .as("no X-Study-Token is minted").isZero();
    }

    @Test
    void anotherDeviceOfTheSameAccountReplacesThePreviousDeviceToken() throws Exception {
        UUID room = room();
        String subject = sub();
        var phone = student("google", subject, null, null, "Hugo Dois");
        UUID studentId = linkAndEnroll(phone, room, "Hugo Dois");
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        String phoneToken = field(post(phone, LIVE + "/" + session + "/join", "{}").body(), "token");

        var laptop = student("google", subject, null, null, null);
        var laptopJoin = post(laptop, LIVE + "/" + session + "/join", "{}");
        assertThat(laptopJoin.statusCode()).isEqualTo(200);
        String laptopToken = field(laptopJoin.body(), "token");
        assertThat(laptopToken).isNotEqualTo(phoneToken);
        assertThat(joins.validateParticipantToken(session, laptopToken).getStudent().getId()).isEqualTo(studentId);
        assertThatThrownBy(() -> joins.validateParticipantToken(session, phoneToken))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ? and student_id = ?",
                Integer.class, session, studentId)).isEqualTo(1);
    }

    @Test
    void concurrentJoinsOfOneAccountLeaveOneParticipantAndOneValidToken() throws Exception {
        UUID room = room();
        var browser = student("google", sub(), null, null, "Iara Corrida");
        UUID studentId = linkAndEnroll(browser, room, "Iara Corrida");
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        var pool = Executors.newFixedThreadPool(4);
        var gate = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> { gate.await(); return post(browser, LIVE + "/" + session + "/join", "{}"); }));
        }
        gate.countDown();
        List<String> tokens = new ArrayList<>();
        for (var future : futures) {
            var response = future.get();
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            tokens.add(field(response.body(), "token"));
        }
        pool.shutdown();
        long valid = tokens.stream().filter(token -> {
            try { joins.validateParticipantToken(session, token); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }).count();
        assertThat(valid).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ? and student_id = ?",
                Integer.class, session, studentId)).isEqualTo(1);
    }

    @Test
    void lateEnrolledMemberJoinsTheClassInProgressAndIsPresentOnlyAfterJoining() throws Exception {
        UUID room = room();
        UUID session = sessions.start(room, "Aula em curso", Set.of()).id();
        var browser = student("google", sub(), null, null, "Juca Tardio");
        var student = students.create(null, "Juca Tardio", null, null);
        link(browser, student.id());
        // Enrollment created by SQL (as the join-link flow does), bypassing ClassroomService hooks.
        jdbc.update("insert into enrollments (id, classroom_id, student_id, active) values (?, ?, ?, true)",
                UUID.randomUUID(), room, student.id());
        assertThat(jdbc.queryForObject("select count(*) from session_participants where session_id = ? and student_id = ?",
                Integer.class, session, student.id())).isZero();
        assertThat(get(browser, LIVE).body()).contains(session.toString());
        assertThat(post(browser, LIVE + "/" + session + "/join", "{}").statusCode()).isEqualTo(200);
        assertThat(present(session, student.id())).isTrue();
    }

    @Test
    void deniedCasesAreUniform404AndNeverLeakOtherClassrooms() throws Exception {
        UUID mine = room();
        UUID other = room();
        var browser = student("google", sub(), null, null, "Lia Negada");
        UUID studentId = linkAndEnroll(browser, mine, "Lia Negada");
        UUID otherSession = sessions.start(other, "Aula alheia", Set.of()).id();
        UUID mySession = sessions.start(mine, "Minha aula", Set.of()).id();

        // IDOR: a session id of another classroom.
        assertThat(get(browser, LIVE).body()).doesNotContain(otherSession.toString());
        var idor = post(browser, LIVE + "/" + otherSession + "/join", "{}");
        assertThat(idor.statusCode()).isEqualTo(404);
        var missing = post(browser, LIVE + "/" + UUID.randomUUID() + "/join", "{}");
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(field(idor.body(), "message")).isEqualTo("Aula não encontrada ou indisponível.")
                .isEqualTo(field(missing.body(), "message"));
        assertThat(idor.body()).doesNotContain("Aula alheia").doesNotContain(other.toString());

        // Account without Student/Enrollment.
        var orphan = student("google", sub(), null, null, "Sem Matrícula");
        assertThat(get(orphan, LIVE).body()).isEqualTo("[]");
        assertThat(post(orphan, LIVE + "/" + mySession + "/join", "{}").statusCode()).isEqualTo(404);

        // Anonymous, teacher session and missing CSRF.
        var anonymous = new Browser(client(), null);
        assertThat(get(anonymous, LIVE).statusCode()).isEqualTo(401);
        var teacher = teacher();
        assertThat(get(teacher, LIVE).statusCode()).isEqualTo(403);
        assertThat(send(browser.client(), "POST", LIVE + "/" + mySession + "/join", "{}", null).statusCode())
                .isEqualTo(403);
        assertThat(present(mySession, studentId)).isFalse();

        // Enrollment blocked, then classroom inactive, then identity revoked: all re-checked per request.
        jdbc.update("update enrollments set active = false where student_id = ? and classroom_id = ?", studentId, mine);
        assertThat(post(browser, LIVE + "/" + mySession + "/join", "{}").statusCode()).isEqualTo(404);
        assertThat(get(browser, LIVE).body()).isEqualTo("[]");
        jdbc.update("update enrollments set active = true where student_id = ? and classroom_id = ?", studentId, mine);
        jdbc.update("update classrooms set active = false where id = ?", mine);
        assertThat(post(browser, LIVE + "/" + mySession + "/join", "{}").statusCode()).isEqualTo(404);
        jdbc.update("update classrooms set active = true where id = ?", mine);
        assertThat(post(browser, LIVE + "/" + mySession + "/join", "{}").statusCode()).isEqualTo(200);
        jdbc.update("""
                update student_account_identities set revoked_at = now()
                 where account_id = (select id from student_accounts where student_id = ?)
                """, studentId);
        assertThat(post(browser, LIVE + "/" + mySession + "/join", "{}").statusCode()).isEqualTo(404);
        assertThat(get(browser, LIVE).body()).isEqualTo("[]");
    }

    @Test
    void guestCodeEntryAndLegacyBootstrapKeepTheirContracts() throws Exception {
        UUID room = room();
        var guestStudent = students.create("G-" + UUID.randomUUID().toString().substring(0, 6), "Nina Convidada", null, null);
        classrooms.enroll(room, guestStudent.id());
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        var anonymous = new Browser(client(), null);
        var guest = send(anonymous.client(), "POST", "/api/join/" + codeOf(session),
                "{\"identity\":\"" + guestStudent.registration() + "\",\"rememberDevice\":false}", null);
        assertThat(guest.statusCode()).as(guest.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from student_accounts where student_id = ?",
                Integer.class, guestStudent.id())).as("code never creates an account link").isZero();
        var bootstrap = send(anonymous.client(), "POST", "/api/learn/journeys/redeem/bootstrap",
                "{\"code\":\"J-ABC123\",\"identity\":\"Nina Convidada\"}", null);
        assertThat(bootstrap.statusCode()).isEqualTo(410);
    }

    @Test
    void expiredGuestCodeDoesNotLockTheMemberOutOfAnActiveClass() throws Exception {
        UUID room = room();
        var first = student("google", sub(), null, null, "Olga Doze Horas");
        UUID firstId = linkAndEnroll(first, room, "Olga Doze Horas");
        var second = student("google", sub(), null, null, "Pedro Doze Horas");
        UUID secondId = linkAndEnroll(second, room, "Pedro Doze Horas");
        UUID session = sessions.start(room, "Aula longa", Set.of()).id();
        expireCode(session);
        String expired = codeOf(session);

        // Guests (D5) still depend on the TTL: the expired code is refused.
        var anonymous = new Browser(client(), null);
        assertThat(send(anonymous.client(), "GET", "/api/join/" + expired, null, null).statusCode()).isEqualTo(400);

        // The member discovers the ACTIVE class regardless of the code TTL...
        assertThat(get(first, LIVE).body()).contains(session.toString());
        var joined = post(first, LIVE + "/" + session + "/join", "{}");
        assertThat(joined.statusCode()).as(joined.body()).isEqualTo(200);
        // ...and the join renewed the code (new random value + new TTL) so the existing runtime works.
        String renewed = field(joined.body(), "code");
        assertThat(renewed).isNotEqualTo(expired).isEqualTo(codeOf(session));
        assertThat(codeUsable(session)).isTrue();
        assertThat(joins.validateJoinAccess(renewed, field(joined.body(), "token")).studentId()).isEqualTo(firstId);
        assertThat(present(session, firstId)).isTrue();
        assertThat(send(anonymous.client(), "GET", "/api/join/" + expired, null, null).statusCode())
                .as("the old code is not revived").isEqualTo(404);

        // A still-valid code is reused, never rotated again by a member join.
        var other = post(second, LIVE + "/" + session + "/join", "{}");
        assertThat(other.statusCode()).isEqualTo(200);
        assertThat(field(other.body(), "code")).isEqualTo(renewed);
        assertThat(present(session, secondId)).isTrue();
    }

    @Test
    void revokedCodeIsNeverReactivatedByAMemberJoin() throws Exception {
        UUID room = room();
        var browser = student("google", sub(), null, null, "Quésia Revogada");
        UUID studentId = linkAndEnroll(browser, room, "Quésia Revogada");
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        for (boolean expired : new boolean[] {false, true}) {
            jdbc.update("update session_join_codes set active = false where session_id = ?", session);
            if (expired) expireCode(session);
            var before = jdbc.queryForMap("select code, active, expires_at from session_join_codes where session_id = ?", session);

            assertThat(get(browser, LIVE).body()).isEqualTo("[]");
            var denied = post(browser, LIVE + "/" + session + "/join", "{}");
            assertThat(denied.statusCode()).isEqualTo(404);
            assertThat(field(denied.body(), "message")).isEqualTo("Aula não encontrada ou indisponível.");
            assertThat(jdbc.queryForMap("select code, active, expires_at from session_join_codes where session_id = ?", session))
                    .as("revoked code untouched (expired=%s)", expired).isEqualTo(before);
            assertThat(present(session, studentId)).isFalse();
        }
    }

    @Test
    void finishedClassWithExpiredCodeIsNeverReopened() throws Exception {
        UUID room = room();
        var browser = student("google", sub(), null, null, "Rui Encerrado");
        UUID studentId = linkAndEnroll(browser, room, "Rui Encerrado");
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        expireCode(session);
        sessions.finish(session);
        var before = jdbc.queryForMap("select code, active, expires_at from session_join_codes where session_id = ?", session);

        assertThat(get(browser, LIVE).body()).isEqualTo("[]");
        assertThat(post(browser, LIVE + "/" + session + "/join", "{}").statusCode()).isEqualTo(404);
        // Even a code still flagged active (inconsistent legacy row) must not reopen a FINISHED class.
        jdbc.update("update session_join_codes set active = true where session_id = ?", session);
        assertThat(post(browser, LIVE + "/" + session + "/join", "{}").statusCode()).isEqualTo(404);
        jdbc.update("update session_join_codes set active = false where session_id = ?", session);
        assertThat(jdbc.queryForMap("select code, active, expires_at from session_join_codes where session_id = ?", session))
                .isEqualTo(before);
        assertThat(jdbc.queryForObject("select status from class_sessions where id = ?", String.class, session))
                .isEqualTo("FINISHED");
        assertThat(present(session, studentId)).isFalse();
    }

    @Test
    void concurrentMemberJoinsRenewAnExpiredCodeExactlyOnce() throws Exception {
        UUID room = room();
        List<Browser> members = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var browser = student("google", sub(), null, null, "Sara Paralela " + i);
            linkAndEnroll(browser, room, "Sara Paralela " + i);
            members.add(browser);
        }
        UUID session = sessions.start(room, "Aula", Set.of()).id();
        expireCode(session);
        String expired = codeOf(session);
        var pool = Executors.newFixedThreadPool(members.size() + 1);
        var gate = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();
        for (Browser member : members) {
            futures.add(pool.submit(() -> { gate.await(); return post(member, LIVE + "/" + session + "/join", "{}"); }));
        }
        // The teacher's own ensureCode competes for the same row lock.
        Future<String> teacherCode = pool.submit(() -> { gate.await(); return joins.ensureCode(session).code(); });
        gate.countDown();
        Set<String> codes = new java.util.HashSet<>();
        for (var future : futures) {
            var response = future.get();
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            codes.add(field(response.body(), "code"));
        }
        codes.add(teacherCode.get());
        pool.shutdown();
        assertThat(codes).as("one serialized renewal").hasSize(1).doesNotContain(expired);
        assertThat(codes).containsExactly(codeOf(session));
        assertThat(codeUsable(session)).isTrue();
    }

    @Test
    void memberRenewalRacingWithFinishNeverReopensTheClass() throws Exception {
        for (int round = 0; round < 3; round++) {
            UUID room = room();
            List<Browser> members = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                var browser = student("google", sub(), null, null, "Téo Corrida " + i);
                linkAndEnroll(browser, room, "Téo Corrida " + i);
                members.add(browser);
            }
            UUID session = sessions.start(room, "Aula", Set.of()).id();
            expireCode(session);
            var pool = Executors.newFixedThreadPool(members.size() + 1);
            var gate = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (Browser member : members) {
                futures.add(pool.submit(() -> { gate.await(); return post(member, LIVE + "/" + session + "/join", "{}"); }));
            }
            Future<?> finish = pool.submit(() -> { gate.await(); return sessions.finish(session); });
            gate.countDown();
            finish.get();
            for (var future : futures) {
                var response = future.get();
                assertThat(response.statusCode()).as(response.body()).isIn(200, 404);
                if (response.statusCode() == 200) {
                    // Joined before the finish committed: its access dies with the session.
                    assertThatThrownBy(() -> joins.validateParticipantToken(session, field(response.body(), "token")))
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }
            pool.shutdown();
            assertThat(jdbc.queryForObject("select status from class_sessions where id = ?", String.class, session))
                    .isEqualTo("FINISHED");
            assertThat(jdbc.queryForObject("select active from session_join_codes where session_id = ?", Boolean.class, session))
                    .as("renewal never survives the finish").isFalse();
            for (Browser member : members) {
                assertThat(get(member, LIVE).body()).isEqualTo("[]");
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private UUID room() {
        return classrooms.create("Turma membro " + UUID.randomUUID().toString().substring(0, 8), null).id();
    }

    /** Effect of ADR-0070/0074 (invite or join link): account → Student, plus an active enrollment. */
    private UUID linkAndEnroll(Browser browser, UUID room, String name) throws Exception {
        var student = students.create(null, name, null, null);
        link(browser, student.id());
        classrooms.enroll(room, student.id());
        return student.id();
    }

    private void link(Browser browser, UUID studentId) throws Exception {
        UUID accountId = UUID.fromString(field(get(browser, "/api/student-account/session").body(), "accountId"));
        jdbc.update("update student_accounts set student_id = ? where id = ?", studentId, accountId);
    }

    private String codeOf(UUID session) {
        return jdbc.queryForObject("select code from session_join_codes where session_id = ?", String.class, session);
    }

    private void expireCode(UUID session) {
        // A real 12h TTL elapsed while the class stayed ACTIVE.
        jdbc.update("update session_join_codes set expires_at = now() - interval '1 hour' where session_id = ?", session);
    }

    private boolean codeUsable(UUID session) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select active and expires_at > now() from session_join_codes where session_id = ?", Boolean.class, session));
    }

    private boolean present(UUID session, UUID student) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select present from session_participants where session_id = ? and student_id = ?",
                Boolean.class, session, student));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String sub() { return "sub-" + UUID.randomUUID(); }
}
