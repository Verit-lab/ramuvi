package br.com.arenadev.identity.oidc;

import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No Google/Microsoft credentials are used. This tests HTTP security,
 * redirect generation and session-bound PKCE, NOT external token validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.student-identity.oidc.enabled=true",
        "app.student-identity.enrollment-claim-enabled=true",
        "app.student-identity.study-session-enabled=true",
        "app.student-identity.oidc.google-client-id=test-google-client",
        "app.student-identity.oidc.google-client-secret=test-google-secret",
        "app.student-identity.oidc.public-base-url=http://localhost:3000",
        "app.teacher.username=oauth-teacher",
        "app.teacher.password=oauth-test-password"
})
class StudentOidcRoutesIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, StudentOidcRoutesIT.class);
    }

    @LocalServerPort private int port;

    @Test
    void googleRedirectUsesAuthorizationCodeStateNonceAndS256Pkce() throws Exception {
        HttpClient browser = client();
        var providers = get(browser, "/api/student-account/providers");
        assertThat(providers.statusCode()).isEqualTo(200);
        assertThat(providers.body()).contains("google").doesNotContain("microsoft");
        assertThat(providers.body()).doesNotContain("test-google-secret");
        assertThat(get(browser, "/api/student-account/session").statusCode()).isEqualTo(401);

        var authorize = get(browser, "/api/student-oidc/authorization/google");
        assertThat(authorize.statusCode()).isEqualTo(302);
        String destination = authorize.headers().firstValue("Location").orElseThrow();
        assertThat(destination).startsWith("https://accounts.google.com/");
        assertThat(destination).contains(
                "response_type=code", "client_id=test-google-client", "scope=openid",
                "state=", "nonce=", "code_challenge=", "code_challenge_method=S256");
        assertThat(destination).contains("redirect_uri=http://localhost:3000/api/student-oidc/callback/google");
        assertThat(destination).doesNotContain("test-google-secret");
        assertThat(get(browser, "/api/classrooms").statusCode()).isEqualTo(401);
    }

    @Test
    void teacherRemainsIsolatedFromStudentEndpoints() throws Exception {
        HttpClient teacher = client();
        String csrf = csrf(teacher);
        var login = send(teacher, "POST", "/api/auth/login",
                "{\"username\":\"oauth-teacher\",\"password\":\"oauth-test-password\"}", csrf);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(get(teacher, "/api/auth/session").statusCode()).isEqualTo(200);
        assertThat(get(teacher, "/api/student-account/session").statusCode()).isEqualTo(403);
        assertThat(send(teacher, "POST", "/api/student-account/logout", "{}", csrf)
                .statusCode()).isEqualTo(403);
        assertThat(get(teacher, "/api/auth/session").statusCode()).isEqualTo(200);
    }

    @Test
    void enrollmentClaimIsProtectedByStudentRoleAndCsrf() throws Exception {
        HttpClient anonymous = client();
        String anonymousCsrf = csrf(anonymous);
        assertThat(get(anonymous, "/api/student-account/classrooms").statusCode()).isEqualTo(401);
        assertThat(send(anonymous, "POST", "/api/student-account/enrollments/claim",
                "{\"token\":\"invalid\"}", anonymousCsrf).statusCode()).isEqualTo(401);

        HttpClient teacher = client();
        String csrf = csrf(teacher);
        assertThat(send(teacher, "POST", "/api/auth/login",
                "{\"username\":\"oauth-teacher\",\"password\":\"oauth-test-password\"}", csrf)
                .statusCode()).isEqualTo(200);
        assertThat(get(teacher, "/api/student-account/classrooms").statusCode()).isEqualTo(403);
        assertThat(send(teacher, "POST", "/api/student-account/enrollments/claim",
                "{\"token\":\"invalid\"}", csrf).statusCode()).isEqualTo(403);
        assertThat(send(teacher, "POST", "/api/student-account/enrollments/claim",
                "{\"token\":\"invalid\"}", null).statusCode()).isEqualTo(403);
        // Student must never receive a teacher invitation capability response.
        assertThat(get(anonymous, "/api/student-account-invitations/capabilities")
                .statusCode()).isEqualTo(401);
        assertThat(get(teacher, "/api/student-account-invitations/capabilities")
                .body()).contains("\"enabled\":false");
    }

    @Test
    void verifiedStudyEndpointsRejectAnonymousAndTeacherWithValidCsrf() throws Exception {
        HttpClient anonymous = client();
        String anonCsrf = csrf(anonymous);
        assertThat(get(anonymous, "/api/student-account/study/journeys").statusCode()).isEqualTo(401);
        assertThat(send(anonymous, "POST",
                "/api/student-account/study/journeys/11111111-1111-4111-8111-111111111111"
                + "/contents/22222222-2222-4222-8222-222222222222/complete",
                "{}", anonCsrf).statusCode()).isEqualTo(401);

        HttpClient teacher = client();
        String teacherCsrf = csrf(teacher);
        assertThat(send(teacher, "POST", "/api/auth/login",
                "{\"username\":\"oauth-teacher\",\"password\":\"oauth-test-password\"}",
                teacherCsrf).statusCode()).isEqualTo(200);
        assertThat(get(teacher, "/api/student-account/study/journeys").statusCode()).isEqualTo(403);
        assertThat(send(teacher, "POST",
                "/api/student-account/study/journeys/11111111-1111-4111-8111-111111111111"
                + "/contents/22222222-2222-4222-8222-222222222222/complete",
                "{}", teacherCsrf).statusCode()).isEqualTo(403);
    }

    private HttpClient client() {
        var cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return send(client, "GET", path, null, null);
    }

    private String csrf(HttpClient browser) throws Exception {
        var response = get(browser, "/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body().replaceFirst(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private HttpResponse<String> send(HttpClient browser, String method, String path,
                                      String body, String csrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        return browser.send(request.method(method, body == null ?
                HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
