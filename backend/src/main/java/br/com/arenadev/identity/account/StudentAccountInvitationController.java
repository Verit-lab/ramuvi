package br.com.arenadev.identity.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Protected by existing ROLE_TEACHER and CSRF on /api/**.
 * Does not expose claim() to anonymous clients.
 */
@RestController
@ConditionalOnProperty(prefix = "app.student-identity", name = "invitation-issuance-enabled", havingValue = "true")
@RequestMapping("/api/student-account-invitations")
public class StudentAccountInvitationController {
    private final StudentAccountService service;

    public StudentAccountInvitationController(StudentAccountService service) {
        this.service = service;
    }

    @PostMapping("/enrollments/{enrollmentId}")
    public ResponseEntity<StudentAccountService.IssuedInvite> issue(@PathVariable UUID enrollmentId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.issueEnrollmentInvite(enrollmentId));
    }

    @DeleteMapping("/{inviteId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID inviteId) {
        service.revokeEnrollmentInvite(inviteId);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore()).build();
    }
}
