package br.com.arenadev.study;

import java.util.UUID;

/** Same canonical student, different authentication proof. Never a generic role grant. */
public record StudyPrincipal(UUID userId, UUID enrollmentId, UUID classroomId, Role role, Proof proof) {
    public enum Role { STUDENT }
    public enum Proof { DEVICE_CLAIM, VERIFIED_ACCOUNT }

    /** Compatibility constructor for legacy device-claim access. */
    public StudyPrincipal(UUID userId, UUID enrollmentId, UUID classroomId, Role role) {
        this(userId, enrollmentId, classroomId, role, Proof.DEVICE_CLAIM);
    }

    public static StudyPrincipal verifiedAccount(UUID studentId) {
        return new StudyPrincipal(studentId, null, null, Role.STUDENT, Proof.VERIFIED_ACCOUNT);
    }

    public boolean verifiedAccount() { return proof == Proof.VERIFIED_ACCOUNT; }
}
