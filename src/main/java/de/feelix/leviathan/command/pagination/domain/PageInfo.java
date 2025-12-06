package de.feelix.leviathan.command.pagination.domain;

import de.feelix.leviathan.command.pagination.exception.InvalidPageException;

import java.util.Objects;

/**
 * Immutable value object representing pagination metadata.
 * <p>
 * Semantics and invariants:
 * <ul>
 *   <li>Page numbers are 1-based (first page is 1).</li>
 *   <li>{@code totalPages} is at least 1 (empty datasets still report one logical page).</li>
 *   <li>{@code elementsOnPage} reflects the actual number of elements on the current page (0 if empty).</li>
 *   <li>Offset calculation: {@link #getOffset()} = {@code (currentPage - 1) * pageSize}.</li>
 * </ul>
 * Provides convenience methods for navigation and range calculations used by renderers.
 */
public final class PageInfo {

    private final int currentPage;
    private final int totalPages;
    private final int pageSize;
    private final long totalElements;
    private final int elementsOnPage;

    private PageInfo(Builder builder) {
        this.currentPage = builder.currentPage;
        this.totalPages = builder.totalPages;
        this.pageSize = builder.pageSize;
        this.totalElements = builder.totalElements;
        this.elementsOnPage = builder.elementsOnPage;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Factory method to create {@link PageInfo} from total elements and page size.
     * Performs derived calculations for {@code totalPages} and {@code elementsOnPage}.
     */
    public static PageInfo of(int currentPage, long totalElements, int pageSize) {
        int totalPages = calculateTotalPages(totalElements, pageSize);
        int elementsOnPage = calculateElementsOnPage(currentPage, totalPages, totalElements, pageSize);

        return builder()
            .currentPage(currentPage)
            .totalPages(totalPages)
            .pageSize(pageSize)
            .totalElements(totalElements)
            .elementsOnPage(elementsOnPage)
            .build();
    }

    private static int calculateTotalPages(long totalElements, int pageSize) {
        if (totalElements == 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    private static int calculateElementsOnPage(int currentPage, int totalPages, long totalElements, int pageSize) {
        if (totalElements == 0) {
            return 0;
        }
        if (currentPage < totalPages) {
            return pageSize;
        }
        int remaining = (int) (totalElements % pageSize);
        return remaining == 0 ? pageSize : remaining;
    }

    // Navigation checks
    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public boolean isFirstPage() {
        return currentPage == 1;
    }

    public boolean isLastPage() {
        return currentPage == totalPages;
    }

    public boolean isEmpty() {
        return totalElements == 0;
    }

    // Navigation calculations
    public int getNextPage() {
        return hasNextPage() ? currentPage + 1 : currentPage;
    }

    public int getPreviousPage() {
        return hasPreviousPage() ? currentPage - 1 : currentPage;
    }

    public int getFirstPage() {
        return 1;
    }

    public int getLastPage() {
        return totalPages;
    }

    // Offset calculations
    public long getOffset() {
        return (long) (currentPage - 1) * pageSize;
    }

    public int getStartIndex() {
        if (isEmpty()) return 0;
        return (int) getOffset() + 1;
    }

    public int getEndIndex() {
        if (isEmpty()) return 0;
        return getStartIndex() + elementsOnPage - 1;
    }

    // Getters
    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getElementsOnPage() {
        return elementsOnPage;
    }

    /**
     * Creates a new PageInfo for navigating to a specific page.
     */
    public PageInfo navigateTo(int targetPage) {
        if (targetPage < 1 || targetPage > totalPages) {
            throw new InvalidPageException(targetPage, totalPages);
        }
        return PageInfo.of(targetPage, totalElements, pageSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PageInfo pageInfo = (PageInfo) o;
        return currentPage == pageInfo.currentPage &&
               totalPages == pageInfo.totalPages &&
               pageSize == pageInfo.pageSize &&
               totalElements == pageInfo.totalElements;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentPage, totalPages, pageSize, totalElements);
    }

    @Override
    public String toString() {
        return String.format(
            "PageInfo{page=%d/%d, elements=%d-%d of %d}",
            currentPage, totalPages, getStartIndex(), getEndIndex(), totalElements
        );
    }

    public static final class Builder {
        private int currentPage = 1;
        private int totalPages = 1;
        private int pageSize = 10;
        private long totalElements = 0;
        private int elementsOnPage = 0;

        private Builder() {
        }

        public Builder currentPage(int currentPage) {
            if (currentPage < 1) {
                throw new InvalidPageException(currentPage, 1);
            }
            this.currentPage = currentPage;
            return this;
        }

        public Builder totalPages(int totalPages) {
            this.totalPages = Math.max(1, totalPages);
            return this;
        }

        public Builder pageSize(int pageSize) {
            if (pageSize < 1) {
                throw new IllegalArgumentException("Page size must be at least 1");
            }
            this.pageSize = pageSize;
            return this;
        }

        public Builder totalElements(long totalElements) {
            if (totalElements < 0) {
                throw new IllegalArgumentException("Total elements cannot be negative");
            }
            this.totalElements = totalElements;
            return this;
        }

        public Builder elementsOnPage(int elementsOnPage) {
            this.elementsOnPage = elementsOnPage;
            return this;
        }

        public PageInfo build() {
            if (currentPage > totalPages) {
                throw new InvalidPageException(currentPage, totalPages);
            }
            return new PageInfo(this);
        }
    }
}
