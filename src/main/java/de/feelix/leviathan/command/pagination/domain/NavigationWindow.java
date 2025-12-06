package de.feelix.leviathan.command.pagination.domain;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import lombok.Getter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a sliding window of page numbers for navigation display.
 * <p>
 * Calculates which page numbers should be visible in a pagination navigation bar while:
 * <ul>
 *   <li>Centering the current page when possible</li>
 *   <li>Shifting toward edges when near the start/end</li>
 *   <li>Maintaining a consistent window size of {@code 2 * sidePages + 1} when possible</li>
 *   <li>Indicating omitted ranges via configurable ellipses</li>
 * </ul>
 * <p>
 * Example navigation display: {@code <- 1 ... 4 5 [6] 7 8 ... 20 ->}
 *
 * <p>Invariants:</p>
 * <ul>
 *   <li>{@code 1 <= windowStart <= currentPage <= windowEnd <= totalPages}</li>
 *   <li>{@code visiblePages} is an ascending, contiguous range {@code [windowStart, windowEnd]}</li>
 * </ul>
 *
 * @see PageInfo
 * @see PaginationConfig
 * @since 1.0.0
 */
public final class NavigationWindow {

    /** The current page number (1-based) */
    @Getter
    private final int currentPage;
    /** Total number of pages available */
    @Getter
    private final int totalPages;
    /** Number of pages shown on each side of the current page */
    @Getter
    private final int sidePages;
    /** List of page numbers visible in the navigation window */
    @Getter
    private final List<Integer> visiblePages;
    /** Whether to show ellipsis before the visible window */
    private final boolean showStartEllipsis;
    /** Whether to show ellipsis after the visible window */
    private final boolean showEndEllipsis;
    /** First page number in the visible window */
    @Getter
    private final int windowStart;
    /** Last page number in the visible window */
    @Getter
    private final int windowEnd;

    private NavigationWindow(PageInfo pageInfo, int sidePages) {
        this.currentPage = pageInfo.getCurrentPage();
        this.totalPages = pageInfo.getTotalPages();
        this.sidePages = sidePages;
        
        WindowBounds bounds = calculateWindowBounds();
        this.windowStart = bounds.start;
        this.windowEnd = bounds.end;
        this.visiblePages = calculateVisiblePages(bounds);
        this.showStartEllipsis = bounds.start > 1;
        this.showEndEllipsis = bounds.end < totalPages;
    }

    /**
     * Create a NavigationWindow from PageInfo and configuration.
     *
     * @param pageInfo the page information containing current page and total pages
     * @param config the pagination configuration with side pages setting
     * @return a new NavigationWindow instance
     * @throws NullPointerException if pageInfo or config is null
     */
    public static NavigationWindow from(PageInfo pageInfo, PaginationConfig config) {
        Objects.requireNonNull(pageInfo, "PageInfo cannot be null");
        Objects.requireNonNull(config, "Config cannot be null");
        return new NavigationWindow(pageInfo, config.getSidePages());
    }

    /**
     * Create a NavigationWindow with a custom side pages count.
     *
     * @param pageInfo the page information containing current page and total pages
     * @param sidePages the number of pages to show on each side of current page
     * @return a new NavigationWindow instance
     * @throws NullPointerException if pageInfo is null
     * @throws IllegalArgumentException if sidePages is negative
     */
    public static NavigationWindow of(PageInfo pageInfo, int sidePages) {
        Objects.requireNonNull(pageInfo, "PageInfo cannot be null");
        if (sidePages < 0) {
            throw new IllegalArgumentException("Side pages cannot be negative");
        }
        return new NavigationWindow(pageInfo, sidePages);
    }

    /**
     * Calculates the window bounds ensuring:
     * 1. Current page is centered when possible
     * 2. Window shifts at edges to maintain visibility
     * 3. Window size is consistent (2 * sidePages + 1) when possible
     */
    private WindowBounds calculateWindowBounds() {
        int windowSize = 2 * sidePages + 1;
        
        // If total pages fit in window, show all
        if (totalPages <= windowSize) {
            return new WindowBounds(1, totalPages);
        }

        // Calculate ideal centered window
        int idealStart = currentPage - sidePages;
        int idealEnd = currentPage + sidePages;

        // Shift window if near start edge
        if (idealStart < 1) {
            return new WindowBounds(1, windowSize);
        }

        // Shift window if near end edge
        if (idealEnd > totalPages) {
            int start = Math.max(1, totalPages - windowSize + 1);
            return new WindowBounds(start, totalPages);
        }

        // Centered window
        return new WindowBounds(idealStart, idealEnd);
    }

    private List<Integer> calculateVisiblePages(WindowBounds bounds) {
        List<Integer> pages = new ArrayList<>();
        for (int i = bounds.start; i <= bounds.end; i++) {
            pages.add(i);
        }
        return Collections.unmodifiableList(pages);
    }

    /**
     * Check if a page number is within the visible window.
     *
     * @param pageNumber the page number to check
     * @return true if the page is visible in the navigation window
     */
    public boolean isVisible(int pageNumber) {
        return pageNumber >= windowStart && pageNumber <= windowEnd;
    }

    /**
     * Get the distance from current page to the window start.
     *
     * @return number of pages between current page and window start
     */
    public int getDistanceToStart() {
        return currentPage - windowStart;
    }

    /**
     * Get the distance from current page to the window end.
     *
     * @return number of pages between current page and window end
     */
    public int getDistanceToEnd() {
        return windowEnd - currentPage;
    }

    /**
     * Get all visible page numbers before the current page.
     *
     * @return an unmodifiable list of page numbers less than current page
     */
    public List<Integer> getPagesBefore() {
        List<Integer> before = new ArrayList<>();
        for (int page : visiblePages) {
            if (page < currentPage) {
                before.add(page);
            }
        }
        return Collections.unmodifiableList(before);
    }

    /**
     * Get all visible page numbers after the current page.
     *
     * @return an unmodifiable list of page numbers greater than current page
     */
    public List<Integer> getPagesAfter() {
        List<Integer> after = new ArrayList<>();
        for (int page : visiblePages) {
            if (page > currentPage) {
                after.add(page);
            }
        }
        return Collections.unmodifiableList(after);
    }

    /**
     * Determine the window position relative to the full page range.
     * Useful for deciding how to render navigation controls.
     *
     * @return the window position enum value
     */
    public WindowPosition getPosition() {
        if (totalPages <= 2 * sidePages + 1) {
            return WindowPosition.FULL;
        }
        if (windowStart == 1) {
            return WindowPosition.AT_START;
        }
        if (windowEnd == totalPages) {
            return WindowPosition.AT_END;
        }
        return WindowPosition.MIDDLE;
    }

    /**
     * Check if an ellipsis should be shown before the visible window.
     *
     * @return true if there are hidden pages before the window start
     */
    public boolean showStartEllipsis() {
        return showStartEllipsis;
    }

    /**
     * Check if an ellipsis should be shown after the visible window.
     *
     * @return true if there are hidden pages after the window end
     */
    public boolean showEndEllipsis() {
        return showEndEllipsis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NavigationWindow that = (NavigationWindow) o;
        return currentPage == that.currentPage &&
                totalPages == that.totalPages &&
                sidePages == that.sidePages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentPage, totalPages, sidePages);
    }

    @Override
    public String toString() {
        return String.format("NavigationWindow{visible=%s, current=%d, ellipsis=[start=%b, end=%b]}",
                visiblePages, currentPage, showStartEllipsis, showEndEllipsis);
    }

    /**
     * Internal helper for window bounds calculation.
     */
    private static final class WindowBounds {
        final int start;
        final int end;

        WindowBounds(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Enum representing window position relative to full page range.
     */
    public enum WindowPosition {
        /** All pages are visible, no ellipsis needed */
        FULL,
        /** Window is at the start, may show end ellipsis */
        AT_START,
        /** Window is at the end, may show start ellipsis */
        AT_END,
        /** Window is in the middle, may show both ellipses */
        MIDDLE
    }
}
