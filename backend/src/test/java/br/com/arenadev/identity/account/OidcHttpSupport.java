package br.com.arenadev.identity.account;

import br.com.arenadev.support.FakeOidcProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared HTTP helpers for ADR-0074 ITs. Students authenticate through the real Spring
 * OIDC login (authorization redirect with state/nonce/PKCE, callback, token exchange,
 * RS256 signature, issuer/audience/nonce validation, StudentOidcUserService) against a
 * loopback {@link FakeOidcProvider}. Only the provider endpoints are fake.
 */
abstract class OidcHttpSupport {
    static final String GOOGLE = "https://accounts.google.com";
    static final String MICROSOFT = "https://login.microsoftonline.com/11111111-2222-4333-8444-555555555555/v2.0";
    static final String CLIENT_ID = "it-client";
    static final FakeOidcProvider PROVIDER = new FakeOidcProvider(CLIENT_ID);
    static final String TEACHER_USER = "link-teacher";
    static final String TEACHER_PASSWORD = "link-teacher-password";

    /** Overrides only the provider endpoints; issuers stay the production values. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderRegistrations {
        @Bean
        @Primary
        ClientRegistrationRepository fakeProviderRegistrations() {
            return new InMemoryClientRegistrationRepository(
                    registration("google", GOOGLE, "Google"),
                    registration("microsoft", MICROSOFT, "Microsoft"));
        }

        private static ClientRegistration registration(String id, String issuer, String name) {
            return ClientRegistration.withRegistrationId(id)
                    .clientId(CLIENT_ID)
                    .clientSecret("it-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/api/student-oidc/callback/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri(PROVIDER.baseUrl() + "/authorize")
                    .tokenUri(PROVIDER.baseUrl() + "/token")
                    .jwkSetUri(PROVIDER.baseUrl() + "/jwks")
                    .issuerUri(issuer)
                    .userNameAttributeName("sub")
                    .clientName(name)
                    .build();
        }
    }

    abstract int port();

    record Browser(HttpClient client, String csrf) { }

    Browser teacher() throws Exception {
        HttpClient client = client();
        String csrf = csrf(client);
        var login = send(client, "POST", "/api/auth/login",
                "{\"username\":\"" + TEACHER_USER + "\",\"password\":\"" + TEACHER_PASSWORD + "\"}", csrf);
        assertThat(login.statusCode()).isEqualTo(200);
        return new Browser(client, csrf(client));
    }

    Browser student(String provider, String subject, String email, Boolean emailVerified, String name)
            throws Exception {
        HttpClient client = client();
        var authorize = send(client, "GET", "/api/student-oidc/authorization/" + provider, null, null);
        assertThat(authorize.statusCode()).isEqualTo(302);
        Map<String, String> query = query(URI.create(authorize.headers().firstValue("Location").orElseThrow()));
        assertThat(query).containsKeys("state", "nonce", "code_challenge");
        String issuer = provider.equals("google") ? GOOGLE : MICROSOFT;
        String code = PROVIDER.prepareCode(issuer, subject, query.get("nonce"), email, emailVerified, name);
        URI callback = URI.create(query.get("redirect_uri"));
        var completed = send(client, "GET", callback.getRawPath() + "?code=" + code + "&state="
                + java.net.URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8), null, null);
        assertThat(completed.statusCode()).as("OIDC callback").isEqualTo(302);
        assertThat(completed.headers().firstValue("Location").orElseThrow()).endsWith("/learn?connected=1");
        return new Browser(client, csrf(client));
    }

    HttpClient client() {
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    String csrf(HttpClient client) throws Exception {
        var response = send(client, "GET", "/api/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body().replaceFirst(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    HttpResponse<String> get(Browser browser, String path) throws Exception {
        return send(browser.client(), "GET", path, null, null);
    }

    HttpResponse<String> post(Browser browser, String path, String body) throws Exception {
        return send(browser.client(), "POST", path, body, browser.csrf());
    }

    HttpResponse<String> send(HttpClient client, String method, String path, String body, String csrf)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port() + path))
                .header("Content-Type", "application/json");
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Teacher issues a link; returns the raw token (shown only in this response). */
    String issueLink(Browser teacher, java.util.UUID classroomId, int days, boolean approval) throws Exception {
        var issued = post(teacher, "/api/classrooms/" + classroomId + "/join-link",
                "{\"validDays\":" + days + ",\"requiresApproval\":" + approval + "}");
        assertThat(issued.statusCode()).as(issued.body()).isEqualTo(201);
        assertThat(issued.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
        String token = field(issued.body(), "token");
        assertThat(token).matches("[A-Z2-7]{32}");
        assertThat(field(issued.body(), "path")).isEqualTo("/learn?account=1#ingresso=" + token);
        return token;
    }

    HttpResponse<String> accept(Browser student, String token) throws Exception {
        return post(student, "/api/student-account/classroom-links/accept", "{\"token\":\"" + token + "\"}");
    }

    static String field(String json, String name) {
        var match = Pattern.compile("\"" + Pattern.quote(name) + "\":\"?([^\",}]*)\"?").matcher(json);
        return match.find() ? match.group(1) : null;
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }
}
