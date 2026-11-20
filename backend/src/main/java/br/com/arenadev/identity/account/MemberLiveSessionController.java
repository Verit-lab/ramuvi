package br.com.arenadev.identity.account;

import br.com.arenadev.session.SessionJoinService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Member entry into the live class without a code (ADR-0076 D4). ROLE_STUDENT + CSRF from
 * SecurityConfig; identity only from Spring's OIDC principal. Guests keep /join (D5).
 */
@RestController
@RequestMapping("/api/student-account/live-sessions")
@ConditionalOnProperty(prefix = "app",
        name = {"live-member-join.enabled", "student-identity.oidc.enabled",
                "student-identity.enrollment-claim-enabled"},
        havingValue = "true")
public class MemberLiveSessionController {
    private final MemberLiveSessionService live;

    public MemberLiveSessionController(MemberLiveSessionService live) {
        this.live = live;
    }

    @GetMapping
    public ResponseEntity<List<MemberLiveSessionService.LiveClass>> list(@AuthenticationPrincipal OidcUser principal) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(live.list(identity.issuer(), identity.subject()));
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<SessionJoinService.JoinAccessView> join(@PathVariable UUID sessionId,
                                                                  @AuthenticationPrincipal OidcUser principal) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(live.join(identity.issuer(), identity.subject(), sessionId));
    }
}
