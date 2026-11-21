package br.com.arenadev.identity.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v0.12.2 student rollout switches (ROLE_STUDENT via /api/student-account/**). Same conditions
 * as MemberLiveSessionController and StudentLibraryController, so the UI never offers a route
 * the backend does not expose.
 */
@RestController
public class StudentCapabilitiesController {
    private final boolean liveMemberJoin;
    private final boolean personalLibrary;

    public StudentCapabilitiesController(
            @Value("${app.student-identity.oidc.enabled:false}") boolean oidc,
            @Value("${app.student-identity.enrollment-claim-enabled:false}") boolean claim,
            @Value("${app.live-member-join.enabled:false}") boolean memberJoin,
            @Value("${app.library.sharing.enabled:false}") boolean sharing,
            @Value("${app.library.personal.enabled:false}") boolean personal) {
        this.liveMemberJoin = oidc && claim && memberJoin;
        this.personalLibrary = oidc && sharing && personal;
    }

    @GetMapping("/api/student-account/capabilities")
    public ResponseEntity<Capabilities> capabilities() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Capabilities(liveMemberJoin, personalLibrary));
    }

    public record Capabilities(boolean liveMemberJoin, boolean personalLibrary) { }
}
