package br.com.arenadev.identity.oidc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * I1 OIDC sign-in, separate from delegated Classroom/Teams integrations.
 * Fail closed on missing providers, insecure public origins and shared tenant aliases.
 * Never expose the configured secrets or authorize linking by email.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.student-identity.oidc", name = "enabled", havingValue = "true")
public class StudentOidcConfiguration {
    static final String GOOGLE_ISSUER = "https://accounts.google.com";
    static final String GOOGLE_AUTHORIZATION = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
    static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    ClientRegistrationRepository studentClientRegistrations(
            @Value("${app.student-identity.oidc.public-base-url}") String publicBaseUrl,
            @Value("${app.student-identity.oidc.google-client-id:}") String googleId,
            @Value("${app.student-identity.oidc.google-client-secret:}") String googleSecret,
            @Value("${app.student-identity.oidc.microsoft-client-id:}") String microsoftId,
            @Value("${app.student-identity.oidc.microsoft-client-secret:}") String microsoftSecret,
            @Value("${app.student-identity.oidc.microsoft-tenant-id:}") String microsoftTenant,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie
    ) {
        String baseUrl = requirePublicOrigin(publicBaseUrl, secureCookie);
        List<ClientRegistration> registrations = new ArrayList<>();

        if (!googleId.isBlank() || !googleSecret.isBlank()) {
            requirePair(googleId, googleSecret, "Google");
            registrations.add(registration("google", GOOGLE_ISSUER, googleId, googleSecret,
                    GOOGLE_AUTHORIZATION, GOOGLE_TOKEN, GOOGLE_JWKS, baseUrl));
        }
        if (!microsoftId.isBlank() || !microsoftSecret.isBlank() || !microsoftTenant.isBlank()) {
            requirePair(microsoftId, microsoftSecret, "Microsoft");
            String tenant;
            try {
                tenant = UUID.fromString(microsoftTenant).toString();
                if (!tenant.equalsIgnoreCase(microsoftTenant)) {
                    throw new IllegalArgumentException("O tenant Microsoft deve ser um UUID canônico.");
                }
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException(
                        "Configure APP_STUDENT_OIDC_MICROSOFT_TENANT_ID com o UUID de um tenant específico.", invalid);
            }
            String host = "https://login.microsoftonline.com/" + tenant;
            registrations.add(registration("microsoft", host + "/v2.0", microsoftId, microsoftSecret,
                    host + "/oauth2/v2.0/authorize",
                    host + "/oauth2/v2.0/token",
                    host + "/discovery/v2.0/keys", baseUrl));
        }
        if (registrations.isEmpty()) {
            throw new IllegalStateException("OIDC de alunos habilitado sem provedor configurado.");
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    static ClientRegistration registration(String provider, String issuer, String id, String secret,
                                           String authorization, String token, String jwks,
                                           String publicBaseUrl) {
        return ClientRegistration.withRegistrationId(provider)
                .clientId(id)
                .clientSecret(secret)
                .clientAuthenticationMethod(provider.equals("google")
                        ? ClientAuthenticationMethod.CLIENT_SECRET_POST
                        : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(publicBaseUrl + "/api/student-oidc/callback/" + provider)
                .scope("openid", "profile", "email")
                .authorizationUri(authorization)
                .tokenUri(token)
                .jwkSetUri(jwks)
                .issuerUri(issuer)
                .userNameAttributeName("sub")
                .clientName(provider.equals("google") ? "Google" : "Microsoft")
                .build();
    }

    static String requirePublicOrigin(String configured, boolean secureCookie) {
        try {
            URI url = URI.create(configured);
            String host = url.getHost();
            boolean loopback = host != null &&
                    (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
            boolean https = "https".equalsIgnoreCase(url.getScheme());
            boolean hasNonRootPath = url.getRawPath() != null
                    && !url.getRawPath().isEmpty() && !url.getRawPath().equals("/");
            if (host == null || url.getRawUserInfo() != null || url.getRawQuery() != null
                    || url.getRawFragment() != null || hasNonRootPath
                    || url.getPort() == 0 || (!https && !(loopback && "http".equalsIgnoreCase(url.getScheme())))) {
                throw new IllegalArgumentException("Origem OIDC inválida.");
            }
            if (https && !secureCookie) {
                throw new IllegalArgumentException("HTTPS OIDC requer APP_SESSION_COOKIE_SECURE=true.");
            }
            return url.getScheme().toLowerCase(Locale.ROOT) + "://" + url.getRawAuthority();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "APP_STUDENT_OIDC_PUBLIC_BASE_URL requer origem HTTPS ou loopback HTTP; sem path/query; HTTPS requer APP_SESSION_COOKIE_SECURE=true.", invalid);
        }
    }

    private static void requirePair(String id, String secret, String name) {
        if (id.isBlank() || secret.isBlank()) {
            throw new IllegalStateException("OIDC " + name + " requer client ID e secret.");
        }
    }
}
