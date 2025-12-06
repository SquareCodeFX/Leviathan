package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when cache operations fail.
 * <p>
 * Thrown from cache implementations (e.g., {@code LruPaginationCache}) to indicate
 * unexpected failures during load/compute or asynchronous computations.
 * A common recovery strategy for callers is to treat this as a cache miss
 * and proceed to compute the value directly, optionally logging the failure.
 * </p>
 */
public final class CacheException extends PaginationException {

    /** Error code for cache failures. */
    private static final String ERROR_CODE = "PAG-004";

    /**
     * Create a new cache exception with a message.
     *
     * @param message human-readable description of the cache failure
     */
    public CacheException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Create a new cache exception with a message and cause.
     *
     * @param message human-readable description of the cache failure
     * @param cause   underlying cause
     */
    public CacheException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
