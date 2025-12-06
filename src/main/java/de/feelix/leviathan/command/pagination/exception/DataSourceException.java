package de.feelix.leviathan.command.pagination.exception;

/**
 * Exception thrown when data source operations fail.
 * <p>
 * Indicates an error occurred while fetching elements or counting total elements
 * from a {@code PaginationDataSource}. Both synchronous and asynchronous
 * operations may wrap their underlying exceptions in this type.
 * </p>
 *
 * <p>Typical scenarios include database timeouts, I/O failures, or unexpected
 * exceptions thrown by user-provided callbacks in {@code LazyDataSource}.</p>
 */
public final class DataSourceException extends PaginationException {

    /** Error code for data source failures. */
    private static final String ERROR_CODE = "PAG-003";

    /**
     * Create a new data source exception with a message.
     *
     * @param message human-readable description of the failure
     */
    public DataSourceException(String message) {
        super(ERROR_CODE, message);
    }

    /**
     * Create a new data source exception with a message and cause.
     *
     * @param message human-readable description of the failure
     * @param cause   underlying cause (e.g., SQLException, IOException)
     */
    public DataSourceException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
