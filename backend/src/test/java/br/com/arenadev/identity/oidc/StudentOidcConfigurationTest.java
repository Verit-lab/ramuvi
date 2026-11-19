package br.com.arenadev.identity.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentOidcConfigurationTest {
    @Test
    void productionSessionCookieSupportsProviderCallbackWithoutDroppingState() throws IOException {
        var sources = new YamlPropertySourceLoader().load(
                "production", new ClassPathResource("application-prod.yml"));
        assertThat(sources).isNotEmpty();
        var production = sources.get(0);
        assertThat(production.getProperty("server.servlet.session.cookie.same-site"))
                .isEqualTo("lax");
        assertThat(production.getProperty("server.servlet.session.cookie.secure"))
                .isEqualTo(true);
        assertThat(production.getProperty("server.servlet.session.cookie.http-only"))
                .isEqualTo(true);
    }

    @Test
    void onlyHttpsOrLocalHttpOriginsCanBeUsedForCallbacks() {
        assertThat(StudentOidcConfiguration.requirePublicOrigin("https://ramuvi.example.org/", true))
                .isEqualTo("https://ramuvi.example.org");
        assertThat(StudentOidcConfiguration.requirePublicOrigin("http://localhost:3000", false))
                .isEqualTo("http://localhost:3000");
        for (String origin : new String[]{
                "http://ramuvi.example.org",
                "https://ramuvi.example.org/not-a-base",
                "https://ramuvi.example.org?next=https://evil.test",
                "https://someone@ramuvi.example.org",
                "http://evil.test:3000",
                "file:///etc/passwd",
                "https://ramuvi.example.org/#fragment"
        }) {
            assertThatThrownBy(() -> StudentOidcConfiguration.requirePublicOrigin(origin, true))
                    .as("invalid callback origin: %s", origin)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> StudentOidcConfiguration.requirePublicOrigin(
                "https://ramuvi.example.org", false)).hasMessageContaining("SECURE");
    }

    @Test
    void microsoftRequiresTenantSpecificIssuerAndBothClientCredentials() {
        var configuration = new StudentOidcConfiguration();
        assertThatThrownBy(() -> configuration.studentClientRegistrations(
                "http://localhost:3000", "", "", "id", "secret", "common", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TENANT_ID");
        assertThatThrownBy(() -> configuration.studentClientRegistrations(
                "http://localhost:3000", "id", "", "", "", "", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Google");
        var repository = configuration.studentClientRegistrations(
                "http://localhost:3000", "", "", "id", "secret",
                "550e8400-e29b-41d4-a716-446655440000", false);
        assertThat(repository.findByRegistrationId("microsoft").getProviderDetails().getIssuerUri())
                .isEqualTo("https://login.microsoftonline.com/550e8400-e29b-41d4-a716-446655440000/v2.0");
    }

    @Test
    void registrationsAreExactIssuerBoundAndNotSharedWithClassroomIntegration() {
        var google = StudentOidcConfiguration.registration(
                "google", StudentOidcConfiguration.GOOGLE_ISSUER, "id", "secret",
                StudentOidcConfiguration.GOOGLE_AUTHORIZATION,
                StudentOidcConfiguration.GOOGLE_TOKEN,
                StudentOidcConfiguration.GOOGLE_JWKS, "https://ramuvi.example.org");
        assertThat(google.getProviderDetails().getIssuerUri())
                .isEqualTo("https://accounts.google.com");
        assertThat(google.getRedirectUri())
                .isEqualTo("https://ramuvi.example.org/api/student-oidc/callback/google");
        assertThat(google.getScopes()).contains("openid", "email");
        assertThat(google.getClientId()).isEqualTo("id");
        assertThat(google.getClientAuthenticationMethod().getValue()).isEqualTo("client_secret_post");
    }
}
