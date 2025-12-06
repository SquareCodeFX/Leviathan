package de.feelix.leviathan.command.pagination.exception;

/**
 * Base exception for all pagination-related errors.
 * <p>
 * Provides a common ancestor used across pagination modules (datasource, cache, config, services)
 * so callers can catch a single type for pagination failures while still retaining specific
 * subtypes for fine-grained handling.
 * </p>
 *
 * <p>Every subclass supplies a short, stable {@code errorCode} to aid in diagnostics and
 * log filtering (e.g., {@code PAG-001}).</p>
 *
 * @see ConfigurationException
 * @see InvalidPageException
 * @see DataSourceException
 * @see CacheException
 * @since 1.0.0
 */
public abstract class PaginationException extends RuntimeException {

    /**
     * Stable short error identifier (e.g., "PAG-001"). Useful for log parsing and user-friendly output.
     */
    private final String errorCode;

    /**
     * Create a new pagination exception with the given error code and message.
     *
     * @param errorCode stable error identifier
     * @param message   human-readable error description
     */
    protected PaginationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Create a new pagination exception with the given error code, message and cause.
     *
     * @param errorCode stable error identifier
     * @param message   human-readable error description
     * @param cause     underlying cause
     */
    protected PaginationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Get the stable error identifier for this exception.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", errorCode, getClass().getSimpleName(), getMessage());
    }
}
