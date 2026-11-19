package br.com.arenadev.identity;

import br.com.arenadev.classroom.Enrollment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollment_device_claims")
public class EnrollmentDeviceClaim {
    /** Live-session re-entry ("remember this device"), unchanged from ADR-0005. */
    public static final Duration LIVE_TTL = Duration.ofDays(180);
    /** Operator opt-in study access from a live join: short and never extended beyond this window. */
    public static final Duration LIVE_STUDY_TTL = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private DeviceClaimScope scope = DeviceClaimScope.LIVE;

    protected EnrollmentDeviceClaim() {
    }

    public EnrollmentDeviceClaim(Enrollment enrollment, String tokenHash, Instant now, DeviceClaimScope scope) {
        if (scope == null || scope == DeviceClaimScope.LEGACY) {
            // LEGACY only exists for rows created before V71; never issue new ones.
            throw new IllegalArgumentException("Escopo de dispositivo inválido.");
        }
        this.enrollment = enrollment;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.scope = scope;
        this.expiresAt = now.plus(ttl(scope));
    }

    private static Duration ttl(DeviceClaimScope scope) {
        return scope == DeviceClaimScope.LIVE_STUDY ? LIVE_STUDY_TTL : LIVE_TTL;
    }

    public UUID getId() {
        return id;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public DeviceClaimScope getScope() {
        return scope;
    }

    public void touch(Instant now) {
        this.lastUsedAt = now;
        // Only LIVE claims slide. LEGACY claims (possibly issued from a typed name)
        // keep their current expiry and LIVE_STUDY never exceeds its short window.
        if (scope == DeviceClaimScope.LIVE) {
            this.expiresAt = now.plus(LIVE_TTL);
        }
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
