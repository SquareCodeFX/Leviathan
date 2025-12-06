package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when pagination configuration is invalid.
 */
public final class ConfigurationException extends PaginationException {

    private static final String ERROR_CODE = "PAG-001";

    public ConfigurationException(String message) {
        super(ERROR_CODE, message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
