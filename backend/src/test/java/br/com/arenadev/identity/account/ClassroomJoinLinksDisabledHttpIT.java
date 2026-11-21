package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Default: join links OFF even with OIDC and invitations ON; routes do not exist. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.auto-email-link-enabled=true",
        "app.student-identity.oidc.google-client-id=unused",
        "app.student-identity.oidc.google-client-secret=unused",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class ClassroomJoinLinksDisabledHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, ClassroomJoinLinksDisabledHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;

    @Override int port() { return port; }

    @Test
    void linksOffMeansNoRoutesAndCapabilityOff() throws Exception {
        var room = classrooms.create("Off " + UUID.randomUUID().toString().substring(0, 6), null).id();
        var teacher = teacher();
        assertThat(get(teacher, "/api/student-account-invitations/capabilities").body())
                .contains("\"classroomJoinLinksEnabled\":false", "\"autoEmailLinkEnabled\":false");
        assertThat(post(teacher, "/api/classrooms/" + room + "/join-link", "{\"validDays\":7}").statusCode())
                .isIn(404, 405);
        var student = student("google", "sub-" + UUID.randomUUID(), "x" + UUID.randomUUID()
                .toString().substring(0, 6) + "@escola.example", true, "Off");
        assertThat(accept(student, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC").statusCode()).isIn(404, 405);
        // Auto e-mail flag alone never links at login (ADR-0074).
        assertThat(get(student, "/api/student-account/session").body()).contains("\"studentId\":null");
    }
}
