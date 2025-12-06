
package de.feelix.leviathan.command.pagination.exception;

import lombok.Getter;

/**
 * Exception thrown when a 1-based page number is outside the valid range.
 * <p>
 * Typical sources:
 * <ul>
 *   <li>Navigation attempts (e.g., {@code PageInfo.navigateTo(int)})</li>
 *   <li>Validation utilities (e.g., {@code PaginationUtils.validatePageNumber(...)})</li>
 *   <li>Service guards before fetching data for a page</li>
 * </ul>
 */
@Getter
public final class InvalidPageException extends PaginationException {

    /** Error code for invalid page requests. */
    private static final String ERROR_CODE = "PAG-002";

    /** The page number that was requested (1-based). */
    private final int requestedPage;
    /** Total number of available pages at the time of the request (at least 1). */
    private final int totalPages;

    /**
     * Construct a new InvalidPageException.
     *
     * @param requestedPage the requested 1-based page number
     * @param totalPages    the total number of pages (must be >= 1)
     */
    public InvalidPageException(int requestedPage, int totalPages) {
        super(ERROR_CODE, formatMessage(requestedPage, totalPages));
        this.requestedPage = requestedPage;
        this.totalPages = totalPages;
    }

    /**
     * Create a readable error message for the given request.
     */
    private static String formatMessage(int requestedPage, int totalPages) {
        if (requestedPage < 1) {
            return String.format("Page number must be at least 1, got: %d", requestedPage);
        }
        return String.format("Page %d does not exist. Valid range: 1 to %d", requestedPage, totalPages);
    }
}
