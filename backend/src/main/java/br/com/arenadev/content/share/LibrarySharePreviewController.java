package br.com.arenadev.content.share;

import br.com.arenadev.auth.LoginRateLimitExceededException;
import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.identity.account.StudentAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public preview (ADR-0076 D7). The token arrives in the POST body (it lived in the URL
 * fragment, never in a path, query, log or Referer). Rate limited per client IP; responses are
 * no-store and noindex. CSRF-exempt because it creates no state besides aggregate counters and
 * must work without a session; any student account is derived from Spring's OIDC principal.
 */
@RestController
@ConditionalOnProperty(prefix = "app.library.sharing", name = "enabled", havingValue = "true")
public class LibrarySharePreviewController {
    static final String NOINDEX = "noindex, nofollow, noarchive";

    private final LibraryShareService shares;
    private final StudentAccountService accounts;
    private final LoginRateLimitService rateLimit;

    public LibrarySharePreviewController(LibraryShareService shares, StudentAccountService accounts,
                                         @Qualifier("joinRateLimitService") LoginRateLimitService rateLimit) {
        this.shares = shares;
        this.accounts = accounts;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/api/library/shares/preview")
    public ResponseEntity<LibraryShareService.SharedContent> preview(@Valid @RequestBody TokenRequest request,
                                                                     @AuthenticationPrincipal OidcUser principal,
                                                                     HttpServletRequest servletRequest) {
        UUID accountId = verifiedAccountId(accounts, principal);
        var content = throttled(rateLimit, servletRequest, () -> shares.preview(request.token(), accountId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Robots-Tag", NOINDEX).body(content);
    }

    /** Account of the CURRENT verified OIDC identity, re-checked in the DB; null when absent. */
    static UUID verifiedAccountId(StudentAccountService accounts, OidcUser principal) {
        if (principal == null || principal.getIdToken() == null) return null;
        return accounts.findActiveVerifiedOidcAccount(principal.getIdToken().getClaimAsString("iss"),
                        principal.getIdToken().getClaimAsString("sub"))
                .map(StudentAccountService.AccountView::accountId).orElse(null);
    }

    /** Invalid/expired/revoked tokens count per IP; success never resets (ADR-0075 §3). */
    static <T> T throttled(LoginRateLimitService rateLimit, HttpServletRequest request,
                           java.util.function.Supplier<T> action) {
        String key = "library-share-ip|" + (request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
        var decision = rateLimit.beforeAttempt(key);
        if (!decision.allowed()) {
            throw new LoginRateLimitExceededException(
                    "Muitas tentativas com links de material. Aguarde antes de tentar novamente.",
                    decision.retryAfterSeconds());
        }
        try {
            return action.get();
        } catch (IllegalArgumentException invalid) {
            rateLimit.recordFailure(key);
            throw invalid;
        } catch (LibraryAccessException denied) {
            if (denied.status() == HttpStatus.NOT_FOUND) rateLimit.recordFailure(key);
            throw denied;
        }
    }

    public record TokenRequest(@NotBlank @Size(max = 64) String token) { }
}
