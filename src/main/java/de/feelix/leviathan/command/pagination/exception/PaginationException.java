package de.feelix.leviathan.command.pagination.exception;

/**
 * Base exception for all pagination-related errors.
 * Provides a common ancestor for exception handling.
 */
public abstract class PaginationException extends RuntimeException {

    private final String errorCode;

    protected PaginationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected PaginationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", errorCode, getClass().getSimpleName(), getMessage());
    }
}
