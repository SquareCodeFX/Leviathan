package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central management class for all performance optimization features.
 * <p>
 * This class provides:
 * <ul>
 *   <li>Singleton access to shared performance components</li>
 *   <li>Pre-configured object pools for common types</li>
 *   <li>Global result and argument caches</li>
 *   <li>Parallel parser management</li>
 *   <li>Combined statistics and monitoring</li>
 *   <li>Graceful shutdown handling</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Get the global instance
 * PerformanceManager perf = PerformanceManager.getInstance();
 *
 * // Use pre-configured pools
 * StringBuilder sb = perf.getStringBuilderPool().borrow();
 * try {
 *     sb.append("Hello");
 *     return sb.toString();
 * } finally {
 *     perf.getStringBuilderPool().release(sb);
 * }
 *
 * // Get combined statistics
 * PerformanceStats stats = perf.getStats();
 * System.out.println("Cache hit ratio: " + stats.getCombinedHitRatio());
 *
 * // Shutdown on plugin disable
 * perf.shutdown();
 * }</pre>
 */
public final class PerformanceManager {

    // Singleton instance
    private static volatile PerformanceManager instance;
    private static final Object LOCK = new Object();

    // Configuration
    private final PerformanceConfig config;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // Pre-configured object pools
    private final ObjectPool<StringBuilder> stringBuilderPool;
    private final ObjectPool<ArrayList<?>> arrayListPool;
    private final ObjectPool<HashMap<?, ?>> hashMapPool;

    // Caches
    private final ResultCache resultCache;

    // Parallel parser
    private final ParallelParser parallelParser;

    private PerformanceManager(PerformanceConfig config) {
        this.config = config;

        // Initialize object pools
        this.stringBuilderPool = ObjectPool.create(
            StringBuilder::new,
            sb -> sb.setLength(0),
            config.stringBuilderPoolSize
        );

        this.arrayListPool = ObjectPool.create(
            ArrayList::new,
            list -> ((ArrayList<?>) list).clear(),
            config.arrayListPoolSize
        );

        this.hashMapPool = ObjectPool.create(
            HashMap::new,
            map -> ((HashMap<?, ?>) map).clear(),
            config.hashMapPoolSize
        );

        // Initialize caches
        this.resultCache = ResultCache.create(
            config.resultCacheTtlMinutes,
            TimeUnit.MINUTES,
            config.resultCacheMaxSize
        );

        // Initialize parallel parser
        this.parallelParser = ParallelParser.builder()
            .threads(config.parallelParserThreads)
            .parallelThreshold(config.parallelThreshold)
            .timeout(config.parseTimeoutMillis, TimeUnit.MILLISECONDS)
            .build();

        // Prewarm pools if configured
        if (config.prewarmPools) {
            prewarmPools();
        }
    }

    /**
     * Get the global PerformanceManager instance.
     * <p>
     * Creates the instance with default configuration if not already initialized.
     *
     * @return the global instance
     */
    public static @NotNull PerformanceManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new PerformanceManager(PerformanceConfig.defaults());
                }
            }
        }
        return instance;
    }

    /**
     * Initialize with custom configuration.
     * <p>
     * Must be called before any other access to getInstance().
     *
     * @param config the configuration
     * @return the initialized instance
     * @throws IllegalStateException if already initialized
     */
    public static @NotNull PerformanceManager initialize(@NotNull PerformanceConfig config) {
        Preconditions.checkNotNull(config, "config");
        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException("PerformanceManager already initialized");
            }
            instance = new PerformanceManager(config);
            return instance;
        }
    }

    /**
     * Reset the global instance (primarily for testing).
     */
    public static void reset() {
        synchronized (LOCK) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
            }
        }
    }

    // ==================== Object Pools ====================

    /**
     * Get the StringBuilder pool.
     * <p>
     * Use for building strings in hot paths.
     *
     * @return the StringBuilder pool
     */
    public @NotNull ObjectPool<StringBuilder> getStringBuilderPool() {
        return stringBuilderPool;
    }

    /**
     * Get the ArrayList pool.
     * <p>
     * Use for temporary lists in hot paths.
     *
     * @return the ArrayList pool
     */
    @SuppressWarnings("unchecked")
    public <T> @NotNull ObjectPool<ArrayList<T>> getArrayListPool() {
        return (ObjectPool<ArrayList<T>>) (ObjectPool<?>) arrayListPool;
    }

    /**
     * Get the HashMap pool.
     * <p>
     * Use for temporary maps in hot paths.
     *
     * @return the HashMap pool
     */
    @SuppressWarnings("unchecked")
    public <K, V> @NotNull ObjectPool<HashMap<K, V>> getHashMapPool() {
        return (ObjectPool<HashMap<K, V>>) (ObjectPool<?>) hashMapPool;
    }

    /**
     * Build a string using a pooled StringBuilder.
     *
     * @param builder the function to build the string
     * @return the built string
     */
    public @NotNull String buildString(@NotNull java.util.function.Consumer<StringBuilder> builder) {
        Preconditions.checkNotNull(builder, "builder");
        return stringBuilderPool.withPooled(sb -> {
            builder.accept(sb);
            return sb.toString();
        });
    }

    // ==================== Caches ====================

    /**
     * Get the result cache.
     *
     * @return the result cache
     */
    public @NotNull ResultCache getResultCache() {
        return resultCache;
    }

    /**
     * Clear all caches.
     */
    public void clearCaches() {
        resultCache.clear();
        ArgumentCache.clearAll();
    }

    // ==================== Parallel Parser ====================

    /**
     * Get the parallel parser.
     *
     * @return the parallel parser
     */
    public @NotNull ParallelParser getParallelParser() {
        return parallelParser;
    }

    // ==================== Configuration ====================

    /**
     * Check if performance optimizations are enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Enable or disable performance optimizations.
     *
     * @param enable true to enable
     */
    public void setEnabled(boolean enable) {
        enabled.set(enable);
        ArgumentCache.setEnabled(enable);
    }

    /**
     * Get the current configuration.
     *
     * @return the configuration
     */
    public @NotNull PerformanceConfig getConfig() {
        return config;
    }

    // ==================== Statistics ====================

    /**
     * Get combined performance statistics.
     *
     * @return statistics snapshot
     */
    public @NotNull PerformanceStats getStats() {
        return new PerformanceStats(
            stringBuilderPool.getStats(),
            arrayListPool.getStats(),
            hashMapPool.getStats(),
            resultCache.getStats(),
            ArgumentCache.getStats(),
            parallelParser.getStats(),
            CommandPrecompiler.getStats(),
            enabled.get()
        );
    }

    /**
     * Reset all statistics counters.
     */
    public void resetStats() {
        stringBuilderPool.resetStats();
        arrayListPool.resetStats();
        hashMapPool.resetStats();
        resultCache.resetStats();
        ArgumentCache.resetStats();
        parallelParser.resetStats();
        CommandPrecompiler.resetStats();
    }

    // ==================== Lifecycle ====================

    /**
     * Prewarm object pools.
     */
    public void prewarmPools() {
        stringBuilderPool.prewarm(config.stringBuilderPoolSize / 2);
        arrayListPool.prewarm(config.arrayListPoolSize / 2);
        hashMapPool.prewarm(config.hashMapPoolSize / 2);
    }

    /**
     * Shutdown the performance manager.
     * <p>
     * Releases resources and stops background threads.
     * Should be called on plugin disable.
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            parallelParser.shutdown();
            stringBuilderPool.clear();
            arrayListPool.clear();
            hashMapPool.clear();
            resultCache.clear();
            ArgumentCache.clearAll();
            CommandPrecompiler.clearAll();
        }
    }

    /**
     * Check if the manager is shut down.
     *
     * @return true if shut down
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    // ==================== Configuration Class ====================

    /**
     * Configuration for PerformanceManager.
     */
    public static final class PerformanceConfig {
        int stringBuilderPoolSize = 100;
        int arrayListPoolSize = 50;
        int hashMapPoolSize = 30;
        int resultCacheMaxSize = 500;
        int resultCacheTtlMinutes = 5;
        int parallelParserThreads = Runtime.getRuntime().availableProcessors();
        int parallelThreshold = 3;
        long parseTimeoutMillis = 5000;
        boolean prewarmPools = true;

        private PerformanceConfig() {}

        /**
         * Get default configuration.
         *
         * @return default config
         */
        public static @NotNull PerformanceConfig defaults() {
            return new PerformanceConfig();
        }

        /**
         * Create a builder for custom configuration.
         *
         * @return a new builder
         */
        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private final PerformanceConfig config = new PerformanceConfig();

            public Builder stringBuilderPoolSize(int size) {
                config.stringBuilderPoolSize = size;
                return this;
            }

            public Builder arrayListPoolSize(int size) {
                config.arrayListPoolSize = size;
                return this;
            }

            public Builder hashMapPoolSize(int size) {
                config.hashMapPoolSize = size;
                return this;
            }

            public Builder resultCacheMaxSize(int size) {
                config.resultCacheMaxSize = size;
                return this;
            }

            public Builder resultCacheTtlMinutes(int minutes) {
                config.resultCacheTtlMinutes = minutes;
                return this;
            }

            public Builder parallelParserThreads(int threads) {
                config.parallelParserThreads = threads;
                return this;
            }

            public Builder parallelThreshold(int threshold) {
                config.parallelThreshold = threshold;
                return this;
            }

            public Builder parseTimeoutMillis(long timeout) {
                config.parseTimeoutMillis = timeout;
                return this;
            }

            public Builder prewarmPools(boolean prewarm) {
                config.prewarmPools = prewarm;
                return this;
            }

            public PerformanceConfig build() {
                return config;
            }
        }
    }

    // ==================== Statistics Class ====================

    /**
     * Combined performance statistics snapshot.
     */
    public static final class PerformanceStats {
        private final ObjectPool.PoolStats stringBuilderPoolStats;
        private final ObjectPool.PoolStats arrayListPoolStats;
        private final ObjectPool.PoolStats hashMapPoolStats;
        private final ResultCache.CacheStats resultCacheStats;
        private final ArgumentCache.CacheStats argumentCacheStats;
        private final ParallelParser.ParserStats parallelParserStats;
        private final CommandPrecompiler.CompilerStats compilerStats;
        private final boolean enabled;

        PerformanceStats(ObjectPool.PoolStats sbStats,
                         ObjectPool.PoolStats alStats,
                         ObjectPool.PoolStats hmStats,
                         ResultCache.CacheStats resultStats,
                         ArgumentCache.CacheStats argStats,
                         ParallelParser.ParserStats parserStats,
                         CommandPrecompiler.CompilerStats compilerStats,
                         boolean enabled) {
            this.stringBuilderPoolStats = sbStats;
            this.arrayListPoolStats = alStats;
            this.hashMapPoolStats = hmStats;
            this.resultCacheStats = resultStats;
            this.argumentCacheStats = argStats;
            this.parallelParserStats = parserStats;
            this.compilerStats = compilerStats;
            this.enabled = enabled;
        }

        public ObjectPool.PoolStats getStringBuilderPoolStats() { return stringBuilderPoolStats; }
        public ObjectPool.PoolStats getArrayListPoolStats() { return arrayListPoolStats; }
        public ObjectPool.PoolStats getHashMapPoolStats() { return hashMapPoolStats; }
        public ResultCache.CacheStats getResultCacheStats() { return resultCacheStats; }
        public ArgumentCache.CacheStats getArgumentCacheStats() { return argumentCacheStats; }
        public ParallelParser.ParserStats getParallelParserStats() { return parallelParserStats; }
        public CommandPrecompiler.CompilerStats getCompilerStats() { return compilerStats; }
        public boolean isEnabled() { return enabled; }

        /**
         * Get combined pool hit ratio.
         *
         * @return average hit ratio across all pools
         */
        public double getCombinedPoolHitRatio() {
            double sum = stringBuilderPoolStats.getHitRatio()
                       + arrayListPoolStats.getHitRatio()
                       + hashMapPoolStats.getHitRatio();
            return sum / 3.0;
        }

        /**
         * Get combined cache hit ratio.
         *
         * @return average hit ratio across all caches
         */
        public double getCombinedCacheHitRatio() {
            double sum = resultCacheStats.getHitRatio() + argumentCacheStats.getHitRatio();
            return sum / 2.0;
        }

        /**
         * Get total pool borrows across all pools.
         *
         * @return total borrows
         */
        public long getTotalPoolBorrows() {
            return stringBuilderPoolStats.getBorrowCount()
                 + arrayListPoolStats.getBorrowCount()
                 + hashMapPoolStats.getBorrowCount();
        }

        /**
         * Get total cache hits across all caches.
         *
         * @return total hits
         */
        public long getTotalCacheHits() {
            return resultCacheStats.getHits() + argumentCacheStats.getHits();
        }

        @Override
        public String toString() {
            return "PerformanceStats{\n" +
                   "  enabled=" + enabled + ",\n" +
                   "  pools={\n" +
                   "    stringBuilder=" + stringBuilderPoolStats + ",\n" +
                   "    arrayList=" + arrayListPoolStats + ",\n" +
                   "    hashMap=" + hashMapPoolStats + "\n" +
                   "  },\n" +
                   "  caches={\n" +
                   "    result=" + resultCacheStats + ",\n" +
                   "    argument=" + argumentCacheStats + "\n" +
                   "  },\n" +
                   "  parallelParser=" + parallelParserStats + ",\n" +
                   "  compiler=" + compilerStats + "\n" +
                   "}";
        }

        /**
         * Get a compact summary string.
         *
         * @return compact summary
         */
        public String toSummary() {
            return String.format(
                "Performance[pools=%.0f%% hit, caches=%.0f%% hit, compiled=%d cmds]",
                getCombinedPoolHitRatio() * 100,
                getCombinedCacheHitRatio() * 100,
                compilerStats.getCachedCommands()
            );
        }
    }
}
