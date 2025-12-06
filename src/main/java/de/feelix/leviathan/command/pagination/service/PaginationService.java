package de.feelix.leviathan.command.pagination.service;

import de.feelix.leviathan.command.pagination.cache.CacheStats;
import de.feelix.leviathan.command.pagination.cache.LruPaginationCache;
import de.feelix.leviathan.command.pagination.cache.PaginationCache;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.datasource.PaginationDataSource;
import de.feelix.leviathan.command.pagination.domain.NavigationWindow;
import de.feelix.leviathan.command.pagination.domain.PageInfo;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import de.feelix.leviathan.command.pagination.exception.InvalidPageException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * Main pagination service providing core pagination functionality.
 * Orchestrates data sources, caching, and result generation.
 *
 * @param <T> The type of elements being paginated
 */
public final class PaginationService<T> {

    /** Immutable pagination behavior and styling configuration. */
    private final PaginationConfig config;
    /** Data provider used to fetch elements and total count. */
    private final PaginationDataSource<T> dataSource;
    /** Optional cache for page results keyed by {@link CacheKey}. */
    private final PaginationCache<CacheKey, PaginatedResult<T>> cache;
    /** Executor for async operations (defaults to {@link ForkJoinPool#commonPool()}). */
    private final ExecutorService executor;
    /** Whether caching is enabled per configuration and a cache instance is present. */
    private final boolean cacheEnabled;

    private PaginationService(Builder<T> builder) {
        this.config = builder.config;
        this.dataSource = builder.dataSource;
        this.cache = builder.cache;
        this.executor = builder.executor;
        this.cacheEnabled = config.isCacheEnabled() && cache != null;
    }

    /**
     * Create a new builder for {@link PaginationService}.
     *
     * @param <T> element type
     * @return builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Gets a specific page of results.
     *
     * @param pageNumber The 1-based page number
     * @return PaginatedResult containing the page data
     * @throws InvalidPageException if page number is invalid
     */
    public PaginatedResult<T> getPage(int pageNumber) {
        validatePageNumber(pageNumber);

        CacheKey cacheKey = createCacheKey(pageNumber);

        if (cacheEnabled) {
            Optional<PaginatedResult<T>> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        PaginatedResult<T> result = fetchPage(pageNumber);

        if (cacheEnabled) {
            cache.put(cacheKey, result);
        }

        return result;
    }

    /**
     * Gets a specific page asynchronously.
     *
     * @param pageNumber the 1-based page number
     * @return future completing with the requested page
     * @throws InvalidPageException if {@code pageNumber < 1}
     */
    public CompletableFuture<PaginatedResult<T>> getPageAsync(int pageNumber) {
        validatePageNumber(pageNumber);

        if (!config.isAsyncEnabled()) {
            return CompletableFuture.supplyAsync(() -> getPage(pageNumber), executor);
        }

        CacheKey cacheKey = createCacheKey(pageNumber);

        if (cacheEnabled) {
            return cache.getOrComputeAsync(cacheKey, () -> fetchPageAsync(pageNumber));
        }

        return fetchPageAsync(pageNumber);
    }

    /**
     * Gets the first page.
     *
     * @return the first page (page 1)
     */
    public PaginatedResult<T> getFirstPage() {
        return getPage(1);
    }

    /**
     * Gets the first page asynchronously.
     *
     * @return future completing with the first page (page 1)
     */
    public CompletableFuture<PaginatedResult<T>> getFirstPageAsync() {
        return getPageAsync(1);
    }

    /**
     * Gets the last page.
     *
     * @return the last available page computed from total elements
     */
    public PaginatedResult<T> getLastPage() {
        int totalPages = calculateTotalPages();
        return getPage(totalPages);
    }

    /**
     * Gets the last page asynchronously.
     *
     * @return future completing with the last available page
     */
    public CompletableFuture<PaginatedResult<T>> getLastPageAsync() {
        return countAsync()
                .thenCompose(count -> {
                    int totalPages = calculateTotalPages(count);
                    return getPageAsync(totalPages);
                });
    }

    /**
     * Gets the next page relative to the given result.
     *
     * @param current the current page
     * @return next page if it exists, otherwise {@link Optional#empty()}
     */
    public Optional<PaginatedResult<T>> getNextPage(PaginatedResult<T> current) {
        Objects.requireNonNull(current, "Current result cannot be null");

        if (!current.hasNextPage()) {
            return Optional.empty();
        }

        return Optional.of(getPage(current.getCurrentPage() + 1));
    }

    /**
     * Gets the next page asynchronously.
     *
     * @param current the current page
     * @return future completing with next page if it exists, else empty
     */
    public CompletableFuture<Optional<PaginatedResult<T>>> getNextPageAsync(PaginatedResult<T> current) {
        Objects.requireNonNull(current, "Current result cannot be null");

        if (!current.hasNextPage()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return getPageAsync(current.getCurrentPage() + 1)
                .thenApply(Optional::of);
    }

    /**
     * Gets the previous page relative to the given result.
     *
     * @param current the current page
     * @return previous page if it exists, otherwise {@link Optional#empty()}
     */
    public Optional<PaginatedResult<T>> getPreviousPage(PaginatedResult<T> current) {
        Objects.requireNonNull(current, "Current result cannot be null");

        if (!current.hasPreviousPage()) {
            return Optional.empty();
        }

        return Optional.of(getPage(current.getCurrentPage() - 1));
    }

    /**
     * Gets the previous page asynchronously.
     *
     * @param current the current page
     * @return future completing with previous page if it exists, else empty
     */
    public CompletableFuture<Optional<PaginatedResult<T>>> getPreviousPageAsync(PaginatedResult<T> current) {
        Objects.requireNonNull(current, "Current result cannot be null");

        if (!current.hasPreviousPage()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return getPageAsync(current.getCurrentPage() - 1)
                .thenApply(Optional::of);
    }

    /**
     * Navigates to a specific page from the current position.
     *
     * @param current    the current page (not used for bounds; kept for API symmetry)
     * @param targetPage the target 1-based page number
     * @return the target page
     * @throws InvalidPageException if {@code targetPage < 1} or beyond total pages
     */
    public PaginatedResult<T> navigateTo(PaginatedResult<T> current, int targetPage) {
        Objects.requireNonNull(current, "Current result cannot be null");
        return getPage(targetPage);
    }

    /**
     * Gets multiple pages at once.
     *
     * @param startPage inclusive start page (1-based)
     * @param endPage   inclusive end page (1-based)
     * @return list of page results in ascending order
     * @throws IllegalArgumentException if startPage > endPage
     */
    public List<PaginatedResult<T>> getPages(int startPage, int endPage) {
        validatePageNumber(startPage);
        validatePageNumber(endPage);

        if (startPage > endPage) {
            throw new IllegalArgumentException("Start page must be <= end page");
        }

        return java.util.stream.IntStream.rangeClosed(startPage, endPage)
                .mapToObj(this::getPage)
                .toList();
    }

    /**
     * Gets multiple pages asynchronously.
     *
     * @param startPage inclusive start page (1-based)
     * @param endPage   inclusive end page (1-based)
     * @return future completing with list of page results in ascending order
     * @throws IllegalArgumentException if startPage > endPage
     */
    public CompletableFuture<List<PaginatedResult<T>>> getPagesAsync(int startPage, int endPage) {
        validatePageNumber(startPage);
        validatePageNumber(endPage);

        if (startPage > endPage) {
            throw new IllegalArgumentException("Start page must be <= end page");
        }

        List<CompletableFuture<PaginatedResult<T>>> futures = java.util.stream.IntStream
                .rangeClosed(startPage, endPage)
                .mapToObj(this::getPageAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Prefetches pages around the current page for faster navigation.
     * Only effective when caching is enabled.
     *
     * @param currentPage the reference page
     * @param radius      number of pages to prefetch on each side
     */
    public void prefetch(int currentPage, int radius) {
        if (!cacheEnabled) {
            return;
        }

        int totalPages = calculateTotalPages();
        int start = Math.max(1, currentPage - radius);
        int end = Math.min(totalPages, currentPage + radius);

        for (int page = start; page <= end; page++) {
            if (page != currentPage) {
                final int pageToFetch = page;
                executor.submit(() -> {
                    try {
                        getPage(pageToFetch);
                    } catch (Exception ignored) {
                        // Prefetch failures are non-critical
                    }
                });
            }
        }
    }

    /**
     * Returns the total number of pages.
     *
     * @return total pages (at least 1)
     */
    public int getTotalPages() {
        return calculateTotalPages();
    }

    /**
     * Returns the total number of elements.
     *
     * @return total element count (non-negative)
     */
    public long count() {
        return dataSource.count();
    }

    /**
    * Returns the total number of elements asynchronously.
    *
    * @return future completing with total element count
    */
    public CompletableFuture<Long> countAsync() {
        return dataSource.countAsync();
    }

    /**
     * Checks if a page number is valid.
     *
     * @param pageNumber 1-based page number
     * @return true if page exists
     */
    public boolean isValidPage(int pageNumber) {
        if (pageNumber < 1) {
            return false;
        }
        int totalPages = calculateTotalPages();
        return pageNumber <= totalPages;
    }

    /**
     * Invalidates cached data.
     * No-op when caching is disabled.
     */
    public void invalidateCache() {
        if (cacheEnabled) {
            cache.invalidateAll();
        }
    }

    /**
     * Invalidates a specific page from cache.
     * No-op when caching is disabled.
     *
     * @param pageNumber 1-based page number to evict
     */
    public void invalidatePage(int pageNumber) {
        if (cacheEnabled) {
            cache.invalidate(createCacheKey(pageNumber));
        }
    }

    /**
     * Returns cache statistics.
     *
     * @return cache stats if caching is enabled, otherwise empty
     */
    public Optional<CacheStats> getCacheStats() {
        return cacheEnabled ? Optional.of(cache.getStats()) : Optional.empty();
    }

    /**
     * Returns the current configuration.
     *
     * @return pagination config (immutable)
     */
    public PaginationConfig getConfig() {
        return config;
    }

    // Private methods

    /**
     * Fetch a page synchronously from the data source and build the result object.
     */
    private PaginatedResult<T> fetchPage(int pageNumber) {
        long totalElements = dataSource.count();
        PageInfo pageInfo = PageInfo.of(pageNumber, totalElements, config.getPageSize());

        // Validate page exists
        if (pageNumber > pageInfo.getTotalPages()) {
            throw new InvalidPageException(pageNumber, pageInfo.getTotalPages());
        }

        long offset = pageInfo.getOffset();
        List<T> items = dataSource.fetch(offset, config.getPageSize());

        NavigationWindow window = NavigationWindow.from(pageInfo, config);

        return PaginatedResult.<T>builder()
                .items(items)
                .pageInfo(pageInfo)
                .navigationWindow(window)
                .addMetadata("dataSourceId", dataSource.getIdentifier())
                .build();
    }

    /**
     * Fetch a page asynchronously from the data source and build the result object.
     */
    private CompletableFuture<PaginatedResult<T>> fetchPageAsync(int pageNumber) {
        return dataSource.countAsync()
                .thenCompose(totalElements -> {
                    PageInfo pageInfo = PageInfo.of(pageNumber, totalElements, config.getPageSize());

                    if (pageNumber > pageInfo.getTotalPages()) {
                        return CompletableFuture.failedFuture(
                                new InvalidPageException(pageNumber, pageInfo.getTotalPages()));
                    }

                    long offset = pageInfo.getOffset();
                    return dataSource.fetchAsync(offset, config.getPageSize())
                            .thenApply(items -> {
                                NavigationWindow window = NavigationWindow.from(pageInfo, config);
                                return PaginatedResult.<T>builder()
                                        .items(items)
                                        .pageInfo(pageInfo)
                                        .navigationWindow(window)
                                        .addMetadata("dataSourceId", dataSource.getIdentifier())
                                        .build();
                            });
                });
    }

    /**
     * Validate a 1-based page number.
     */
    private void validatePageNumber(int pageNumber) {
        if (pageNumber < 1) {
            throw new InvalidPageException(pageNumber, 1);
        }
    }

    /**
     * Calculate total pages from the current data source count.
     */
    private int calculateTotalPages() {
        return calculateTotalPages(dataSource.count());
    }

    /**
     * Calculate total pages from a provided element count.
     */
    private int calculateTotalPages(long totalElements) {
        if (totalElements == 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalElements / config.getPageSize());
    }

    /**
     * Build a cache key for the given page number and current configuration.
     */
    private CacheKey createCacheKey(int pageNumber) {
        return new CacheKey(dataSource.getIdentifier(), pageNumber, config.getPageSize());
    }

    /**
     * Cache key combining data source ID, page number, and page size.
     */
    private record CacheKey(String dataSourceId, int pageNumber, int pageSize) {}

    /**
     * Builder for {@link PaginationService}.
     */
    public static final class Builder<T> {
        /** Configuration used by the service (defaults applied via {@link PaginationConfig#defaults()}). */
        private PaginationConfig config = PaginationConfig.defaults();
        /** Data source for items and counts (required). */
        private PaginationDataSource<T> dataSource;
        /** Optional cache for page results (if unset and caching enabled, use {@link #withDefaultCache()}). */
        private PaginationCache<CacheKey, PaginatedResult<T>> cache;
        /** Executor for async operations (defaults to common pool). */
        private ExecutorService executor = ForkJoinPool.commonPool();

        private Builder() {}

        /**
         * Set the pagination configuration.
         *
         * @param config non-null configuration
         * @return this builder
         */
        public Builder<T> config(PaginationConfig config) {
            this.config = Objects.requireNonNull(config, "Config cannot be null");
            return this;
        }

        /**
         * Set the data source for this service.
         *
         * @param dataSource non-null data source
         * @return this builder
         */
        public Builder<T> dataSource(PaginationDataSource<T> dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "Data source cannot be null");
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder<T> cache(PaginationCache<?, ?> cache) {
            this.cache = (PaginationCache<CacheKey, PaginatedResult<T>>) cache;
            return this;
        }

        /**
         * Set the executor for asynchronous operations.
         *
         * @param executor non-null executor
         * @return this builder
         */
        public Builder<T> executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
            return this;
        }

        /**
         * Creates a default LRU cache based on configuration.
         */
        public Builder<T> withDefaultCache() {
            this.cache = LruPaginationCache.fromConfig(
                    config != null ? config : PaginationConfig.defaults()
            );
            return this;
        }

        /**
         * Build the service instance.
         *
         * @return new PaginationService
         * @throws NullPointerException if required fields are missing
         */
        public PaginationService<T> build() {
            Objects.requireNonNull(dataSource, "Data source must be set");
            return new PaginationService<>(this);
        }
    }
}
