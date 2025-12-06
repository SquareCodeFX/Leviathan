
package de.feelix.leviathan.command.pagination.exception;

import lombok.Getter;

/**
 * Exception thrown when an invalid page number is requested.
 */
@Getter
public final class InvalidPageException extends PaginationException {

    private static final String ERROR_CODE = "PAG-002";

    private final int requestedPage;
    private final int totalPages;

    public InvalidPageException(int requestedPage, int totalPages) {
        super(ERROR_CODE, formatMessage(requestedPage, totalPages));
        this.requestedPage = requestedPage;
        this.totalPages = totalPages;
    }

    private static String formatMessage(int requestedPage, int totalPages) {
        if (requestedPage < 1) {
            return String.format("Page number must be at least 1, got: %d", requestedPage);
        }
        return String.format("Page %d does not exist. Valid range: 1 to %d", requestedPage, totalPages);
    }
}
