package br.com.arenadev.classroom.hub;

import br.com.arenadev.session.SessionService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Teacher classroom hub (ADR-0076 D2/D3). ROLE_TEACHER + CSRF via SecurityConfig (/api/**).
 * Start and switch reuse SessionService: same lock, same typed 409, same Arena runtime.
 */
@RestController
@RequestMapping("/api/classrooms/{classroomId}")
@ConditionalOnProperty(prefix = "app.classroom-hub", name = "enabled", havingValue = "true")
public class ClassroomHubController {
    private final ClassroomHubService hub;
    private final SessionService sessions;

    public ClassroomHubController(ClassroomHubService hub, SessionService sessions) {
        this.hub = hub;
        this.sessions = sessions;
    }

    @GetMapping("/hub")
    public ResponseEntity<ClassroomHubService.HubView> hub(@PathVariable UUID classroomId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(hub.hub(classroomId));
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionService.SessionView> start(@PathVariable UUID classroomId,
                                                            @Valid @RequestBody(required = false) StartRequest request) {
        String title = request == null ? null : request.title();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(sessions.start(classroomId, title, Set.of()));
    }

    @PostMapping("/sessions/switch")
    public ResponseEntity<SessionService.SwitchView> switchActive(@PathVariable UUID classroomId,
                                                                  @Valid @RequestBody SwitchRequest request) {
        if (!Boolean.TRUE.equals(request.confirm())) {
            throw new IllegalArgumentException("Confirme o encerramento da aula atual antes de iniciar outra.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(sessions.switchActive(classroomId, request.expectedActiveSessionId(), request.title()));
    }

    public record StartRequest(@Size(max = 160) String title) { }

    public record SwitchRequest(@NotNull UUID expectedActiveSessionId, @Size(max = 160) String title,
                                Boolean confirm) { }
}
