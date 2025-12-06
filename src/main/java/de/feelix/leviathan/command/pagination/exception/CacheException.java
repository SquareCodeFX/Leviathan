package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when cache operations fail.
 */
public final class CacheException extends PaginationException {

    private static final String ERROR_CODE = "PAG-004";

    public CacheException(String message) {
        super(ERROR_CODE, message);
    }

    public CacheException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
