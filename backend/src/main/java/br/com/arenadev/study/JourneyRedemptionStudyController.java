package br.com.arenadev.study;

import br.com.arenadev.auth.LoginRateLimitExceededException;
import br.com.arenadev.auth.LoginRateLimitService;
import br.com.arenadev.journey.JourneyRedemptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Public Journey-code routes. The code is a 6-character shared hint, so every route
 * is throttled per client IP and none of them can mint a study credential (ADR-0075).
 */
@RestController
@RequestMapping("/api/learn/journeys")
public class JourneyRedemptionStudyController {
    static final String BOOTSTRAP_GONE_MESSAGE =
            "O acesso a Jornadas por código e nome/matrícula foi desativado por segurança. "
                    + "Entre em /learn com sua conta e use o link de ingresso da turma ou o convite individual do professor.";

    private final JourneyRedemptionService redemption;
    private final StudyPrincipalResolver principals;
    private final LoginRateLimitService rateLimit;

    public JourneyRedemptionStudyController(
            JourneyRedemptionService redemption,
            StudyPrincipalResolver principals,
            @Qualifier("joinRateLimitService") LoginRateLimitService rateLimit
    ) {
        this.redemption = redemption;
        this.principals = principals;
        this.rateLimit = rateLimit;
    }

    @GetMapping("/redeem/{code}")
    public JourneyRedemptionService.RedemptionPreviewView preview(
            @PathVariable String code, HttpServletRequest request) {
        return throttled("journey-preview|" + client(request), () -> redemption.preview(code));
    }

    /**
     * Removed in v0.12.1 P0. Kept as an explicit 410 (instead of a 404) so legacy
     * clients show actionable guidance. It never reads the body, never looks up a
     * student and never issues a device claim, whatever the input.
     */
    @PostMapping("/redeem/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrap() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.GONE.value());
        body.put("error", HttpStatus.GONE.getReasonPhrase());
        body.put("message", BOOTSTRAP_GONE_MESSAGE);
        body.put("next", "/learn?account=1");
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).body(body);
    }

    @PostMapping("/redeem")
    public JourneyRedemptionService.RedemptionView redeem(
            @RequestHeader("X-Study-Token") String token,
            @RequestBody RedeemRequest request,
            HttpServletRequest servletRequest
    ) {
        return throttled("journey-redeem|" + client(servletRequest),
                () -> redemption.redeem(request.code(), principals.resolve(token)));
    }

    private <T> T throttled(String key, Supplier<T> action) {
        LoginRateLimitService.Decision decision = rateLimit.beforeAttempt(key);
        if (!decision.allowed()) {
            throw new LoginRateLimitExceededException(
                    "Muitas tentativas com códigos de Jornada. Aguarde antes de tentar novamente.",
                    decision.retryAfterSeconds());
        }
        try {
            // No reset on success: a valid code interleaved with guesses must not
            // clear the failure window used against code enumeration.
            return action.get();
        } catch (ResponseStatusException | IllegalArgumentException rejected) {
            rateLimit.recordFailure(key);
            throw rejected;
        }
    }

    private static String client(HttpServletRequest request) {
        // Remote address after the trusted-proxy valve; never a caller-supplied header.
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    public record RedeemRequest(String code) {}
}
