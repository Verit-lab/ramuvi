package br.com.arenadev.study;

import br.com.arenadev.classroom.Enrollment;
import br.com.arenadev.identity.DeviceClaimService;
import br.com.arenadev.identity.EnrollmentDeviceClaim;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyPrincipalResolver {
    private final DeviceClaimService deviceClaims;

    public StudyPrincipalResolver(DeviceClaimService deviceClaims) {
        this.deviceClaims = deviceClaims;
    }

    /** Study endpoints (Journeys, profile, achievements): requires a study-scoped claim (ADR-0075). */
    public StudyPrincipal resolve(String token) {
        return resolve(token, true);
    }

    /** Live-session discovery only: a LIVE "remember this device" claim is enough. */
    public StudyPrincipal resolveForLiveDiscovery(String token) {
        return resolve(token, false);
    }

    private StudyPrincipal resolve(String token, boolean study) {
        try {
            EnrollmentDeviceClaim claim = study
                    ? deviceClaims.requireUsableForStudy(token)
                    : deviceClaims.requireUsable(token);
            Enrollment enrollment = claim.getEnrollment();
            deviceClaims.markUsed(claim);
            return new StudyPrincipal(
                    enrollment.getStudent().getId(),
                    enrollment.getId(),
                    enrollment.getClassroom().getId(),
                    StudyPrincipal.Role.STUDENT
            );
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade de estudo inválida ou expirada.");
        }
    }
}
