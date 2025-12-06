package de.feelix.leviathan.command.pagination.cache;

import java.util.Objects;

/**
 * Immutable value object for cache statistics.
 * <p>
 * Tracks cache performance metrics including hits, misses, evictions, and load times.
 * This class provides both raw counts and calculated metrics like hit rate and utilization.
 * <p>
 * Example usage:
 * <pre>{@code
 * CacheStats stats = cache.getStats();
 * System.out.println("Hit rate: " + (stats.getHitRate() * 100) + "%");
 * System.out.println("Cache utilization: " + (stats.getUtilization() * 100) + "%");
 * }</pre>
 *
 * @see PaginationCache
 * @since 1.0.0
 */
public final class CacheStats {

    /** Number of cache hits (successful lookups) */
    private final long hitCount;
    /** Number of cache misses (failed lookups requiring load) */
    private final long missCount;
    /** Number of entries evicted from the cache */
    private final long evictionCount;
    /** Number of successful load operations */
    private final long loadSuccessCount;
    /** Number of failed load operations */
    private final long loadFailureCount;
    /**
     * Total time spent loading entries in milliseconds.
     *
     * <p>Note: The producing cache currently records nanoseconds internally
     * and converts them to milliseconds when exposing stats (see
     * {@link de.feelix.leviathan.command.pagination.cache.LruPaginationCache#getStats()}).
     * This field therefore stores milliseconds.</p>
     */
    private final long totalLoadTime;
    /** Current number of entries in the cache */
    private final int currentSize;
    /** Maximum allowed entries in the cache */
    private final int maxSize;

    private CacheStats(Builder builder) {
        this.hitCount = builder.hitCount;
        this.missCount = builder.missCount;
        this.evictionCount = builder.evictionCount;
        this.loadSuccessCount = builder.loadSuccessCount;
        this.loadFailureCount = builder.loadFailureCount;
        this.totalLoadTime = builder.totalLoadTime;
        this.currentSize = builder.currentSize;
        this.maxSize = builder.maxSize;
    }

    /**
     * Create a new builder for constructing CacheStats.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an empty CacheStats with all counters at zero.
     *
     * @return an empty CacheStats instance
     */
    public static CacheStats empty() {
        return builder().build();
    }

    /**
     * Calculate the cache hit rate as a ratio between 0.0 and 1.0.
     * A higher hit rate indicates better cache performance.
     *
     * @return the hit rate (hits / total requests), or 0.0 if no requests
     */
    public double getHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    /**
     * Calculate the cache miss rate as a ratio between 0.0 and 1.0.
     * This is the complement of the hit rate (1.0 - hitRate).
     *
     * @return the miss rate (misses / total requests)
     */
    public double getMissRate() {
        return 1.0 - getHitRate();
    }

    /**
     * Get the total number of cache requests (hits + misses).
     *
     * @return the total request count
     */
    public long getRequestCount() {
        return hitCount + missCount;
    }

    /**
     * Calculate the average time to load an entry in milliseconds.
     *
     * @return the average load time (ms), or 0.0 if no loads have occurred
     */
    public double getAverageLoadTime() {
        long totalLoads = loadSuccessCount + loadFailureCount;
        return totalLoads == 0 ? 0.0 : (double) totalLoadTime / totalLoads;
    }

    /**
     * Calculate the success rate for load operations.
     *
     * @return the load success rate (successful loads / total loads), or 1.0 if no loads
     */
    public double getLoadSuccessRate() {
        long totalLoads = loadSuccessCount + loadFailureCount;
        return totalLoads == 0 ? 1.0 : (double) loadSuccessCount / totalLoads;
    }

    /**
     * Calculate the cache utilization as a ratio of current size to max size.
     *
     * @return the utilization ratio (currentSize / maxSize), or 0.0 if maxSize is 0
     */
    public double getUtilization() {
        return maxSize == 0 ? 0.0 : (double) currentSize / maxSize;
    }

    /**
     * Get the number of cache hits.
     *
     * @return the hit count
     */
    public long getHitCount() {
        return hitCount;
    }

    /**
     * Get the number of cache misses.
     *
     * @return the miss count
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * Get the number of entries evicted from the cache.
     *
     * @return the eviction count
     */
    public long getEvictionCount() {
        return evictionCount;
    }

    /**
     * Get the number of successful load operations.
     *
     * @return the successful load count
     */
    public long getLoadSuccessCount() {
        return loadSuccessCount;
    }

    /**
     * Get the number of failed load operations.
     *
     * @return the failed load count
     */
    public long getLoadFailureCount() {
        return loadFailureCount;
    }

    /**
     * Get the total time spent loading entries in nanoseconds.
     *
     * @return the total load time in nanoseconds
     */
    public long getTotalLoadTime() {
        return totalLoadTime;
    }

    /**
     * Get the current number of entries in the cache.
     *
     * @return the current cache size
     */
    public int getCurrentSize() {
        return currentSize;
    }

    /**
     * Get the maximum number of entries the cache can hold.
     *
     * @return the maximum cache size
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Create a new CacheStats with the hit count incremented by 1.
     * Since CacheStats is immutable, this returns a new instance.
     *
     * @return a new CacheStats with incremented hit count
     */
    public CacheStats withHit() {
        return toBuilder().hitCount(hitCount + 1).build();
    }

    /**
     * Create a new CacheStats with the miss count incremented by 1.
     * Since CacheStats is immutable, this returns a new instance.
     *
     * @return a new CacheStats with incremented miss count
     */
    public CacheStats withMiss() {
        return toBuilder().missCount(missCount + 1).build();
    }

    /**
     * Create a new builder initialized with this CacheStats' values.
     * Useful for creating modified copies.
     *
     * @return a new Builder pre-populated with this stats' values
     */
    public Builder toBuilder() {
        return new Builder()
                .hitCount(hitCount)
                .missCount(missCount)
                .evictionCount(evictionCount)
                .loadSuccessCount(loadSuccessCount)
                .loadFailureCount(loadFailureCount)
                .totalLoadTime(totalLoadTime)
                .currentSize(currentSize)
                .maxSize(maxSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheStats that = (CacheStats) o;
        return hitCount == that.hitCount &&
                missCount == that.missCount &&
                evictionCount == that.evictionCount &&
                loadSuccessCount == that.loadSuccessCount &&
                loadFailureCount == that.loadFailureCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hitCount, missCount, evictionCount, loadSuccessCount, loadFailureCount);
    }

    @Override
    public String toString() {
        return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, size=%d/%d, evictions=%d}",
                hitCount, missCount, getHitRate() * 100, currentSize, maxSize, evictionCount);
    }

    /**
     * Builder for constructing {@link CacheStats} instances.
     * Provides a fluent API for setting cache statistics values.
     */
    public static final class Builder {
        private long hitCount = 0;
        private long missCount = 0;
        private long evictionCount = 0;
        private long loadSuccessCount = 0;
        private long loadFailureCount = 0;
        private long totalLoadTime = 0;
        private int currentSize = 0;
        private int maxSize = 0;

        private Builder() {}

        /**
         * Set the number of cache hits.
         *
         * @param hitCount the hit count
         * @return this builder for method chaining
         */
        public Builder hitCount(long hitCount) {
            this.hitCount = hitCount;
            return this;
        }

        /**
         * Set the number of cache misses.
         *
         * @param missCount the miss count
         * @return this builder for method chaining
         */
        public Builder missCount(long missCount) {
            this.missCount = missCount;
            return this;
        }

        /**
         * Set the number of entries evicted from the cache.
         *
         * @param evictionCount the eviction count
         * @return this builder for method chaining
         */
        public Builder evictionCount(long evictionCount) {
            this.evictionCount = evictionCount;
            return this;
        }

        /**
         * Set the number of successful load operations.
         *
         * @param loadSuccessCount the successful load count
         * @return this builder for method chaining
         */
        public Builder loadSuccessCount(long loadSuccessCount) {
            this.loadSuccessCount = loadSuccessCount;
            return this;
        }

        /**
         * Set the number of failed load operations.
         *
         * @param loadFailureCount the failed load count
         * @return this builder for method chaining
         */
        public Builder loadFailureCount(long loadFailureCount) {
            this.loadFailureCount = loadFailureCount;
            return this;
        }

        /**
         * Set the total time spent loading entries in nanoseconds.
         *
         * @param totalLoadTime the total load time in nanoseconds
         * @return this builder for method chaining
         */
        public Builder totalLoadTime(long totalLoadTime) {
            this.totalLoadTime = totalLoadTime;
            return this;
        }

        /**
         * Set the current number of entries in the cache.
         *
         * @param currentSize the current cache size
         * @return this builder for method chaining
         */
        public Builder currentSize(int currentSize) {
            this.currentSize = currentSize;
            return this;
        }

        /**
         * Set the maximum number of entries the cache can hold.
         *
         * @param maxSize the maximum cache size
         * @return this builder for method chaining
         */
        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Build and return the immutable CacheStats instance.
         *
         * @return the configured CacheStats instance
         */
        public CacheStats build() {
            return new CacheStats(this);
        }
    }
}
