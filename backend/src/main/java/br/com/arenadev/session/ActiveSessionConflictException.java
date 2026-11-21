package br.com.arenadev.session;

/**
 * Typed 409 for the "at most one ACTIVE session per classroom" invariant (ADR-0076 D3).
 * Carries the session that is actually active so the UI can offer "Retomar aula" or
 * "Encerrar e iniciar outra" instead of a generic integrity error.
 */
public class ActiveSessionConflictException extends RuntimeException {
    public enum Code { ACTIVE_SESSION_EXISTS, ACTIVE_SESSION_CHANGED }

    private final Code code;
    private final SessionService.SessionView activeSession;

    public ActiveSessionConflictException(Code code, SessionService.SessionView activeSession, String message) {
        super(message);
        this.code = code;
        this.activeSession = activeSession;
    }

    public Code code() {
        return code;
    }

    public SessionService.SessionView activeSession() {
        return activeSession;
    }
}
