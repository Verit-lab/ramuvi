package br.com.arenadev.identity;

import br.com.arenadev.classroom.Enrollment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Remembered-device claims (ADR-0005, restricted by ADR-0075).
 * The only issuance path is the live-session join, where the student identifies
 * by declared information in front of the teacher. Such a claim is NOT a proof
 * of identity: by default it only re-enters live sessions and is rejected by
 * every study endpoint ({@link #requireUsableForStudy}).
 */
@Service
public class DeviceClaimService {
    private final EnrollmentDeviceClaimRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean liveClaimStudyEnabled;
    private final boolean legacyClaimStudyEnabled;

    public DeviceClaimService(
            EnrollmentDeviceClaimRepository repository,
            @Value("${app.study.live-device-claim-study-enabled:false}") boolean liveClaimStudyEnabled,
            @Value("${app.study.legacy-device-claims-study-enabled:true}") boolean legacyClaimStudyEnabled
    ) {
        this.repository = repository;
        this.liveClaimStudyEnabled = liveClaimStudyEnabled;
        this.legacyClaimStudyEnabled = legacyClaimStudyEnabled;
    }

    /** Live-session "remember this device". Study access only with the explicit operator opt-in. */
    @Transactional
    public IssuedDeviceClaim issueForLiveSession(Enrollment enrollment) {
        Instant now = Instant.now();
        String token = generateToken();
        DeviceClaimScope scope = liveClaimStudyEnabled ? DeviceClaimScope.LIVE_STUDY : DeviceClaimScope.LIVE;
        EnrollmentDeviceClaim claim = new EnrollmentDeviceClaim(enrollment, hashToken(token), now, scope);
        repository.save(claim);
        return new IssuedDeviceClaim(token, claim.getExpiresAt());
    }

    /** X-Study-Token: Journeys, profile, achievements. LIVE claims are always refused. */
    @Transactional(readOnly = true)
    public EnrollmentDeviceClaim requireUsableForStudy(String rawToken) {
        EnrollmentDeviceClaim claim = requireUsable(rawToken);
        boolean allowed = switch (claim.getScope()) {
            case LIVE -> false;
            case LIVE_STUDY -> liveClaimStudyEnabled;
            case LEGACY -> legacyClaimStudyEnabled;
        };
        if (!allowed) throw invalidClaim();
        return claim;
    }

    @Transactional(readOnly = true)
    public EnrollmentDeviceClaim requireUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidClaim();
        }
        Instant now = Instant.now();
        EnrollmentDeviceClaim claim = repository.findByTokenHash(hashToken(rawToken.trim()))
                .orElseThrow(DeviceClaimService::invalidClaim);
        if (!claim.isUsableAt(now) || !claim.getEnrollment().isActive()) {
            throw invalidClaim();
        }
        return claim;
    }

    @Transactional
    public void markUsed(EnrollmentDeviceClaim claim) {
        claim.touch(Instant.now());
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByTokenHash(hashToken(rawToken.trim()))
                .filter(claim -> claim.getRevokedAt() == null)
                .ifPresent(claim -> claim.revoke(Instant.now()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 indisponível.", error);
        }
    }

    private static IllegalArgumentException invalidClaim() {
        return new IllegalArgumentException("Dispositivo não reconhecido para esta turma.");
    }

    public record IssuedDeviceClaim(String token, Instant expiresAt) {
    }
}
