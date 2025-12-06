package de.feelix.leviathan.command.pagination.domain;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import lombok.Getter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NavigationWindow {

    // Getters
    @Getter
    private final int currentPage;
    @Getter
    private final int totalPages;
    @Getter
    private final int sidePages;
    @Getter
    private final List<Integer> visiblePages;
    private final boolean showStartEllipsis;
    private final boolean showEndEllipsis;
    @Getter
    private final int windowStart;
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
     * Creates a NavigationWindow from PageInfo and configuration.
     */
    public static NavigationWindow from(PageInfo pageInfo, PaginationConfig config) {
        Objects.requireNonNull(pageInfo, "PageInfo cannot be null");
        Objects.requireNonNull(config, "Config cannot be null");
        return new NavigationWindow(pageInfo, config.getSidePages());
    }

    /**
     * Creates a NavigationWindow with custom side pages count.
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
     * Checks if a page number is in the visible window.
     */
    public boolean isVisible(int pageNumber) {
        return pageNumber >= windowStart && pageNumber <= windowEnd;
    }

    /**
     * Returns the distance from current page to window edge.
     */
    public int getDistanceToStart() {
        return currentPage - windowStart;
    }

    public int getDistanceToEnd() {
        return windowEnd - currentPage;
    }

    /**
     * Returns pages before current in the window.
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
     * Returns pages after current in the window.
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
     * Calculates window position info for display.
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

    public boolean showStartEllipsis() {
        return showStartEllipsis;
    }

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
