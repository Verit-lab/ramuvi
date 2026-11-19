package br.com.arenadev.identity.account;

import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.auth.LoginRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Protected by ROLE_STUDENT + CSRF in SecurityConfig. Feature OFF by default.
 * No accountId, studentId, email or device claim can be submitted by the client.
 * The verified provider identity is derived solely from Spring's SecurityContext.
 */
@RestController
@RequestMapping("/api/student-account")
@ConditionalOnProperty(prefix = "app.student-identity", name = "enrollment-claim-enabled",
        havingValue = "true")
public class StudentEnrollmentClaimController {
    private final StudentAccountService accounts;
    private final LoginRateLimitService rateLimit;

    public StudentEnrollmentClaimController(StudentAccountService accounts,
            @Qualifier("joinRateLimitService") LoginRateLimitService rateLimit) {
        this.accounts = accounts;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/enrollments/claim")
    public ResponseEntity<StudentAccountService.ClaimedEnrollment> claim(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody ClaimRequest request,
            HttpServletRequest servletRequest
    ) {
        Identity identity = requireOidc(principal);
        // In addition to 256-bit invite entropy: contain accidental/brute-force retries.
        // Remote address, not caller-provided X-Forwarded-For, is the trusted client IP.
        String key = "student-i2|" + identity.issuer() + "|" + identity.subject()
                + "|" + servletRequest.getRemoteAddr();
        LoginRateLimitService.Decision decision = rateLimit.beforeAttempt(key);
        if (!decision.allowed()) {
            throw new LoginRateLimitExceededException(
                    "Muitas tentativas de convite. Tente novamente mais tarde.",
                    decision.retryAfterSeconds());
        }
        try {
            var result = accounts.claimEnrollmentInviteForVerifiedOidc(
                    request.token(), identity.issuer(), identity.subject());
            rateLimit.recordSuccess(key);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (IllegalArgumentException rejected) {
            rateLimit.recordFailure(key);
            throw rejected;
        }
    }

    @PostMapping("/classrooms/join")
    public ResponseEntity<StudentAccountService.ClaimedEnrollment> joinClassroom(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody JoinClassroomRequest request,
            HttpServletRequest servletRequest
    ) {
        Identity identity = requireOidc(principal);
        String key = "student-classroom-join|" + identity.issuer() + "|" + identity.subject()
                + "|" + servletRequest.getRemoteAddr();
        LoginRateLimitService.Decision decision = rateLimit.beforeAttempt(key);
        if (!decision.allowed()) {
            throw new LoginRateLimitExceededException(
                    "Muitas tentativas de ingresso. Aguarde antes de tentar novamente.",
                    decision.retryAfterSeconds());
        }
        String email = Boolean.TRUE.equals(principal.getEmailVerified())
                ? principal.getEmail() : null;
        try {
            var joined = accounts.joinClassroomByVerifiedOidc(
                    identity.issuer(), identity.subject(), request.code(),
                    email, principal.getFullName());
            rateLimit.recordSuccess(key);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(joined);
        } catch (IllegalArgumentException rejected) {
            rateLimit.recordFailure(key);
            throw rejected;
        }
    }

    public record JoinClassroomRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,40}",
                    message = "Código de turma inválido.") String code
    ) { }

    @GetMapping("/classrooms")
    public ResponseEntity<List<StudentAccountService.ActiveClassroom>> classrooms(
            @AuthenticationPrincipal OidcUser principal
    ) {
        Identity identity = requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.listActiveClassroomsForVerifiedOidc(
                        identity.issuer(), identity.subject()));
    }

    static Identity requireOidc(OidcUser principal) {
        if (principal == null || principal.getIdToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String issuer = principal.getIdToken().getClaimAsString("iss");
        String subject = principal.getIdToken().getClaimAsString("sub");
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new Identity(issuer, subject);
    }

    public record ClaimRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Convite inválido.")
            String token
    ) { }
    public record Identity(String issuer, String subject) { }
}
