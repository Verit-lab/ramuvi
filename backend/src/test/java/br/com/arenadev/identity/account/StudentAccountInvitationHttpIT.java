package br.com.arenadev.identity.account;

import br.com.arenadev.classroom.ClassroomService;
import br.com.arenadev.classroom.StudentService;
import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.invitation-issuance-enabled=true",
        "app.teacher.username=identity-teacher",
        "app.teacher.password=identity-teacher-test"
})
class StudentAccountInvitationHttpIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, StudentAccountInvitationHttpIT.class);
    }

    @LocalServerPort private int port;
    @Autowired private ClassroomService classrooms;
    @Autowired private StudentService students;

    @Test
    void teacherCanIssueAndRevokeWhileAnonymousAndMissingCsrfAreRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var s = students.create("HTTP-" + suffix, "Aluno HTTP " + suffix, null);
        var c = classrooms.create("HTTP v12 " + suffix, "HT" + suffix);
        var enrollment = classrooms.enroll(c.id(), s.id());
        String endpoint = "/api/student-account-invitations/enrollments/" + enrollment.enrollmentId();

        HttpClient anonymous = client();
        String anonymousCsrf = csrf(anonymous);
        assertThat(send(anonymous, "POST", endpoint, "{}", anonymousCsrf).statusCode()).isEqualTo(401);

        HttpClient teacher = client();
        String token = csrf(teacher);
        assertThat(send(teacher, "POST", "/api/auth/login",
                "{\"username\":\"identity-teacher\",\"password\":\"identity-teacher-test\"}", token)
                .statusCode()).isEqualTo(200);
        assertThat(send(teacher, "POST", endpoint, "{}", null).statusCode()).isEqualTo(403);
        var invitation = send(teacher, "POST", endpoint, "{}", token);
        assertThat(invitation.statusCode()).isEqualTo(201);
        assertThat(invitation.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
        assertThat(invitation.body()).contains("\"inviteId\":", "\"expiresAt\":", "\"token\":");

        var match = Pattern.compile("\\\"inviteId\\\":\\\"([0-9a-f-]+)\\\"").matcher(invitation.body());
        assertThat(match.find()).isTrue();
        String inviteId = match.group(1);
        assertThat(send(teacher, "DELETE", "/api/student-account-invitations/" + inviteId,
                null, token).statusCode()).isEqualTo(204);
    }

    private HttpClient client() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    private String csrf(HttpClient client) throws Exception {
        var response = send(client, "GET", "/api/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body().replaceFirst(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String body, String csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        return client.send(request.method(method, body == null ?
                HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
