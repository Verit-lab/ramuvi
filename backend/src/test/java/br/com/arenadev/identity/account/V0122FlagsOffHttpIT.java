package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
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

/**
 * Production defaults of v0.12.2 (ADR-0076 §5, restriction 4): with every new switch OFF the new
 * routes do not exist, capabilities report false and the legacy Arena start keeps working (now
 * with the typed 409). OIDC itself is ON here so the student capability route is reachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class V0122FlagsOffHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, V0122FlagsOffHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private JdbcTemplate jdbc;

    @Override int port() { return port; }

    @Test
    void newRoutesAreAbsentAndCapabilitiesAreFalseByDefault() throws Exception {
        UUID room = classrooms.create("Turma flags " + UUID.randomUUID().toString().substring(0, 6), null).id();
        var teacher = teacher();
        assertThat(get(teacher, "/api/teacher/capabilities").body())
                .contains("\"classroomHub\":false").contains("\"librarySharing\":false");
        assertThat(get(teacher, "/api/classrooms/" + room + "/hub").statusCode()).isEqualTo(404);
        assertThat(post(teacher, "/api/classrooms/" + room + "/sessions", "{}").statusCode()).isEqualTo(404);
        assertThat(post(teacher, "/api/library/resources/" + UUID.randomUUID() + "/shares",
                "{\"permission\":\"VIEW\",\"validDays\":7}").statusCode()).isEqualTo(404);
        var anonymous = new Browser(client(), null);
        assertThat(send(anonymous.client(), "POST", "/api/library/shares/preview",
                "{\"token\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567\"}", null).statusCode()).isEqualTo(404);

        var student = student("google", "sub-" + UUID.randomUUID(), null, null, "Aluna flags");
        assertThat(get(student, "/api/student-account/capabilities").body())
                .contains("\"liveMemberJoin\":false").contains("\"personalLibrary\":false");
        assertThat(get(student, "/api/student-account/live-sessions").statusCode()).isEqualTo(404);
        assertThat(get(student, "/api/student-account/library/items").statusCode()).isEqualTo(404);

        // Legacy Arena start is unchanged except for the typed conflict.
        var started = post(teacher, "/api/sessions", "{\"classroomId\":\"" + room + "\"}");
        assertThat(started.statusCode()).isEqualTo(201);
        var conflict = post(teacher, "/api/sessions", "{\"classroomId\":\"" + room + "\"}");
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(field(conflict.body(), "code")).isEqualTo("ACTIVE_SESSION_EXISTS");
        assertThat(jdbc.queryForObject("select count(*) from class_sessions where classroom_id = ?", Integer.class, room))
                .isEqualTo(1);
    }
}
