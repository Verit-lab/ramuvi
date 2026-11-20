package br.com.arenadev.content.share;

import org.springframework.http.HttpStatus;

/**
 * Typed outcome of the Library sharing contracts (ADR-0076 D7/D8). The code is stable for the UI;
 * the message never reveals whether a token existed, expired or was revoked (uniform 404).
 */
public class LibraryAccessException extends RuntimeException {
    public enum Code { SHARE_UNAVAILABLE, ACCOUNT_REQUIRED, VIEW_ONLY, ITEM_NOT_FOUND, RESOURCE_NOT_SHAREABLE, TOO_MANY_OPEN_SHARES }

    private final HttpStatus status;
    private final Code code;

    public LibraryAccessException(HttpStatus status, Code code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public Code code() {
        return code;
    }

    static LibraryAccessException unavailable() {
        return new LibraryAccessException(HttpStatus.NOT_FOUND, Code.SHARE_UNAVAILABLE,
                "Este link de material não está disponível. Ele pode ter expirado ou sido revogado.");
    }
}
