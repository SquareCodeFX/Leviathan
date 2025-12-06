package de.feelix.leviathan.command.pagination.cache;

import java.util.Objects;

/**
 * Immutable value object for cache statistics.
 */
public final class CacheStats {

    private final long hitCount;
    private final long missCount;
    private final long evictionCount;
    private final long loadSuccessCount;
    private final long loadFailureCount;
    private final long totalLoadTime;
    private final int currentSize;
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

    public static Builder builder() {
        return new Builder();
    }

    public static CacheStats empty() {
        return builder().build();
    }

    // Calculated metrics
    public double getHitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    public double getMissRate() {
        return 1.0 - getHitRate();
    }

    public long getRequestCount() {
        return hitCount + missCount;
    }

    public double getAverageLoadTime() {
        long totalLoads = loadSuccessCount + loadFailureCount;
        return totalLoads == 0 ? 0.0 : (double) totalLoadTime / totalLoads;
    }

    public double getLoadSuccessRate() {
        long totalLoads = loadSuccessCount + loadFailureCount;
        return totalLoads == 0 ? 1.0 : (double) loadSuccessCount / totalLoads;
    }

    public double getUtilization() {
        return maxSize == 0 ? 0.0 : (double) currentSize / maxSize;
    }

    // Getters
    public long getHitCount() {
        return hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public long getEvictionCount() {
        return evictionCount;
    }

    public long getLoadSuccessCount() {
        return loadSuccessCount;
    }

    public long getLoadFailureCount() {
        return loadFailureCount;
    }

    public long getTotalLoadTime() {
        return totalLoadTime;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Creates a new stats object with incremented hit count.
     */
    public CacheStats withHit() {
        return toBuilder().hitCount(hitCount + 1).build();
    }

    /**
     * Creates a new stats object with incremented miss count.
     */
    public CacheStats withMiss() {
        return toBuilder().missCount(missCount + 1).build();
    }

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

        public Builder hitCount(long hitCount) {
            this.hitCount = hitCount;
            return this;
        }

        public Builder missCount(long missCount) {
            this.missCount = missCount;
            return this;
        }

        public Builder evictionCount(long evictionCount) {
            this.evictionCount = evictionCount;
            return this;
        }

        public Builder loadSuccessCount(long loadSuccessCount) {
            this.loadSuccessCount = loadSuccessCount;
            return this;
        }

        public Builder loadFailureCount(long loadFailureCount) {
            this.loadFailureCount = loadFailureCount;
            return this;
        }

        public Builder totalLoadTime(long totalLoadTime) {
            this.totalLoadTime = totalLoadTime;
            return this;
        }

        public Builder currentSize(int currentSize) {
            this.currentSize = currentSize;
            return this;
        }

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public CacheStats build() {
            return new CacheStats(this);
        }
    }
}
