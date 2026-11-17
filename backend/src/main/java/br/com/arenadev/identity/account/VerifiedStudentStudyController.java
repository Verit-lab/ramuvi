package br.com.arenadev.identity.account;

import br.com.arenadev.study.JourneyStudyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ROLE_STUDENT enforced by SecurityConfig. POST operations require CSRF.
 * Fail closed unless OIDC, enrollment claim and I3 are all enabled.
 */
@RestController
@RequestMapping("/api/student-account/study/journeys")
@ConditionalOnProperty(prefix = "app.student-identity",
        name = {"oidc.enabled", "enrollment-claim-enabled", "study-session-enabled"}, havingValue = "true")
public class VerifiedStudentStudyController {
    private final VerifiedStudentStudyService study;

    public VerifiedStudentStudyController(VerifiedStudentStudyService study) {
        this.study = study;
    }

    @GetMapping
    public ResponseEntity<List<JourneyStudyService.JourneyStudySummary>> list(
            @AuthenticationPrincipal OidcUser principal) {
        var id = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.list(id.issuer(), id.subject()));
    }

    @GetMapping("/{journeyId}")
    public ResponseEntity<JourneyStudyService.JourneyStudyView> get(
            @PathVariable UUID journeyId, @AuthenticationPrincipal OidcUser principal) {
        var id = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.get(journeyId, id.issuer(), id.subject()));
    }

    @GetMapping("/{journeyId}/contents/{contentId}")
    public ResponseEntity<JourneyStudyService.JourneyContentDetailView> content(
            @PathVariable UUID journeyId, @PathVariable UUID contentId,
            @AuthenticationPrincipal OidcUser principal) {
        var id = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.content(journeyId, contentId, id.issuer(), id.subject()));
    }

    @PostMapping("/{journeyId}/contents/{contentId}/start")
    public ResponseEntity<JourneyStudyService.JourneyStudyView> start(
            @PathVariable UUID journeyId, @PathVariable UUID contentId,
            @AuthenticationPrincipal OidcUser principal) {
        var id = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.start(journeyId, contentId, id.issuer(), id.subject()));
    }

    @PostMapping("/{journeyId}/contents/{contentId}/complete")
    public ResponseEntity<JourneyStudyService.JourneyStudyView> complete(
            @PathVariable UUID journeyId, @PathVariable UUID contentId,
            @AuthenticationPrincipal OidcUser principal) {
        var id = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.complete(journeyId, contentId, id.issuer(), id.subject()));
    }
}
