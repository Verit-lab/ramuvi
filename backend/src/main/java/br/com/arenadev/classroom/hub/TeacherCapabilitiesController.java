package br.com.arenadev.classroom.hub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v0.12.2 teacher rollout switches (ROLE_TEACHER). Same conditions as the controllers'
 * {@code @ConditionalOnProperty}: the UI never offers an action the backend does not expose.
 */
@RestController
public class TeacherCapabilitiesController {
    private final boolean classroomHub;
    private final boolean librarySharing;

    public TeacherCapabilitiesController(
            @Value("${app.classroom-hub.enabled:false}") boolean classroomHub,
            @Value("${app.library.sharing.enabled:false}") boolean librarySharing) {
        this.classroomHub = classroomHub;
        this.librarySharing = librarySharing;
    }

    @GetMapping("/api/teacher/capabilities")
    public ResponseEntity<Capabilities> capabilities() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Capabilities(classroomHub, librarySharing));
    }

    public record Capabilities(boolean classroomHub, boolean librarySharing) { }
}
