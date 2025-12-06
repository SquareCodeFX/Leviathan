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

    private final PaginationService<T> service;
    private final PaginationConfig config;
    private final List<Consumer<PaginationEvent<T>>> eventListeners;
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
     */
    public PaginatedResult<T> first() {
        PaginatedResult<T> result = service.getFirstPage();
        updateState(result, NavigationType.FIRST);
        return result;
    }

    /**
     * Navigate to the last page.
     */
    public PaginatedResult<T> last() {
        PaginatedResult<T> result = service.getLastPage();
        updateState(result, NavigationType.LAST);
        return result;
    }

    /**
     * Go back in navigation history.
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
     */
    public void prefetch() {
        if (currentResult != null) {
            service.prefetch(currentResult.getCurrentPage(), config.getSidePages());
        }
    }

    /**
     * Gets the current result.
     */
    public Optional<PaginatedResult<T>> getCurrentResult() {
        return Optional.ofNullable(currentResult);
    }

    /**
     * Gets the current page number.
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
     */
    public void addEventListener(Consumer<PaginationEvent<T>> listener) {
        eventListeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
    }

    /**
     * Removes an event listener.
     */
    public void removeEventListener(Consumer<PaginationEvent<T>> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Returns navigation history.
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
        private final NavigationType type;
        private final PaginatedResult<T> result;
        private final Exception error;
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
        private final LinkedList<Integer> history;
        private final int maxSize;
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
        private PaginationService<T> service;
        private PaginationConfig config = PaginationConfig.defaults();
        private final List<Consumer<PaginationEvent<T>>> eventListeners = new ArrayList<>();
        private int historySize = 50;
        private int initialPage = 1;

        private Builder() {}

        public Builder<T> service(PaginationService<T> service) {
            this.service = Objects.requireNonNull(service, "Service cannot be null");
            this.config = service.getConfig();
            return this;
        }

        public Builder<T> config(PaginationConfig config) {
            this.config = Objects.requireNonNull(config, "Config cannot be null");
            return this;
        }

        public Builder<T> addEventListener(Consumer<PaginationEvent<T>> listener) {
            this.eventListeners.add(Objects.requireNonNull(listener, "Listener cannot be null"));
            return this;
        }

        public Builder<T> historySize(int historySize) {
            if (historySize < 1) {
                throw new IllegalArgumentException("History size must be at least 1");
            }
            this.historySize = historySize;
            return this;
        }

        public Builder<T> initialPage(int initialPage) {
            if (initialPage < 0) {
                throw new IllegalArgumentException("Initial page cannot be negative");
            }
            this.initialPage = initialPage;
            return this;
        }

        public Builder<T> skipInitialLoad() {
            this.initialPage = 0;
            return this;
        }

        public InteractivePaginator<T> build() {
            Objects.requireNonNull(service, "Service must be set");
            return new InteractivePaginator<>(this);
        }
    }
}
