package de.feelix.leviathan.command.pagination.config;

import de.feelix.leviathan.command.pagination.exception.ConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for the pagination system.
 * Uses Builder pattern for flexible, type-safe construction.
 */
public final class PaginationConfig {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_VISIBLE_PAGES = 7;
    private static final int DEFAULT_SIDE_PAGES = 3;
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_CACHE_MAX_SIZE = 1000;

    private final int pageSize;
    private final int visiblePages;
    private final int sidePages;
    private final Duration cacheTtl;
    private final int cacheMaxSize;
    private final boolean cacheEnabled;
    private final boolean asyncEnabled;
    private final String ellipsis;
    private final String pageSeparator;
    private final String currentPagePrefix;
    private final String currentPageSuffix;
    private final String previousSymbol;
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

    public static Builder builder() {
        return new Builder();
    }

    public static PaginationConfig defaults() {
        return builder().build();
    }

    // Getters
    public int getPageSize() {
        return pageSize;
    }

    public int getVisiblePages() {
        return visiblePages;
    }

    public int getSidePages() {
        return sidePages;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public String getEllipsis() {
        return ellipsis;
    }

    public String getPageSeparator() {
        return pageSeparator;
    }

    public String getCurrentPagePrefix() {
        return currentPagePrefix;
    }

    public String getCurrentPageSuffix() {
        return currentPageSuffix;
    }

    public String getPreviousSymbol() {
        return previousSymbol;
    }

    public String getNextSymbol() {
        return nextSymbol;
    }

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

        private Builder() {}

        public Builder pageSize(int pageSize) {
            if (pageSize < 1) {
                throw new ConfigurationException("Page size must be at least 1, got: " + pageSize);
            }
            this.pageSize = pageSize;
            return this;
        }

        public Builder visiblePages(int visiblePages) {
            if (visiblePages < 1) {
                throw new ConfigurationException("Visible pages must be at least 1, got: " + visiblePages);
            }
            this.visiblePages = visiblePages;
            return this;
        }

        public Builder sidePages(int sidePages) {
            if (sidePages < 0) {
                throw new ConfigurationException("Side pages cannot be negative, got: " + sidePages);
            }
            this.sidePages = sidePages;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = Objects.requireNonNull(cacheTtl, "Cache TTL cannot be null");
            return this;
        }

        public Builder cacheMaxSize(int cacheMaxSize) {
            if (cacheMaxSize < 1) {
                throw new ConfigurationException("Cache max size must be at least 1, got: " + cacheMaxSize);
            }
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        public Builder asyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
            return this;
        }

        public Builder ellipsis(String ellipsis) {
            this.ellipsis = Objects.requireNonNull(ellipsis, "Ellipsis cannot be null");
            return this;
        }

        public Builder pageSeparator(String pageSeparator) {
            this.pageSeparator = Objects.requireNonNull(pageSeparator, "Page separator cannot be null");
            return this;
        }

        public Builder currentPageMarkers(String prefix, String suffix) {
            this.currentPagePrefix = Objects.requireNonNull(prefix, "Current page prefix cannot be null");
            this.currentPageSuffix = Objects.requireNonNull(suffix, "Current page suffix cannot be null");
            return this;
        }

        public Builder navigationSymbols(String previous, String next) {
            this.previousSymbol = Objects.requireNonNull(previous, "Previous symbol cannot be null");
            this.nextSymbol = Objects.requireNonNull(next, "Next symbol cannot be null");
            return this;
        }

        public PaginationConfig build() {
            validate();
            return new PaginationConfig(this);
        }

        private void validate() {
            if (sidePages * 2 + 1 > visiblePages) {
                throw new ConfigurationException(
                        String.format("Side pages (%d) too large for visible pages (%d). " +
                                "Required: sidePages * 2 + 1 <= visiblePages", sidePages, visiblePages));
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
