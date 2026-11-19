package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.journey.JourneyCompletionRule;
import br.com.arenadev.journey.JourneyService;
import br.com.arenadev.journey.JourneyUnlockRule;
import br.com.arenadev.study.JourneyStudyService;
import br.com.arenadev.study.StudyPrincipal;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0074 end-to-end over HTTP, all rollout flags ON including auto-email-link.
 * Students sign in through the real Spring OIDC flow (FakeOidcProvider only replaces
 * Google/Microsoft endpoints; issuers are the production values).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.study-session-enabled=true",
        "app.student-identity.classroom-join-links-enabled=true",
        "app.student-identity.auto-email-link-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.security.join-rate-limit.max-failures=200",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class ClassroomJoinLinkHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, ClassroomJoinLinkHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private JourneyService journeys;
    @Autowired private JourneyStudyService study;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    // ------------------------------------------------------------- authorization

    @Test
    void teacherEndpointsRequireTeacherRoleAndCsrfAndNeverListTheToken() throws Exception {
        var room = studyRoom();
        String linkPath = "/api/classrooms/" + room + "/join-link";
        var anonymous = new Browser(client(), null);
        assertThat(send(anonymous.client(), "POST", linkPath, "{\"validDays\":7}", csrf(anonymous.client()))
                .statusCode()).isEqualTo(401);
        var teacher = teacher();
        assertThat(send(teacher.client(), "POST", linkPath, "{\"validDays\":7}", null).statusCode()).isEqualTo(403);
        assertThat(post(teacher, linkPath, "{\"validDays\":0}").statusCode()).isEqualTo(400);
        assertThat(post(teacher, linkPath, "{\"validDays\":31}").statusCode()).isEqualTo(400);
        String token = issueLink(teacher, room, 7, false);

        var status = get(teacher, linkPath);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).contains("\"active\":true").doesNotContain(token);
        assertThat(get(teacher, "/api/classrooms/" + room + "/join-requests").body()).doesNotContain(token);
        // Only the hash is persisted.
        assertThat(jdbc.queryForObject("select count(*) from classroom_join_links where classroom_id = ?"
                + " and encode(token_hash, 'escape') like ?", Integer.class, room, "%" + token + "%")).isZero();

        var student = student("google", sub(), mail(), true, "Aluna Papel");
        for (var path : List.of(linkPath, "/api/classrooms/" + room + "/join-requests")) {
            assertThat(get(student, path).statusCode()).as(path).isEqualTo(403);
        }
        assertThat(post(student, linkPath, "{\"validDays\":7}").statusCode()).isEqualTo(403);
        // Teacher session is not a student principal; student endpoint needs ROLE_STUDENT.
        assertThat(accept(teacher, token).statusCode()).isEqualTo(403);
        // Student without CSRF is rejected before any state change.
        assertThat(send(student.client(), "POST", "/api/student-account/classroom-links/accept",
                "{\"token\":\"" + token + "\"}", null).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select accepted_count from classroom_join_links where classroom_id = ?"
                + " and revoked_at is null", Integer.class, room)).isZero();
    }

    @Test
    void administrativeClassroomCodeAndRemovedV70RoutesNeverAuthenticate() throws Exception {
        String code = "ADM" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        var room = classrooms.create("Turma código " + code, code).id();
        var student = student("google", sub(), mail(), true, "Aluna Código");
        assertThat(accept(student, code).statusCode()).isEqualTo(400);
        assertThat(post(student, "/api/student-account/classrooms/join", "{\"code\":\"" + code + "\"}")
                .statusCode()).isIn(404, 405);
        var teacher = teacher();
        assertThat(send(teacher.client(), "PATCH", "/api/classrooms/" + room + "/self-enrollment",
                "{\"enabled\":true}", teacher.csrf()).statusCode()).isIn(404, 405);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ?",
                Integer.class, room)).isZero();
    }

    // ------------------------------------------------------------- main journeys

    @Test
    void newStudentJoinsWithLinkThenOnlyLogsInAndSeesNewJourneysWithoutAnotherCode() throws Exception {
        var room = studyRoom();
        UUID firstJourney = publishJourney(room, "Jornada 1");
        String token = issueLink(teacher(), room, 7, false);
        String subject = sub();
        var student = student("google", subject, mail(), true, "Bruna Nova");

        var preview = post(student, "/api/student-account/classroom-links/preview",
                "{\"token\":\"" + token.toLowerCase() + "\"}");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"alreadyMember\":false");
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ?",
                Integer.class, room)).as("preview is read-only").isZero();

        var joined = accept(student, grouped(token));
        assertThat(joined.statusCode()).as(joined.body()).isEqualTo(200);
        assertThat(field(joined.body(), "status")).isEqualTo("JOINED_NEW_STUDENT");
        assertThat(joined.body()).contains("\"journeysAvailable\":true")
                .doesNotContain("studentId").doesNotContain("enrollmentId");
        UUID studentId = UUID.fromString(field(get(student, "/api/student-account/session").body(), "studentId"));

        // Replay: idempotent, no duplicate student or enrollment.
        assertThat(field(accept(student, token).body(), "status")).isEqualTo("ALREADY_MEMBER");
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ? and student_id = ?",
                Integer.class, room, studentId)).isEqualTo(1);

        // Recurring access: a new browser only logs in; same student, journeys by enrollment.
        UUID secondJourney = publishJourney(room, "Jornada 2");
        var later = student("google", subject, null, null, null);
        assertThat(field(get(later, "/api/student-account/session").body(), "studentId"))
                .isEqualTo(studentId.toString());
        var list = get(later, "/api/student-account/study/journeys");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(firstJourney.toString(), secondJourney.toString());
    }

    @Test
    void loginAloneNeverLinksButLinkOfTheSameClassroomRecoversStudentXpAndProgress() throws Exception {
        var room = studyRoom();
        UUID journey = publishJourney(room, "Jornada XP");
        String email = mail();
        var existing = students.create("REG-" + UUID.randomUUID().toString().substring(0, 6), "Carla Antiga", null, email);
        var enrollment = classrooms.enroll(room, existing.id());
        jdbc.update("""
                insert into score_events (id, classroom_id, student_id, points, category, description, source)
                values (?, ?, ?, 40, 'BONUS', 'XP anterior', 'MANUAL')
                """, UUID.randomUUID(), room, existing.id());
        var principal = new StudyPrincipal(existing.id(), enrollment.enrollmentId(), room, StudyPrincipal.Role.STUDENT);
        UUID stage = study.get(journey, principal).modules().getFirst().contents().getFirst().id();
        study.complete(journey, stage, principal);
        int studentsBefore = jdbc.queryForObject("select count(*) from students", Integer.class);

        var student = student("google", sub(), email.toUpperCase(), true, "Nome Google Diferente");
        assertThat(get(student, "/api/student-account/session").body()).contains("\"studentId\":null");

        var joined = accept(student, issueLink(teacher(), room, 7, false));
        assertThat(field(joined.body(), "status")).isEqualTo("LINKED_EXISTING_STUDENT");
        assertThat(field(get(student, "/api/student-account/session").body(), "studentId"))
                .isEqualTo(existing.id().toString());
        assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isEqualTo(studentsBefore);
        assertThat(jdbc.queryForObject("select coalesce(sum(points),0) from score_events where student_id = ?",
                Integer.class, existing.id())).isEqualTo(40);
        assertThat(jdbc.queryForObject("select student_id from enrollments where id = ?", UUID.class,
                enrollment.enrollmentId())).isEqualTo(existing.id());
        var list = get(student, "/api/student-account/study/journeys");
        assertThat(list.body()).contains(journey.toString()).contains("\"completedContents\":1");
    }

    @Test
    void verifiedEmailOfAnotherClassroomGoesToTeacherReviewAndApprovalKeepsTheSameStudent() throws Exception {
        var roomA = studyRoom();
        var roomB = studyRoom();
        String email = mail();
        var existing = students.create(null, "Davi Outra Turma", null, email);
        classrooms.enroll(roomA, existing.id());
        var teacher = teacher();
        var student = student("google", sub(), email, true, "Davi Outra Turma");

        var joined = accept(student, issueLink(teacher, roomB, 7, false));
        assertThat(field(joined.body(), "status")).isEqualTo("PENDING_APPROVAL");
        assertThat(get(student, "/api/student-account/session").body()).contains("\"studentId\":null");
        assertThat(get(student, "/api/student-account/classroom-links/requests").body()).contains("PENDING");
        // Retry keeps a single pending request.
        assertThat(field(accept(student, latestTokenFor(teacher, roomB)).body(), "status")).isEqualTo("PENDING_APPROVAL");

        var queue = get(teacher, "/api/classrooms/" + roomB + "/join-requests");
        assertThat(queue.body()).contains("EMAIL_REVIEW").contains(existing.id().toString());
        String requestId = field(queue.body(), "requestId");
        assertThat(jdbc.queryForObject("select count(*) from classroom_join_requests where id = ?", Integer.class,
                UUID.fromString(requestId))).isEqualTo(1);
        // IDOR: a request cannot be decided through another classroom's URL.
        assertThat(post(teacher, "/api/classrooms/" + roomA + "/join-requests/" + requestId + "/approve",
                "{\"mode\":\"CREATE_NEW\"}").statusCode()).isEqualTo(400);
        var approved = post(teacher, "/api/classrooms/" + roomB + "/join-requests/" + requestId + "/approve",
                "{\"mode\":\"LINK_EXISTING\",\"studentId\":\"" + existing.id() + "\"}");
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        assertThat(field(get(student, "/api/student-account/session").body(), "studentId"))
                .isEqualTo(existing.id().toString());
        assertThat(jdbc.queryForObject("select count(*) from enrollments where student_id = ? and active",
                Integer.class, existing.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select verified_email from classroom_join_requests where id = ?",
                String.class, UUID.fromString(requestId))).as("PII minimized after decision").isNull();
        assertThat(post(teacher, "/api/classrooms/" + roomB + "/join-requests/" + requestId + "/reject", "{}")
                .statusCode()).as("already decided").isEqualTo(400);
    }

    @Test
    void unverifiedEmailAndMicrosoftEmailNeverLinkAndNameIsNotProof() throws Exception {
        var room = studyRoom();
        String email = mail();
        var victim = students.create(null, "Eva Vítima", null, email);
        classrooms.enroll(room, victim.id());
        String token = issueLink(teacher(), room, 7, false);

        var unverified = student("google", sub(), email, false, "Eva Vítima");
        assertThat(field(accept(unverified, token).body(), "status")).isEqualTo("PENDING_APPROVAL");
        var microsoft = student("microsoft", sub(), email, true, "Outro Nome");
        String status = field(accept(microsoft, token).body(), "status");
        assertThat(status).isEqualTo("JOINED_NEW_STUDENT");
        assertThat(jdbc.queryForObject("select count(*) from student_accounts where student_id = ?",
                Integer.class, victim.id())).isZero();
        // The Microsoft account got its own record without the (untrusted) e-mail.
        assertThat(jdbc.queryForObject("select count(*) from students where lower(email) = ?",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void deactivatedEnrollmentForeignAccountAndInvalidLinksAreRefused() throws Exception {
        var room = studyRoom();
        var teacher = teacher();
        String token = issueLink(teacher, room, 7, false);

        String disabledEmail = mail();
        var disabled = students.create(null, "Fábio Desativado", null, disabledEmail);
        classrooms.enroll(room, disabled.id());
        jdbc.update("update enrollments set active = false where student_id = ?", disabled.id());
        var disabledStudent = student("google", sub(), disabledEmail, true, "Fábio");
        var refused = accept(disabledStudent, token);
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("desativada");
        assertThat(jdbc.queryForObject("select active from enrollments where student_id = ?", Boolean.class,
                disabled.id())).isFalse();

        String ownedEmail = mail();
        var owned = students.create(null, "Gabi Dona", null, ownedEmail);
        classrooms.enroll(room, owned.id());
        var owner = student("google", sub(), ownedEmail, true, "Gabi");
        assertThat(field(accept(owner, token).body(), "status")).isEqualTo("LINKED_EXISTING_STUDENT");
        var intruder = student("google", sub(), ownedEmail, true, "Gabi");
        assertThat(accept(intruder, token).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from student_accounts where student_id = ?",
                Integer.class, owned.id())).isEqualTo(1);

        var newcomer = student("google", sub(), mail(), true, "Hugo");
        assertThat(accept(newcomer, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").statusCode()).isEqualTo(400);
        // Expired.
        jdbc.update("update classroom_join_links set created_at = now() - interval '2 days',"
                + " expires_at = now() - interval '1 second' where classroom_id = ? and revoked_at is null", room);
        assertThat(accept(newcomer, token).statusCode()).isEqualTo(400);
        // Rotation revokes the previous token; explicit revoke closes the new one.
        String rotated = issueLink(teacher, room, 1, false);
        assertThat(accept(newcomer, token).statusCode()).isEqualTo(400);
        assertThat(send(teacher.client(), "DELETE", "/api/classrooms/" + room + "/join-link", null, teacher.csrf())
                .statusCode()).isEqualTo(204);
        var revoked = accept(newcomer, rotated);
        assertThat(revoked.statusCode()).isEqualTo(400);
        assertThat(revoked.body()).contains("inválido, expirado ou revogado").doesNotContain(rotated);
        assertThat(get(newcomer, "/api/student-account/session").body()).contains("\"studentId\":null");
    }

    @Test
    void twoAccountsRacingForTheSameStudentProduceExactlyOneOwner() throws Exception {
        var room = studyRoom();
        String email = mail();
        var shared = students.create(null, "Íris Corrida", null, email);
        classrooms.enroll(room, shared.id());
        String token = issueLink(teacher(), room, 7, false);
        var first = student("google", sub(), email, true, "Íris");
        var second = student("google", sub(), email, true, "Íris");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (var browser : List.of(first, second)) {
            Callable<Integer> call = () -> { start.await(); return accept(browser, token).statusCode(); };
            results.add(pool.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var result : results) statuses.add(result.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from student_accounts where student_id = ?",
                Integer.class, shared.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from enrollments where classroom_id = ? and student_id = ?",
                Integer.class, room, shared.id())).isEqualTo(1);
    }

    @Test
    void approvalRequiredAndNameOnlyRosterNeverCreateStudentsSilently() throws Exception {
        var room = studyRoom();
        var teacher = teacher();
        var nameOnly = students.create(null, "João Só Nome", null);
        classrooms.enroll(room, nameOnly.id());
        String token = issueLink(teacher, room, 7, false);
        int before = jdbc.queryForObject("select count(*) from students", Integer.class);

        var joao = student("google", sub(), mail(), true, "Joao Google");
        assertThat(field(accept(joao, token).body(), "status")).isEqualTo("PENDING_APPROVAL");
        assertThat(jdbc.queryForObject("select count(*) from students", Integer.class)).isEqualTo(before);
        var queue = get(teacher, "/api/classrooms/" + room + "/join-requests");
        assertThat(queue.body()).contains("POSSIBLE_DUPLICATE").contains(nameOnly.id().toString());
        String request = field(queue.body(), "requestId");
        assertThat(post(teacher, "/api/classrooms/" + room + "/join-requests/" + request + "/approve",
                "{\"mode\":\"LINK_EXISTING\",\"studentId\":\"" + nameOnly.id() + "\"}").statusCode()).isEqualTo(200);
        assertThat(field(get(joao, "/api/student-account/session").body(), "studentId"))
                .isEqualTo(nameOnly.id().toString());

        // Approval ON: a stranger waits; rejection is final for that link.
        var approvalRoom = studyRoom();
        String gated = issueLink(teacher, approvalRoom, 7, true);
        var stranger = student("google", sub(), mail(), true, "Kátia");
        assertThat(field(accept(stranger, gated).body(), "status")).isEqualTo("PENDING_APPROVAL");
        String strangerRequest = field(get(teacher, "/api/classrooms/" + approvalRoom + "/join-requests").body(),
                "requestId");
        assertThat(post(teacher, "/api/classrooms/" + approvalRoom + "/join-requests/" + strangerRequest + "/reject",
                "{}").statusCode()).isEqualTo(200);
        assertThat(accept(stranger, gated).statusCode()).isEqualTo(409);
        var other = student("google", sub(), mail(), true, "Lia");
        accept(other, gated);
        String otherRequest = field(get(teacher, "/api/classrooms/" + approvalRoom + "/join-requests").body(),
                "requestId");
        assertThat(post(teacher, "/api/classrooms/" + approvalRoom + "/join-requests/" + otherRequest + "/approve",
                "{\"mode\":\"CREATE_NEW\"}").statusCode()).isEqualTo(200);
        assertThat(get(other, "/api/student-account/session").body()).doesNotContain("\"studentId\":null");
        assertThat(field(accept(other, gated).body(), "status")).isEqualTo("ALREADY_MEMBER");
    }

    @Test
    void archivingTheClassroomClosesItsLink() throws Exception {
        var room = classrooms.create("Arquivo " + UUID.randomUUID().toString().substring(0, 6), null);
        String token = issueLink(teacher(), room.id(), 7, false);
        classrooms.update(room.id(), room.name(), null, false);
        var student = student("google", sub(), mail(), true, "Mário");
        assertThat(accept(student, token).statusCode()).isEqualTo(400);
    }

    // ------------------------------------------------------------- helpers

    private UUID studyRoom() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var room = classrooms.create("Turma link " + suffix, null);
        classrooms.update(room.id(), room.name(), null, true, null, null, true);
        return room.id();
    }

    private UUID publishJourney(UUID classroomId, String title) {
        var journey = journeys.create(new JourneyService.JourneyInput(classroomId, title, null, List.of(
                new JourneyService.StageInput(null, "Etapa", null, null, JourneyUnlockRule.IMMEDIATE,
                        JourneyCompletionRule.MANUAL, null, null, null, null))));
        return journeys.publish(journey.id()).id();
    }

    private String latestTokenFor(Browser teacher, UUID room) throws Exception {
        // Status never reveals the token; reuse the request flow by issuing a fresh link.
        return issueLink(teacher, room, 7, false);
    }

    private static String grouped(String token) {
        return token.replaceAll("(.{4})(?!$)", "$1-").toLowerCase();
    }

    private static String sub() { return "sub-" + UUID.randomUUID(); }

    private static String mail() { return "a" + UUID.randomUUID().toString().substring(0, 10) + "@escola.example"; }
}
