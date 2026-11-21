package br.com.arenadev.identity;

/**
 * What a remembered-device claim may be used for (ADR-0075).
 * A claim obtained from declared information (name, registration, email typed
 * by the student) together with a shared code is NEVER a proof of identity.
 */
public enum DeviceClaimScope {
    /** Issued before v0.12.1 P0; accepted for study only behind a kill switch, never renewed. */
    LEGACY,
    /** Default for /api/join "remember this device": live-session re-entry only. */
    LIVE,
    /** Operator opt-in (legacy ADR-0005 risk acceptance): live re-entry + short-lived study access. */
    LIVE_STUDY
}
