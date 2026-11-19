package br.com.arenadev.identity.account;

import br.com.arenadev.auth.LoginRateLimitExceededException;
import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.identity.oidc.StudentOidcConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

/**
 * Student side of ADR-0074. ROLE_STUDENT + CSRF from SecurityConfig.
 * The client sends ONLY the link token (POST body, never a URL). Issuer, subject,
 * e-mail and email_verified are read from the Spring OIDC principal; an e-mail is
 * forwarded only for the Google issuer with email_verified=true (Entra e-mail is
 * mutable and is never used for matching).
 */
@RestController
@RequestMapping("/api/student-account/classroom-links")
@ConditionalOnProperty(prefix = "app.student-identity",
        name = {"oidc.enabled", "enrollment-claim-enabled", "classroom-join-links-enabled"},
        havingValue = "true")
public class StudentClassroomLinkController {
    private final ClassroomJoinLinkService links;
    private final LoginRateLimitService rateLimit;
    private final boolean studySessionEnabled;

    public StudentClassroomLinkController(
            ClassroomJoinLinkService links,
            @Qualifier("joinRateLimitService") LoginRateLimitService rateLimit,
            @Value("${app.student-identity.study-session-enabled:false}") boolean studySessionEnabled) {
        this.links = links;
        this.rateLimit = rateLimit;
        this.studySessionEnabled = studySessionEnabled;
    }

    @PostMapping("/preview")
    public ResponseEntity<ClassroomJoinLinkService.LinkPreview> preview(
            @AuthenticationPrincipal OidcUser principal, @Valid @RequestBody LinkRequest request,
            HttpServletRequest servletRequest) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(throttled(identity, servletRequest,
                () -> links.preview(identity.issuer(), identity.subject(), request.token())));
    }

    @PostMapping("/accept")
    public ResponseEntity<AcceptView> accept(
            @AuthenticationPrincipal OidcUser principal, @Valid @RequestBody LinkRequest request,
            HttpServletRequest servletRequest) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        String googleVerifiedEmail = StudentOidcConfiguration.GOOGLE_ISSUER.equals(identity.issuer())
                && Boolean.TRUE.equals(principal.getEmailVerified()) ? principal.getEmail() : null;
        var outcome = throttled(identity, servletRequest, () -> {
            try {
                return links.accept(identity.issuer(), identity.subject(), request.token(),
                        googleVerifiedEmail, principal.getFullName());
            } catch (DataIntegrityViolationException race) {
                // Concurrent accept for the same student/e-mail: unique constraints won; no 500 (F-12).
                throw new IllegalStateException("Outro ingresso foi concluído ao mesmo tempo. Tente novamente.");
            }
        });
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AcceptView(
                outcome.status(), outcome.classroomName(),
                studySessionEnabled && outcome.classroomStudyAccessEnabled()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<ClassroomJoinLinkService.OwnRequest>> requests(
            @AuthenticationPrincipal OidcUser principal) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(links.ownRequests(identity.issuer(), identity.subject()));
    }

    private <T> T throttled(StudentEnrollmentClaimController.Identity identity,
                            HttpServletRequest request, Supplier<T> action) {
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String accountKey = "classroom-link|" + identity.issuer() + "|" + identity.subject() + "|" + client;
        String clientKey = "classroom-link-ip|" + client;
        for (String key : List.of(accountKey, clientKey)) {
            var decision = rateLimit.beforeAttempt(key);
            if (!decision.allowed()) {
                throw new LoginRateLimitExceededException(
                        "Muitas tentativas com links de ingresso. Aguarde antes de tentar novamente.",
                        decision.retryAfterSeconds());
            }
        }
        try {
            return action.get();
        } catch (IllegalArgumentException invalid) {
            // Only invalid/expired/revoked tokens count; no reset on success.
            rateLimit.recordFailure(accountKey);
            rateLimit.recordFailure(clientKey);
            throw invalid;
        }
    }

    public record LinkRequest(@NotBlank @Size(max = 64) String token) { }
    /** Never exposes studentId/enrollmentId: the UI reloads its session for that. */
    public record AcceptView(ClassroomJoinLinkService.JoinStatus status, String classroomName,
                             boolean journeysAvailable) { }
}
