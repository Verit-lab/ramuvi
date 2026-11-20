package br.com.arenadev.content.share;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Teacher side of Library sharing. ROLE_TEACHER + CSRF via SecurityConfig (/api/**). */
@RestController
@ConditionalOnProperty(prefix = "app.library.sharing", name = "enabled", havingValue = "true")
public class LibraryShareController {
    private final LibraryShareService shares;

    public LibraryShareController(LibraryShareService shares) {
        this.shares = shares;
    }

    @PostMapping("/api/library/resources/{resourceId}/shares")
    public ResponseEntity<LibraryShareService.IssuedShare> create(@PathVariable UUID resourceId,
                                                                  @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(shares.create(resourceId, request.permission(), request.validDays(), request.label()));
    }

    @GetMapping("/api/library/resources/{resourceId}/shares")
    public ResponseEntity<List<LibraryShareService.ShareView>> list(@PathVariable UUID resourceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(shares.list(resourceId));
    }

    @PostMapping("/api/library/shares/{shareId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID shareId) {
        shares.revoke(shareId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record CreateRequest(@NotNull LibraryShareService.Permission permission,
                                @Min(LibraryShareService.MIN_DAYS) @Max(LibraryShareService.MAX_DAYS) int validDays,
                                @Size(max = 120) String label) { }
}
