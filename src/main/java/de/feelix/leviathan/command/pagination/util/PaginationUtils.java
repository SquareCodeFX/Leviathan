package de.feelix.leviathan.command.pagination.util;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.message.MessageProvider;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.domain.NavigationWindow;
import de.feelix.leviathan.command.pagination.domain.PageInfo;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import de.feelix.leviathan.command.pagination.exception.InvalidPageException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utility class providing helper methods for pagination operations.
 */
public final class PaginationUtils {

    private PaginationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculates the total number of pages for given element count and page size.
     */
    public static int calculateTotalPages(long totalElements, int pageSize) {
        if (totalElements <= 0) {
            return 1;
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        return (int) Math.ceil((double) totalElements / pageSize);
    }

    /**
     * Calculates the offset for a given page number.
     */
    public static long calculateOffset(int pageNumber, int pageSize) {
        if (pageNumber < 1) {
            throw new InvalidPageException(pageNumber, 1);
        }
        return (long) (pageNumber - 1) * pageSize;
    }

    /**
     * Validates a page number against total pages.
     */
    public static void validatePageNumber(int pageNumber, int totalPages) {
        if (pageNumber < 1 || pageNumber > totalPages) {
            throw new InvalidPageException(pageNumber, totalPages);
        }
    }

    /**
     * Ensures a page number is within valid bounds.
     */
    public static int clampPageNumber(int pageNumber, int totalPages) {
        return Math.max(1, Math.min(pageNumber, Math.max(1, totalPages)));
    }

    /**
     * Generates a list of page numbers for navigation.
     */
    public static List<Integer> generatePageNumbers(int currentPage, int totalPages, int maxVisible) {
        if (totalPages <= maxVisible) {
            List<Integer> pages = new ArrayList<>(totalPages);
            for (int i = 1; i <= totalPages; i++) {
                pages.add(i);
            }
            return pages;
        }

        int half = maxVisible / 2;
        int start = Math.max(1, currentPage - half);
        int end = Math.min(totalPages, start + maxVisible - 1);

        // Adjust start if we're near the end
        if (end - start < maxVisible - 1) {
            start = Math.max(1, end - maxVisible + 1);
        }

        List<Integer> pages = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        return pages;
    }

    /**
     * Paginates a list in-memory.
     */
    public static <T> List<T> paginateList(List<T> list, int pageNumber, int pageSize) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        int totalPages = calculateTotalPages(list.size(), pageSize);
        validatePageNumber(pageNumber, totalPages);

        int start = (int) calculateOffset(pageNumber, pageSize);
        int end = Math.min(start + pageSize, list.size());

        return new ArrayList<>(list.subList(start, end));
    }

    /**
     * Paginates a collection with optional filtering and sorting.
     */
    public static <T> List<T> paginateCollection(
        Collection<T> collection,
        int pageNumber,
        int pageSize,
        Predicate<T> filter,
        Comparator<T> comparator) {

        List<T> filtered;
        if (filter != null) {
            filtered = new ArrayList<>();
            for (T item : collection) {
                if (filter.test(item)) {
                    filtered.add(item);
                }
            }
        } else {
            filtered = new ArrayList<>(collection);
        }

        if (comparator != null) {
            filtered.sort(comparator);
        }

        return paginateList(filtered, pageNumber, pageSize);
    }

    /**
     * Merges multiple paginated results into one.
     */
    @SafeVarargs
    public static <T> List<T> mergeResults(PaginatedResult<T>... results) {
        List<T> merged = new ArrayList<>();
        for (PaginatedResult<T> result : results) {
            if (result != null) {
                merged.addAll(result.getItems());
            }
        }
        return merged;
    }

    /**
     * Transforms items in a paginated result.
     */
    public static <T, R> List<R> transformItems(PaginatedResult<T> result, Function<T, R> transformer) {
        List<T> items = result.getItems();
        List<R> transformed = new ArrayList<>(items.size());
        for (T item : items) {
            transformed.add(transformer.apply(item));
        }
        return transformed;
    }

    /**
     * Checks if two page infos represent the same page state.
     */
    public static boolean isSamePage(PageInfo a, PageInfo b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.getCurrentPage() == b.getCurrentPage() &&
               a.getPageSize() == b.getPageSize() &&
               a.getTotalElements() == b.getTotalElements();
    }

    /**
     * Creates a summary string for a paginated result.
     *
     * @param pageInfo the page info
     * @param messages the message provider for customizable messages
     * @return the formatted summary string
     */
    public static String createSummary(@NotNull PageInfo pageInfo, @NotNull MessageProvider messages) {
        if (pageInfo.isEmpty()) {
            return messages.paginationEmpty();
        }

        return messages.paginationSummary(
            pageInfo.getStartIndex(),
            pageInfo.getEndIndex(),
            pageInfo.getTotalElements(),
            pageInfo.getCurrentPage(),
            pageInfo.getTotalPages()
        );
    }

    /**
     * Creates a compact navigation string.
     */
    public static String createNavigationString(NavigationWindow window, PaginationConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        if (window.showStartEllipsis()) {
            sb.append("1 ").append(config.getEllipsis()).append(" ");
        }

        List<Integer> pages = window.getVisiblePages();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                sb.append(config.getPageSeparator());
            }

            int page = pages.get(i);
            if (page == window.getCurrentPage()) {
                sb.append(config.getCurrentPagePrefix())
                    .append(page)
                    .append(config.getCurrentPageSuffix());
            } else {
                sb.append(page);
            }
        }

        if (window.showEndEllipsis()) {
            sb.append(" ").append(config.getEllipsis())
                .append(" ").append(window.getTotalPages());
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * Estimates memory usage for cached pages.
     */
    public static long estimateCacheMemory(int cachedPages, int averageItemsPerPage, int averageItemSizeBytes) {
        return (long) cachedPages * averageItemsPerPage * averageItemSizeBytes;
    }

    /**
     * Calculates optimal page size for given constraints.
     */
    public static int calculateOptimalPageSize(
        long totalElements,
        int minPageSize,
        int maxPageSize,
        int targetPages) {

        if (totalElements <= 0) {
            return minPageSize;
        }

        int calculated = (int) Math.ceil((double) totalElements / targetPages);
        return Math.max(minPageSize, Math.min(maxPageSize, calculated));
    }

    /**
     * Generates a range of page numbers.
     */
    public static List<Integer> pageRange(int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("Start must be <= end");
        }
        List<Integer> range = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            range.add(i);
        }
        return range;
    }

    /**
     * Checks if a page is near the boundary (first or last pages).
     */
    public static boolean isNearBoundary(int currentPage, int totalPages, int threshold) {
        return currentPage <= threshold || currentPage > totalPages - threshold;
    }

    /**
     * Calculates the midpoint page.
     */
    public static int getMidpointPage(int totalPages) {
        return Math.max(1, (totalPages + 1) / 2);
    }

    /**
     * Splits a large collection into page-sized chunks.
     */
    public static <T> List<List<T>> chunk(List<T> list, int chunkSize) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }

        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(new ArrayList<>(list.subList(i, Math.min(i + chunkSize, list.size()))));
        }
        return chunks;
    }

    /**
     * Calculates which page contains a specific item index.
     */
    public static int getPageForIndex(long itemIndex, int pageSize) {
        if (itemIndex < 0) {
            throw new IllegalArgumentException("Item index cannot be negative");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        return (int) (itemIndex / pageSize) + 1;
    }

    /**
     * Calculates the index of an item within its page.
     */
    public static int getIndexWithinPage(long itemIndex, int pageSize) {
        if (itemIndex < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Invalid arguments");
        }
        return (int) (itemIndex % pageSize);
    }
}
