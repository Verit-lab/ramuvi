package br.com.arenadev.identity.account;

import br.com.arenadev.support.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Share-token guessing is throttled per client IP; a later valid-looking token still gets 429. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.library.sharing.enabled=true",
        "app.security.join-rate-limit.max-failures=3",
        "app.security.join-rate-limit.window-seconds=60",
        "app.security.join-rate-limit.block-seconds=60",
        "app.teacher.username=" + OidcHttpSupport.TEACHER_USER,
        "app.teacher.password=" + OidcHttpSupport.TEACHER_PASSWORD
})
@Import(OidcHttpSupport.FakeProviderRegistrations.class)
class LibraryShareRateLimitHttpIT extends OidcHttpSupport {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry, LibraryShareRateLimitHttpIT.class);
    }

    @LocalServerPort private int port;

    @Override int port() { return port; }

    @Test
    void guessingTokensIsBlockedPerClient() throws Exception {
        var anonymous = new Browser(client(), null);
        for (String guess : new String[]{"A".repeat(32), "B".repeat(32), "C".repeat(32)}) {
            var response = send(anonymous.client(), "POST", "/api/library/shares/preview",
                    "{\"token\":\"" + guess + "\"}", null);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        }
        var blocked = send(anonymous.client(), "POST", "/api/library/shares/preview",
                "{\"token\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567\"}", null);
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
    }
}
