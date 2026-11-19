package br.com.arenadev.identity.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Teacher side of ADR-0074. ROLE_TEACHER + CSRF come from SecurityConfig (/api/**).
 * OFF unless OIDC, enrollment claim and classroom join links are all enabled, so the
 * teacher can never hand out a link students are unable to use.
 * The token appears only in the POST response (no-store); it is never listed again.
 */
@RestController
@RequestMapping("/api/classrooms/{classroomId}")
@ConditionalOnProperty(prefix = "app.student-identity",
        name = {"oidc.enabled", "enrollment-claim-enabled", "classroom-join-links-enabled"},
        havingValue = "true")
public class ClassroomJoinLinkController {
    /** URL fragment: never sent to servers, proxies or Referer headers. */
    static final String LINK_PATH = "/learn?account=1#ingresso=";

    private final ClassroomJoinLinkService links;

    public ClassroomJoinLinkController(ClassroomJoinLinkService links) {
        this.links = links;
    }

    @GetMapping("/join-link")
    public ResponseEntity<ClassroomJoinLinkService.LinkStatus> status(@PathVariable UUID classroomId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.status(classroomId));
    }

    @PostMapping("/join-link")
    public ResponseEntity<IssuedLinkView> issue(@PathVariable UUID classroomId,
                                                @Valid @RequestBody IssueRequest request) {
        var issued = links.issue(classroomId, request.validDays(), Boolean.TRUE.equals(request.requiresApproval()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new IssuedLinkView(issued.linkId(), issued.token(), LINK_PATH + issued.token(),
                        issued.expiresAt(), issued.requiresApproval()));
    }

    @PatchMapping("/join-link")
    public ResponseEntity<ClassroomJoinLinkService.LinkStatus> approval(@PathVariable UUID classroomId,
                                                                        @Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(links.setRequiresApproval(classroomId, request.requiresApproval()));
    }

    @DeleteMapping("/join-link")
    public ResponseEntity<Void> revoke(@PathVariable UUID classroomId) {
        links.revoke(classroomId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/join-requests")
    public ResponseEntity<ClassroomJoinLinkService.ReviewQueue> requests(@PathVariable UUID classroomId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.reviewQueue(classroomId));
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public ResponseEntity<ClassroomJoinLinkService.Decision> approve(
            @PathVariable UUID classroomId, @PathVariable UUID requestId,
            @Valid @RequestBody DecisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(links.approve(classroomId, requestId, request.mode(), request.studentId()));
    }

    @PostMapping("/join-requests/{requestId}/reject")
    public ResponseEntity<ClassroomJoinLinkService.Decision> reject(
            @PathVariable UUID classroomId, @PathVariable UUID requestId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(links.reject(classroomId, requestId));
    }

    public record IssueRequest(
            @Min(value = 1, message = "Validade mínima de 1 dia.")
            @Max(value = 30, message = "Validade máxima de 30 dias.") int validDays,
            Boolean requiresApproval) { }
    public record ApprovalRequest(boolean requiresApproval) { }
    public record DecisionRequest(@NotNull ClassroomJoinLinkService.ApprovalMode mode, UUID studentId) { }
    public record IssuedLinkView(UUID linkId, String token, String path, Instant expiresAt,
                                 boolean requiresApproval) { }
}
