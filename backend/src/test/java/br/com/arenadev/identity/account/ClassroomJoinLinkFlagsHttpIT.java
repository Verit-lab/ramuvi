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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-0074 with auto-email-link OFF (production default) and a tight rate limit. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.classroom-join-links-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.security.join-rate-limit.max-failures=4",
        "app.security.join-rate-limit.block-seconds=60",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class ClassroomJoinLinkFlagsHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, ClassroomJoinLinkFlagsHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    @Test
    void withAutoEmailOffAMatchingTeacherEmailWaitsForTheTeacherWithoutDuplicates() throws Exception {
        var room = classrooms.create("Flag off " + UUID.randomUUID().toString().substring(0, 6), null).id();
        String email = "off" + UUID.randomUUID().toString().substring(0, 8) + "@escola.example";
        var existing = students.create(null, "Nina Cadastrada", null, email);
        classrooms.enroll(room, existing.id());
        var teacher = teacher();
        assertThat(get(teacher, "/api/student-account-invitations/capabilities").body())
                .contains("\"classroomJoinLinksEnabled\":true", "\"autoEmailLinkEnabled\":false");
        var student = student("google", "sub-" + UUID.randomUUID(), email, true, "Nina");

        var joined = accept(student, issueLink(teacher, room, 7, false));
        assertThat(field(joined.body(), "status")).isEqualTo("PENDING_APPROVAL");
        // Study journeys stay unavailable while the I3 flag is OFF.
        assertThat(joined.body()).contains("\"journeysAvailable\":false");
        assertThat(get(student, "/api/student-account/session").body()).contains("\"studentId\":null");
        assertThat(jdbc.queryForObject("select count(*) from students where lower(email) = ?",
                Integer.class, email)).isEqualTo(1);

        String request = field(get(teacher, "/api/classrooms/" + room + "/join-requests").body(), "requestId");
        assertThat(post(teacher, "/api/classrooms/" + room + "/join-requests/" + request + "/approve",
                "{\"mode\":\"LINK_EXISTING\",\"studentId\":\"" + existing.id() + "\"}").statusCode()).isEqualTo(200);
        assertThat(field(get(student, "/api/student-account/session").body(), "studentId"))
                .isEqualTo(existing.id().toString());
    }

    @Test
    void invalidLinkGuessingIsThrottled() throws Exception {
        var student = student("google", "sub-" + UUID.randomUUID(), null, null, "Rate");
        int bad = 0;
        int throttled = 0;
        for (int attempt = 0; attempt < 6; attempt++) {
            int status = accept(student, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB" + (char) ('A' + attempt)).statusCode();
            if (status == 400) bad++;
            if (status == 429) throttled++;
        }
        assertThat(bad).isEqualTo(4);
        assertThat(throttled).isEqualTo(2);
    }
}
