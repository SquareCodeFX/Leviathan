package de.feelix.leviathan.command.pagination.config;

import de.feelix.leviathan.command.pagination.exception.ConfigurationException;
import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for the pagination system.
 * Uses Builder pattern for flexible, type-safe construction.
 * <p>
 * This class holds all configurable settings for pagination behavior including:
 * <ul>
 *   <li>Page size and navigation display settings</li>
 *   <li>Cache configuration for optimized performance</li>
 *   <li>Visual styling symbols for navigation bars</li>
 * </ul>
 * <p>
 * Design notes:
 * <ul>
 *   <li>All fields are final; instances are immutable and thread-safe.</li>
 *   <li>Builder validates invariants (e.g., {@code sidePages * 2 + 1 <= visiblePages}).</li>
 *   <li>{@link #defaults()} provides a quick way to use sensible defaults.</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * PaginationConfig config = PaginationConfig.builder()
 *     .pageSize(15)
 *     .visiblePages(5)
 *     .cacheEnabled(true)
 *     .cacheTtl(Duration.ofMinutes(10))
 *     .ellipsis("…")
 *     .build();
 * }
 * </pre>
 *
 * @see PaginationConfig.Builder
 * @since 1.0.0
 */
@Getter
public final class PaginationConfig {

    /**
     * Default number of items displayed per page
     */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /**
     * Default number of page numbers visible in navigation
     */
    private static final int DEFAULT_VISIBLE_PAGES = 7;
    /**
     * Default number of pages shown on each side of current page
     */
    private static final int DEFAULT_SIDE_PAGES = 3;
    /**
     * Default time-to-live for cached pagination results
     */
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    /**
     * Default maximum number of entries in the pagination cache
     */
    private static final int DEFAULT_CACHE_MAX_SIZE = 1000;

    /**
     * Number of items to display per page
     * Get the number of items displayed per page.
     */
    private final int pageSize;
    /**
     * Number of page numbers visible in navigation bar
     *  Get the number of page numbers visible in navigation.
     */
    private final int visiblePages;
    /**
     * Number of pages shown on each side of the current page
     * Get the number of pages shown on each side of the current page.
     */
    private final int sidePages;
    /**
     * Time-to-live duration for cached pagination results
     * Get the time-to-live duration for cached pagination results.
     */
    private final Duration cacheTtl;
    /**
     * Maximum number of entries allowed in the cache
     * Get the maximum number of entries allowed in the cache.
     *
     */
    private final int cacheMaxSize;
    /**
     * Whether caching of pagination results is enabled
     * Check if caching of pagination results is enabled.
     */
    private final boolean cacheEnabled;
    /**
     * Whether asynchronous pagination operations are enabled
     * Check if asynchronous pagination operations are enabled.
     */
    private final boolean asyncEnabled;
    /**
     * Symbol used to indicate omitted pages (e.g., "...")
     * Get the symbol used to indicate omitted pages in navigation.
     */
    private final String ellipsis;
    /**
     * Separator between page numbers in navigation (e.g., " | ")
     * Get the separator between page numbers in navigation display.
     */
    private final String pageSeparator;
    /**
     * Prefix displayed before the current page number (e.g., "_")
     * Get the prefix displayed before the current page number.
     */
    private final String currentPagePrefix;
    /**
     * Suffix displayed after the current page number (e.g., "_")
     * Get the suffix displayed after the current page number.
     */
    private final String currentPageSuffix;
    /**
     * Symbol for navigating to the previous page (e.g., "<-")
     * Get the symbol for navigating to the previous page.
     */
    private final String previousSymbol;
    /**
     * Symbol for navigating to the next page (e.g., "->")
     * Get the symbol for navigating to the next page.
     */
    private final String nextSymbol;

    private PaginationConfig(Builder builder) {
        this.pageSize = builder.pageSize;
        this.visiblePages = builder.visiblePages;
        this.sidePages = builder.sidePages;
        this.cacheTtl = builder.cacheTtl;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.cacheEnabled = builder.cacheEnabled;
        this.asyncEnabled = builder.asyncEnabled;
        this.ellipsis = builder.ellipsis;
        this.pageSeparator = builder.pageSeparator;
        this.currentPagePrefix = builder.currentPagePrefix;
        this.currentPageSuffix = builder.currentPageSuffix;
        this.previousSymbol = builder.previousSymbol;
        this.nextSymbol = builder.nextSymbol;
    }

    /**
     * Create a new builder for constructing a PaginationConfig.
     *
     * @return a new Builder instance with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get a PaginationConfig with all default values.
     *
     * @return a PaginationConfig instance with default settings
     */
    public static PaginationConfig defaults() {
        return builder().build();
    }

    /**
     * Create a new builder initialized with this configuration's values.
     * Useful for creating modified copies of an existing configuration.
     *
     * @return a new Builder pre-populated with this config's values
     */
    public Builder toBuilder() {
        return new Builder()
            .pageSize(this.pageSize)
            .visiblePages(this.visiblePages)
            .sidePages(this.sidePages)
            .cacheTtl(this.cacheTtl)
            .cacheMaxSize(this.cacheMaxSize)
            .cacheEnabled(this.cacheEnabled)
            .asyncEnabled(this.asyncEnabled)
            .ellipsis(this.ellipsis)
            .pageSeparator(this.pageSeparator)
            .currentPageMarkers(this.currentPagePrefix, this.currentPageSuffix)
            .navigationSymbols(this.previousSymbol, this.nextSymbol);
    }

    /**
     * Builder for constructing {@link PaginationConfig} instances.
     * Provides a fluent API for setting configuration options with validation.
     */
    public static final class Builder {
        private int pageSize = DEFAULT_PAGE_SIZE;
        private int visiblePages = DEFAULT_VISIBLE_PAGES;
        private int sidePages = DEFAULT_SIDE_PAGES;
        private Duration cacheTtl = DEFAULT_CACHE_TTL;
        private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
        private boolean cacheEnabled = true;
        private boolean asyncEnabled = true;
        private String ellipsis = "...";
        private String pageSeparator = " | ";
        private String currentPagePrefix = "_";
        private String currentPageSuffix = "_";
        private String previousSymbol = "<-";
        private String nextSymbol = "->";

        private Builder() {
        }

        /**
         * Set the number of items to display per page.
         *
         * @param pageSize the page size (must be at least 1)
         * @return this builder for method chaining
         * @throws ConfigurationException if pageSize is less than 1
         */
        public Builder pageSize(int pageSize) {
            if (pageSize < 1) {
                throw new ConfigurationException("Page size must be at least 1, got: " + pageSize);
            }
            this.pageSize = pageSize;
            return this;
        }

        /**
         * Set the number of page numbers visible in the navigation bar.
         *
         * @param visiblePages the number of visible pages (must be at least 1)
         * @return this builder for method chaining
         * @throws ConfigurationException if visiblePages is less than 1
         */
        public Builder visiblePages(int visiblePages) {
            if (visiblePages < 1) {
                throw new ConfigurationException("Visible pages must be at least 1, got: " + visiblePages);
            }
            this.visiblePages = visiblePages;
            return this;
        }

        /**
         * Set the number of pages shown on each side of the current page.
         *
         * @param sidePages the number of side pages (must be non-negative)
         * @return this builder for method chaining
         * @throws ConfigurationException if sidePages is negative
         */
        public Builder sidePages(int sidePages) {
            if (sidePages < 0) {
                throw new ConfigurationException("Side pages cannot be negative, got: " + sidePages);
            }
            this.sidePages = sidePages;
            return this;
        }

        /**
         * Set the time-to-live duration for cached pagination results.
         *
         * @param cacheTtl the cache TTL duration (must not be null)
         * @return this builder for method chaining
         * @throws NullPointerException if cacheTtl is null
         */
        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = Objects.requireNonNull(cacheTtl, "Cache TTL cannot be null");
            return this;
        }

        /**
         * Set the maximum number of entries allowed in the cache.
         *
         * @param cacheMaxSize the maximum cache size (must be at least 1)
         * @return this builder for method chaining
         * @throws ConfigurationException if cacheMaxSize is less than 1
         */
        public Builder cacheMaxSize(int cacheMaxSize) {
            if (cacheMaxSize < 1) {
                throw new ConfigurationException("Cache max size must be at least 1, got: " + cacheMaxSize);
            }
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        /**
         * Enable or disable caching of pagination results.
         *
         * @param cacheEnabled true to enable caching, false to disable
         * @return this builder for method chaining
         */
        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        /**
         * Enable or disable asynchronous pagination operations.
         *
         * @param asyncEnabled true to enable async operations, false to disable
         * @return this builder for method chaining
         */
        public Builder asyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
            return this;
        }

        /**
         * Set the symbol used to indicate omitted pages in navigation.
         *
         * @param ellipsis the ellipsis string (e.g., "..." or "…")
         * @return this builder for method chaining
         * @throws NullPointerException if ellipsis is null
         */
        public Builder ellipsis(String ellipsis) {
            this.ellipsis = Objects.requireNonNull(ellipsis, "Ellipsis cannot be null");
            return this;
        }

        /**
         * Set the separator displayed between page numbers in navigation.
         *
         * @param pageSeparator the separator string (e.g., " | " or " ")
         * @return this builder for method chaining
         * @throws NullPointerException if pageSeparator is null
         */
        public Builder pageSeparator(String pageSeparator) {
            this.pageSeparator = Objects.requireNonNull(pageSeparator, "Page separator cannot be null");
            return this;
        }

        /**
         * Set the prefix and suffix used to highlight the current page number.
         *
         * @param prefix the prefix before current page (e.g., "[" or "_")
         * @param suffix the suffix after current page (e.g., "]" or "_")
         * @return this builder for method chaining
         * @throws NullPointerException if prefix or suffix is null
         */
        public Builder currentPageMarkers(String prefix, String suffix) {
            this.currentPagePrefix = Objects.requireNonNull(prefix, "Current page prefix cannot be null");
            this.currentPageSuffix = Objects.requireNonNull(suffix, "Current page suffix cannot be null");
            return this;
        }

        /**
         * Set the symbols for previous and next page navigation.
         *
         * @param previous the previous page symbol (e.g., "&lt;-" or "←")
         * @param next     the next page symbol (e.g., "-&gt;" or "→")
         * @return this builder for method chaining
         * @throws NullPointerException if previous or next is null
         */
        public Builder navigationSymbols(String previous, String next) {
            this.previousSymbol = Objects.requireNonNull(previous, "Previous symbol cannot be null");
            this.nextSymbol = Objects.requireNonNull(next, "Next symbol cannot be null");
            return this;
        }

        /**
         * Build and return the immutable PaginationConfig instance.
         * Validates all configuration settings before construction.
         *
         * @return the configured PaginationConfig instance
         * @throws ConfigurationException if validation fails
         */
        public PaginationConfig build() {
            validate();
            return new PaginationConfig(this);
        }

        /**
         * Validate the builder configuration.
         * Ensures sidePages and visiblePages are compatible.
         *
         * @throws ConfigurationException if validation fails
         */
        private void validate() {
            if (sidePages * 2 + 1 > visiblePages) {
                throw new ConfigurationException(
                    String.format(
                        "Side pages (%d) too large for visible pages (%d). " +
                        "Required: sidePages * 2 + 1 <= visiblePages", sidePages, visiblePages
                    ));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaginationConfig that = (PaginationConfig) o;
        return pageSize == that.pageSize &&
               visiblePages == that.visiblePages &&
               sidePages == that.sidePages &&
               cacheMaxSize == that.cacheMaxSize &&
               cacheEnabled == that.cacheEnabled &&
               asyncEnabled == that.asyncEnabled &&
               Objects.equals(cacheTtl, that.cacheTtl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSize, visiblePages, sidePages, cacheTtl, cacheMaxSize, cacheEnabled, asyncEnabled);
    }

    @Override
    public String toString() {
        return "PaginationConfig{" +
               "pageSize=" + pageSize +
               ", visiblePages=" + visiblePages +
               ", sidePages=" + sidePages +
               ", cacheTtl=" + cacheTtl +
               ", cacheMaxSize=" + cacheMaxSize +
               ", cacheEnabled=" + cacheEnabled +
               ", asyncEnabled=" + asyncEnabled +
               '}';
    }
}
