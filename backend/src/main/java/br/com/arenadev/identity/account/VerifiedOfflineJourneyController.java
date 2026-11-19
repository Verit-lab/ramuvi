package br.com.arenadev.identity.account;

import br.com.arenadev.study.JourneyStudyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Online-only opt-in export. Never accessible by bearer device claims or cached by SW. */
@RestController
@RequestMapping("/api/student-account/study/offline")
@ConditionalOnProperty(prefix = "app.student-identity",
        name = {"oidc.enabled", "enrollment-claim-enabled", "study-session-enabled",
                "offline-download-enabled"}, havingValue = "true")
public class VerifiedOfflineJourneyController {
    private final VerifiedStudentStudyService study;

    public VerifiedOfflineJourneyController(VerifiedStudentStudyService study) {
        this.study = study;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities(@AuthenticationPrincipal OidcUser principal) {
        StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("enabled", true, "maxContents", 80, "maxCharacters", 2_000_000,
                        "expiryDays", 7));
    }

    @GetMapping("/journeys/{journeyId}")
    public ResponseEntity<JourneyStudyService.OfflineJourneySnapshot> journey(
            @PathVariable UUID journeyId, @AuthenticationPrincipal OidcUser principal) {
        var identity = StudentEnrollmentClaimController.requireOidc(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(study.offline(journeyId, identity.issuer(), identity.subject()));
    }
}
