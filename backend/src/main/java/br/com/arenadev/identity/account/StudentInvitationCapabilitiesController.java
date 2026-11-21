package br.com.arenadev.identity.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Teacher-only capability check; the API is not an invitation or authorization grant. */
@RestController
@RequestMapping("/api/student-account-invitations")
public class StudentInvitationCapabilitiesController {
    private final boolean enabled;
    private final boolean classroomJoinLinksEnabled;
    private final boolean autoEmailLinkEnabled;

    public StudentInvitationCapabilitiesController(
            @Value("${app.student-identity.invitation-issuance-enabled:false}") boolean teacherEnabled,
            @Value("${app.student-identity.enrollment-claim-enabled:false}") boolean claimEnabled,
            @Value("${app.student-identity.oidc.enabled:false}") boolean oidcEnabled,
            @Value("${app.student-identity.classroom-join-links-enabled:false}") boolean linksEnabled,
            @Value("${app.student-identity.auto-email-link-enabled:false}") boolean autoEmailLink
    ) {
        this.enabled = teacherEnabled && claimEnabled && oidcEnabled;
        // Same condition as ClassroomJoinLinkController: UI guard == backend availability.
        this.classroomJoinLinksEnabled = linksEnabled && claimEnabled && oidcEnabled;
        this.autoEmailLinkEnabled = this.classroomJoinLinksEnabled && autoEmailLink;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Capabilities> capabilities() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Capabilities(enabled, classroomJoinLinksEnabled, autoEmailLinkEnabled));
    }

    public record Capabilities(boolean enabled, boolean classroomJoinLinksEnabled,
                               boolean autoEmailLinkEnabled) { }
}
