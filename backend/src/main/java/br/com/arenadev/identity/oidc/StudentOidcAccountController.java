package br.com.arenadev.identity.oidc;

import br.com.arenadev.identity.account.StudentAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Session endpoint does not accept account IDs, email or student identifiers.
 * It checks the active external identity against DB on EACH request.
 * Student API stays separate from /api/auth (teacher) and /api/join (guest).
 */
@RestController
@RequestMapping("/api/student-account")
@ConditionalOnProperty(prefix = "app.student-identity.oidc", name = "enabled", havingValue = "true")
public class StudentOidcAccountController {
    private final StudentAccountService accounts;
    private final ClientRegistrationRepository clients;

    public StudentOidcAccountController(StudentAccountService accounts,
                                        ClientRegistrationRepository clients) {
        this.accounts = accounts;
        this.clients = clients;
    }

    @GetMapping("/providers")
    public ResponseEntity<List<ProviderView>> providers() {
        List<ProviderView> available = new ArrayList<>();
        for (String provider : List.of("google", "microsoft")) {
            var registration = clients.findByRegistrationId(provider);
            if (registration != null) {
                available.add(new ProviderView(provider, registration.getClientName(),
                        "/api/student-oidc/authorization/" + provider));
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(available);
    }

    @GetMapping("/session")
    public ResponseEntity<StudentSessionView> session(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String issuer = principal.getIdToken().getClaimAsString("iss");
        String subject = principal.getIdToken().getClaimAsString("sub");
        var account = accounts.findActiveVerifiedOidcAccount(issuer, subject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new StudentSessionView(account.accountId(), account.studentId(), "STUDENT"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record ProviderView(String id, String name, String path) { }
    public record StudentSessionView(java.util.UUID accountId, java.util.UUID studentId, String role) { }
}
