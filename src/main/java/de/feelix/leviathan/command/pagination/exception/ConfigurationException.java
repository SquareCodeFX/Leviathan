package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when pagination configuration is invalid.
 * <p>
 * Typical causes include:
 * <ul>
 *   <li>Invalid page size (must be at least 1)</li>
 *   <li>Inconsistent navigation settings (e.g., {@code sidePages * 2 + 1 > visiblePages})</li>
 *   <li>Null or otherwise illegal configuration values</li>
 * </ul>
 * Used by builders such as {@code PaginationConfig.Builder} to signal validation failures.
 */
public final class ConfigurationException extends PaginationException {

    /**
     * Error code for configuration failures.
     */
    private static final String ERROR_CODE = "PAG-001";

    /**
     * Create a new configuration exception with a message.
     *
     * @param message human-readable description of the invalid configuration
     */
    public ConfigurationException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Create a new configuration exception with a message and cause.
     *
     * @param message human-readable description of the invalid configuration
     * @param cause   underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
