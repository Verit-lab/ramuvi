package br.com.arenadev.content.share;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Minha Biblioteca (ADR-0076 D8). ROLE_STUDENT + CSRF via SecurityConfig; the account comes only
 * from the verified OIDC principal re-checked in the DB on every request. No Student/Enrollment
 * is required and none is created.
 */
@RestController
@RequestMapping("/api/student-account/library/items")
@ConditionalOnProperty(prefix = "app",
        name = {"library.personal.enabled", "library.sharing.enabled", "student-identity.oidc.enabled"},
        havingValue = "true")
public class StudentLibraryController {
    private final StudentLibraryService library;
    private final LibraryShareService shares;
    private final StudentAccountService accounts;
    private final LoginRateLimitService rateLimit;

    public StudentLibraryController(StudentLibraryService library, LibraryShareService shares,
                                    StudentAccountService accounts,
                                    @Qualifier("joinRateLimitService") LoginRateLimitService rateLimit) {
        this.library = library;
        this.shares = shares;
        this.accounts = accounts;
        this.rateLimit = rateLimit;
    }

    @PostMapping
    public ResponseEntity<LibraryShareService.SaveResult> save(@AuthenticationPrincipal OidcUser principal,
                                                               @Valid @RequestBody SaveRequest request,
                                                               HttpServletRequest servletRequest) {
        UUID accountId = requireAccount(principal);
        var result = LibrarySharePreviewController.throttled(rateLimit, servletRequest,
                () -> shares.save(request.token(), accountId, Boolean.TRUE.equals(request.replaceVersion())));
        HttpStatus status = "SAVED".equals(result.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping
    public ResponseEntity<List<StudentLibraryService.ItemSummary>> list(@AuthenticationPrincipal OidcUser principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(library.list(requireAccount(principal)));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<StudentLibraryService.ItemContent> get(@AuthenticationPrincipal OidcUser principal,
                                                                 @PathVariable UUID itemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", LibrarySharePreviewController.NOINDEX)
                .body(library.get(requireAccount(principal), itemId));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal OidcUser principal, @PathVariable UUID itemId) {
        library.remove(requireAccount(principal), itemId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID requireAccount(OidcUser principal) {
        UUID accountId = LibrarySharePreviewController.verifiedAccountId(accounts, principal);
        if (accountId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return accountId;
    }

    public record SaveRequest(@NotBlank @Size(max = 64) String token, Boolean replaceVersion) { }
}
