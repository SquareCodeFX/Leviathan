package de.feelix.leviathan.command.pagination.service;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import de.feelix.leviathan.command.pagination.exception.InvalidPageException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Interactive paginator with state management and navigation helpers.
 * Ideal for UI-driven pagination with event callbacks.
 *
 * @param <T> The type of elements being paginated
 */
public final class InteractivePaginator<T> {

    /** Underlying pagination service used to fetch pages. */
    private final PaginationService<T> service;
    /** Snapshot of configuration used for helper behaviors like prefetch radius. */
    private final PaginationConfig config;
    /** Registered listeners notified on navigation and error events. */
    private final List<Consumer<PaginationEvent<T>>> eventListeners;
    /** Local navigation history supporting back/forward. */
    private final NavigationHistory history;

    private PaginatedResult<T> currentResult;

    private InteractivePaginator(Builder<T> builder) {
        this.service = builder.service;
        this.config = builder.config;
        this.eventListeners = new ArrayList<>(builder.eventListeners);
        this.history = new NavigationHistory(builder.historySize);

        // Load initial page
        if (builder.initialPage > 0) {
            navigateTo(builder.initialPage);
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Navigate to a specific page.
     *
     * @param pageNumber the target 1-based page number
     * @return the resulting page
     * @throws InvalidPageException if the page number is outside the valid range
     */
    public PaginatedResult<T> navigateTo(int pageNumber) {
        try {
            PaginatedResult<T> result = service.getPage(pageNumber);
            updateState(result, NavigationType.DIRECT);
            return result;
        } catch (InvalidPageException e) {
            fireEvent(PaginationEvent.error(e, currentResult));
            throw e;
        }
    }

    /**
     * Navigate to a page asynchronously.
     *
     * @param pageNumber the target 1-based page number
     * @return future completing with the resulting page
     */
    public CompletableFuture<PaginatedResult<T>> navigateToAsync(int pageNumber) {
        return service.getPageAsync(pageNumber)
                .thenApply(result -> {
                    updateState(result, NavigationType.DIRECT);
                    return result;
                })
                .exceptionally(e -> {
                    fireEvent(PaginationEvent.error((Exception) e.getCause(), currentResult));
                    throw (RuntimeException) e.getCause();
                });
    }

    /**
     * Navigate to the next page.
     *
     * @return optional next page (empty if already at the last page or not initialized)
     */
    public Optional<PaginatedResult<T>> next() {
        if (currentResult == null || !currentResult.hasNextPage()) {
            return Optional.empty();
        }

        return service.getNextPage(currentResult)
                .map(result -> {
                    updateState(result, NavigationType.NEXT);
                    return result;
                });
    }

    /**
     * Navigate to the next page asynchronously.
     *
     * @return future completing with optional next page (empty if none)
     */
    public CompletableFuture<Optional<PaginatedResult<T>>> nextAsync() {
        if (currentResult == null || !currentResult.hasNextPage()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return service.getNextPageAsync(currentResult)
                .thenApply(optResult -> optResult.map(result -> {
                    updateState(result, NavigationType.NEXT);
                    return result;
                }));
    }

    /**
     * Navigate to the previous page.
     *
     * @return optional previous page (empty if already at the first page or not initialized)
     */
    public Optional<PaginatedResult<T>> previous() {
        if (currentResult == null || !currentResult.hasPreviousPage()) {
            return Optional.empty();
        }

        return service.getPreviousPage(currentResult)
                .map(result -> {
                    updateState(result, NavigationType.PREVIOUS);
                    return result;
                });
    }

    /**
     * Navigate to the previous page asynchronously.
     *
     * @return future completing with optional previous page (empty if none)
     */
    public CompletableFuture<Optional<PaginatedResult<T>>> previousAsync() {
        if (currentResult == null || !currentResult.hasPreviousPage()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return service.getPreviousPageAsync(currentResult)
                .thenApply(optResult -> optResult.map(result -> {
                    updateState(result, NavigationType.PREVIOUS);
                    return result;
                }));
    }

    /**
     * Navigate to the first page.
     *
     * @return the first page
     */
    public PaginatedResult<T> first() {
        PaginatedResult<T> result = service.getFirstPage();
        updateState(result, NavigationType.FIRST);
        return result;
    }

    /**
     * Navigate to the last page.
     *
     * @return the last available page
     */
    public PaginatedResult<T> last() {
        PaginatedResult<T> result = service.getLastPage();
        updateState(result, NavigationType.LAST);
        return result;
    }

    /**
     * Go back in navigation history.
     *
     * @return the page navigated to, if back navigation is possible
     */
    public Optional<PaginatedResult<T>> back() {
        return history.back()
                .map(pageNumber -> {
                    PaginatedResult<T> result = service.getPage(pageNumber);
                    this.currentResult = result;
                    fireEvent(PaginationEvent.navigated(result, NavigationType.BACK));
                    return result;
                });
    }

    /**
     * Go forward in navigation history.
     *
     * @return the page navigated to, if forward navigation is possible
     */
    public Optional<PaginatedResult<T>> forward() {
        return history.forward()
                .map(pageNumber -> {
                    PaginatedResult<T> result = service.getPage(pageNumber);
                    this.currentResult = result;
                    fireEvent(PaginationEvent.navigated(result, NavigationType.FORWARD));
                    return result;
                });
    }

    /**
     * Jump forward by a number of pages.
     *
     * @param pages number of pages to jump (negative values jump backwards)
     * @return the resulting page if the target is valid, otherwise empty
     */
    public Optional<PaginatedResult<T>> jump(int pages) {
        if (currentResult == null) {
            return Optional.empty();
        }

        int targetPage = currentResult.getCurrentPage() + pages;
        if (!service.isValidPage(targetPage)) {
            return Optional.empty();
        }

        return Optional.of(navigateTo(targetPage));
    }

    /**
     * Refresh the current page.
     *
     * @return the refreshed page; if not initialized, loads the first page
     */
    public PaginatedResult<T> refresh() {
        if (currentResult == null) {
            return first();
        }

        service.invalidatePage(currentResult.getCurrentPage());
        PaginatedResult<T> result = service.getPage(currentResult.getCurrentPage());
        this.currentResult = result;
        fireEvent(PaginationEvent.refreshed(result));
        return result;
    }

    /**
     * Prefetch surrounding pages for faster navigation.
     * Uses {@link PaginationConfig#getSidePages()} to determine prefetch radius.
     */
    public void prefetch() {
        if (currentResult != null) {
            service.prefetch(currentResult.getCurrentPage(), config.getSidePages());
        }
    }

    /**
     * Gets the current result.
     *
     * @return current page if available
     */
    public Optional<PaginatedResult<T>> getCurrentResult() {
        return Optional.ofNullable(currentResult);
    }

    /**
     * Gets the current page number.
     *
     * @return current 1-based page number, or 0 if not initialized
     */
    public int getCurrentPage() {
        return currentResult != null ? currentResult.getCurrentPage() : 0;
    }

    /**
     * Checks if navigation is possible.
     */
    public boolean canNavigateNext() {
        return currentResult != null && currentResult.hasNextPage();
    }

    public boolean canNavigatePrevious() {
        return currentResult != null && currentResult.hasPreviousPage();
    }

    public boolean canGoBack() {
        return history.canGoBack();
    }

    public boolean canGoForward() {
        return history.canGoForward();
    }

    /**
     * Adds an event listener.
     *
     * @param listener consumer invoked on pagination events
     */
    public void addEventListener(Consumer<PaginationEvent<T>> listener) {
        eventListeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }

    /**
     * Removes an event listener.
     *
     * @param listener previously added listener
     */
    public void removeEventListener(Consumer<PaginationEvent<T>> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Returns navigation history.
     *
     * @return unmodifiable list of visited page numbers in chronological order
     */
    public List<Integer> getHistory() {
        return history.getHistory();
    }

    private void updateState(PaginatedResult<T> result, NavigationType type) {
        if (currentResult == null || currentResult.getCurrentPage() != result.getCurrentPage()) {
            history.push(result.getCurrentPage());
        }
        this.currentResult = result;
        fireEvent(PaginationEvent.navigated(result, type));
    }

    private void fireEvent(PaginationEvent<T> event) {
        for (Consumer<PaginationEvent<T>> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
                // Don't let listener exceptions break pagination
            }
        }
    }

    /**
     * Navigation event types.
     */
    public enum NavigationType {
        DIRECT, NEXT, PREVIOUS, FIRST, LAST, BACK, FORWARD, REFRESH, ERROR
    }

    /**
     * Pagination event for listeners.
     */
    public static final class PaginationEvent<T> {
        /** Event type indicating what happened (navigation, refresh, error). */
        private final NavigationType type;
        /** Resulting page (may be null for error events). */
        private final PaginatedResult<T> result;
        /** Associated error for {@link NavigationType#ERROR} events. */
        private final Exception error;
        /** Epoch millis when the event occurred. */
        private final long timestamp;

        private PaginationEvent(NavigationType type, PaginatedResult<T> result, Exception error) {
            this.type = type;
            this.result = result;
            this.error = error;
            this.timestamp = System.currentTimeMillis();
        }

        public static <T> PaginationEvent<T> navigated(PaginatedResult<T> result, NavigationType type) {
            return new PaginationEvent<>(type, result, null);
        }

        public static <T> PaginationEvent<T> refreshed(PaginatedResult<T> result) {
            return new PaginationEvent<>(NavigationType.REFRESH, result, null);
        }

        public static <T> PaginationEvent<T> error(Exception error, PaginatedResult<T> lastResult) {
            return new PaginationEvent<>(NavigationType.ERROR, lastResult, error);
        }

        public NavigationType getType() { return type; }
        public Optional<PaginatedResult<T>> getResult() { return Optional.ofNullable(result); }
        public Optional<Exception> getError() { return Optional.ofNullable(error); }
        public long getTimestamp() { return timestamp; }
        public boolean isError() { return type == NavigationType.ERROR; }
    }

    /**
     * Navigation history with back/forward support.
     */
    private static final class NavigationHistory {
        /** Chronological list of visited pages. */
        private final LinkedList<Integer> history;
        /** Maximum number of entries to retain. */
        private final int maxSize;
        /** Index of the current position in history (-1 when empty). */
        private int position;

        NavigationHistory(int maxSize) {
            this.history = new LinkedList<>();
            this.maxSize = maxSize;
            this.position = -1;
        }

        void push(int pageNumber) {
            // Remove forward history
            while (history.size() > position + 1) {
                history.removeLast();
            }

            history.addLast(pageNumber);
            position++;

            // Trim if too large
            while (history.size() > maxSize) {
                history.removeFirst();
                position--;
            }
        }

        Optional<Integer> back() {
            if (!canGoBack()) {
                return Optional.empty();
            }
            position--;
            return Optional.of(history.get(position));
        }

        Optional<Integer> forward() {
            if (!canGoForward()) {
                return Optional.empty();
            }
            position++;
            return Optional.of(history.get(position));
        }

        boolean canGoBack() {
            return position > 0;
        }

        boolean canGoForward() {
            return position < history.size() - 1;
        }

        List<Integer> getHistory() {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    public static final class Builder<T> {
        /** Required pagination service used by the paginator. */
        private PaginationService<T> service;
        /** Optional configuration, defaults to {@link PaginationConfig#defaults()}. */
        private PaginationConfig config = PaginationConfig.defaults();
        /** Initial event listeners to register on build. */
        private final List<Consumer<PaginationEvent<T>>> eventListeners = new ArrayList<>();
        /** Maximum size of navigation history (default 50). */
        private int historySize = 50;
        /** Initial page to load on construction (0 to skip initial load). */
        private int initialPage = 1;

        private Builder() {}

        /**
         * Set the pagination service (required).
         */
        public Builder<T> service(PaginationService<T> service) {
            this.service = Objects.requireNonNull(service, "Service cannot be null");
            this.config = service.getConfig();
            return this;
        }

        /**
         * Override the configuration used for helper features (e.g., prefetch radius).
         */
        public Builder<T> config(PaginationConfig config) {
            this.config = Objects.requireNonNull(config, "Config cannot be null");
            return this;
        }

        /**
         * Add an event listener that will be registered on build.
         */
        public Builder<T> addEventListener(Consumer<PaginationEvent<T>> listener) {
            this.eventListeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
            return this;
        }

        /**
         * Set the maximum size of the navigation history (min 1).
         */
        public Builder<T> historySize(int historySize) {
            if (historySize < 1) {
                throw new IllegalArgumentException("History size must be at least 1");
            }
            this.historySize = historySize;
            return this;
        }

        /**
         * Set the initial page to load on construction (use 0 to skip initial load).
         */
        public Builder<T> initialPage(int initialPage) {
            if (initialPage < 0) {
                throw new IllegalArgumentException("Initial page cannot be negative");
            }
            this.initialPage = initialPage;
            return this;
        }

        /**
         * Skip loading an initial page; paginator starts without a current result.
         */
        public Builder<T> skipInitialLoad() {
            this.initialPage = 0;
            return this;
        }

        /**
         * Build the {@link InteractivePaginator} instance.
         */
        public InteractivePaginator<T> build() {
            Objects.requireNonNull(service, "Service must be set");
            return new InteractivePaginator<>(this);
        }
    }
}
