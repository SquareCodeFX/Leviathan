package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when data source operations fail.
 */
public final class DataSourceException extends PaginationException {

    private static final String ERROR_CODE = "PAG-003";

    public DataSourceException(String message) {
        super(ERROR_CODE, message);
    }

    public DataSourceException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
