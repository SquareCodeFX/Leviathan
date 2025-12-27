package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.LazyCleanupProvider;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Caching system for expensive command execution results.
 * <p>
 * This cache stores the results of expensive operations to avoid redundant computation
 * when the same command with the same arguments is executed multiple times.
 * <p>
 * Features:
 * <ul>
 *   <li>Per-command result caching</li>
 *   <li>Per-sender result caching (for personalized results)</li>
 *   <li>Global result caching (for shared results)</li>
 *   <li>Configurable TTL per cache entry</li>
 *   <li>Automatic cache invalidation</li>
 *   <li>Statistics tracking</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a result cache
 * ResultCache cache = ResultCache.create(5, TimeUnit.MINUTES);
 *
 * // Cache command results
 * Object result = cache.getOrCompute(
 *     "mycommand",
 *     context,
 *     sender,
 *     () -> expensiveOperation()
 * );
 *
 * // With cache key builder
 * Object result = cache.getOrCompute(
 *     CacheKeyBuilder.forCommand("stats")
 *         .withSender(sender)
 *         .withArg("type", "weekly")
 *         .build(),
 *     () -> computeStats("weekly")
 * );
 * }</pre>
 */
public final class ResultCache {

    private final Map<String, CacheEntry<?>> cache;
    private final long defaultTtlMillis;
    private final int maxSize;
    private final LazyCleanupProvider cleanupProvider;

    // Statistics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    /**
     * Default TTL for cache entries (5 minutes).
     */
    public static final long DEFAULT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    /**
     * Default maximum cache size.
     */
    public static final int DEFAULT_MAX_SIZE = 500;

    private ResultCache(long ttlMillis, int maxSize) {
        this.cache = new ConcurrentHashMap<>();
        this.defaultTtlMillis = ttlMillis;
        this.maxSize = maxSize;
        this.cleanupProvider = LazyCleanupProvider.withInterval(50);
    }

    /**
     * Create a result cache with default settings.
     *
     * @return a new ResultCache instance
     */
    public static @NotNull ResultCache create() {
        return new ResultCache(DEFAULT_TTL_MILLIS, DEFAULT_MAX_SIZE);
    }

    /**
     * Create a result cache with custom TTL.
     *
     * @param ttl  time-to-live value
     * @param unit time unit
     * @return a new ResultCache instance
     */
    public static @NotNull ResultCache create(long ttl, @NotNull TimeUnit unit) {
        Preconditions.checkNotNull(unit, "unit");
        if (ttl <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        return new ResultCache(unit.toMillis(ttl), DEFAULT_MAX_SIZE);
    }

    /**
     * Create a result cache with custom TTL and max size.
     *
     * @param ttl     time-to-live value
     * @param unit    time unit
     * @param maxSize maximum cache entries
     * @return a new ResultCache instance
     */
    public static @NotNull ResultCache create(long ttl, @NotNull TimeUnit unit, int maxSize) {
        Preconditions.checkNotNull(unit, "unit");
        if (ttl <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        return new ResultCache(unit.toMillis(ttl), maxSize);
    }

    /**
     * Get or compute a cached result using a simple key.
     *
     * @param key      the cache key
     * @param supplier the supplier to compute the value if not cached
     * @param <T>      the result type
     * @return the cached or computed result
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getOrCompute(@NotNull String key, @NotNull Supplier<T> supplier) {
        return getOrCompute(key, supplier, defaultTtlMillis);
    }

    /**
     * Get or compute a cached result with custom TTL.
     *
     * @param key         the cache key
     * @param supplier    the supplier to compute the value if not cached
     * @param ttlMillis   custom TTL in milliseconds
     * @param <T>         the result type
     * @return the cached or computed result
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getOrCompute(@NotNull String key, @NotNull Supplier<T> supplier, long ttlMillis) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(supplier, "supplier");

        cleanupProvider.maybeCleanup(this::evictExpired);

        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return (T) entry.value;
        }

        // Compute new value
        misses.incrementAndGet();
        T value = supplier.get();

        if (value != null) {
            ensureCapacity();
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
        }

        return value;
    }

    /**
     * Get or compute a cached result for a specific command execution.
     *
     * @param commandName the command name
     * @param context     the command context
     * @param sender      the command sender
     * @param supplier    the supplier to compute the value
     * @param <T>         the result type
     * @return the cached or computed result
     */
    public <T> @Nullable T getOrCompute(@NotNull String commandName,
                                         @NotNull CommandContext context,
                                         @NotNull CommandSender sender,
                                         @NotNull Supplier<T> supplier) {
        String key = buildKey(commandName, context, sender);
        return getOrCompute(key, supplier);
    }

    /**
     * Get or compute a cached result for a command (sender-agnostic).
     *
     * @param commandName the command name
     * @param context     the command context
     * @param supplier    the supplier to compute the value
     * @param <T>         the result type
     * @return the cached or computed result
     */
    public <T> @Nullable T getOrComputeGlobal(@NotNull String commandName,
                                               @NotNull CommandContext context,
                                               @NotNull Supplier<T> supplier) {
        String key = buildGlobalKey(commandName, context);
        return getOrCompute(key, supplier);
    }

    /**
     * Put a value directly into the cache.
     *
     * @param key   the cache key
     * @param value the value to cache
     * @param <T>   the value type
     */
    public <T> void put(@NotNull String key, @NotNull T value) {
        put(key, value, defaultTtlMillis);
    }

    /**
     * Put a value with custom TTL.
     *
     * @param key       the cache key
     * @param value     the value to cache
     * @param ttlMillis custom TTL in milliseconds
     * @param <T>       the value type
     */
    public <T> void put(@NotNull String key, @NotNull T value, long ttlMillis) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(value, "value");

        ensureCapacity();
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * Get a cached value if present.
     *
     * @param key the cache key
     * @param <T> the value type
     * @return the cached value, or null if not present/expired
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return (T) entry.value;
        }
        return null;
    }

    /**
     * Check if a key is cached.
     *
     * @param key the cache key
     * @return true if cached and not expired
     */
    public boolean isCached(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        CacheEntry<?> entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Invalidate a specific cache entry.
     *
     * @param key the cache key
     */
    public void invalidate(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        if (cache.remove(key) != null) {
            evictions.incrementAndGet();
        }
    }

    /**
     * Invalidate all entries for a command.
     *
     * @param commandName the command name
     */
    public void invalidateCommand(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        String prefix = commandName + ":";
        int removed = 0;
        Iterator<String> it = cache.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
                removed++;
            }
        }
        evictions.addAndGet(removed);
    }

    /**
     * Invalidate all entries for a specific sender.
     *
     * @param sender the sender
     */
    public void invalidateForSender(@NotNull CommandSender sender) {
        Preconditions.checkNotNull(sender, "sender");
        String senderId = getSenderId(sender);
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry<?>>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().contains(":" + senderId + ":")) {
                it.remove();
                removed++;
            }
        }
        evictions.addAndGet(removed);
    }

    /**
     * Invalidate entries matching a predicate.
     *
     * @param predicate the predicate to test keys
     */
    public void invalidateIf(@NotNull java.util.function.Predicate<String> predicate) {
        Preconditions.checkNotNull(predicate, "predicate");
        int removed = 0;
        Iterator<String> it = cache.keySet().iterator();
        while (it.hasNext()) {
            if (predicate.test(it.next())) {
                it.remove();
                removed++;
            }
        }
        evictions.addAndGet(removed);
    }

    /**
     * Clear all cached entries.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        evictions.addAndGet(size);
    }

    /**
     * Get the current cache size.
     *
     * @return the number of entries
     */
    public int size() {
        return cache.size();
    }

    /**
     * Evict expired entries.
     *
     * @return the number of entries evicted
     */
    public int evictExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry<?>>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        evictions.addAndGet(removed);
        return removed;
    }

    /**
     * Get cache statistics.
     *
     * @return statistics snapshot
     */
    public @NotNull CacheStats getStats() {
        return new CacheStats(
            cache.size(),
            maxSize,
            hits.get(),
            misses.get(),
            evictions.get(),
            defaultTtlMillis
        );
    }

    /**
     * Reset statistics counters.
     */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }

    /**
     * Get the cache hit ratio.
     *
     * @return hit ratio between 0.0 and 1.0
     */
    public double getHitRatio() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        if (total == 0) return 1.0;
        return (double) totalHits / total;
    }

    // ==================== Cache Key Building ====================

    /**
     * Build a cache key for a command execution.
     */
    private String buildKey(String commandName, CommandContext context, CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append(commandName).append(":");
        sb.append(getSenderId(sender)).append(":");
        sb.append(contextHash(context));
        return sb.toString();
    }

    /**
     * Build a global cache key (sender-agnostic).
     */
    private String buildGlobalKey(String commandName, CommandContext context) {
        return commandName + ":global:" + contextHash(context);
    }

    /**
     * Get a unique identifier for a sender.
     */
    private String getSenderId(CommandSender sender) {
        if (sender instanceof Player) {
            return ((Player) sender).getUniqueId().toString();
        }
        return sender.getName();
    }

    /**
     * Compute a hash for the context arguments.
     */
    private int contextHash(CommandContext context) {
        return Arrays.hashCode(context.raw());
    }

    /**
     * Ensure capacity by evicting if necessary.
     */
    private void ensureCapacity() {
        if (cache.size() >= maxSize) {
            evictExpired();
            if (cache.size() >= maxSize) {
                evictOldest();
            }
        }
    }

    /**
     * Evict the oldest entry.
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getValue().expiresAt < oldestTime) {
                oldestTime = entry.getValue().expiresAt;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            evictions.incrementAndGet();
        }
    }

    // ==================== Cache Key Builder ====================

    /**
     * Fluent builder for cache keys.
     */
    public static final class CacheKeyBuilder {
        private final StringBuilder sb = new StringBuilder();
        private boolean hasContent = false;

        private CacheKeyBuilder(String commandName) {
            sb.append(commandName);
        }

        /**
         * Start building a cache key for a command.
         *
         * @param commandName the command name
         * @return a new builder
         */
        public static @NotNull CacheKeyBuilder forCommand(@NotNull String commandName) {
            Preconditions.checkNotNull(commandName, "commandName");
            return new CacheKeyBuilder(commandName);
        }

        /**
         * Add sender to the key.
         *
         * @param sender the sender
         * @return this for chaining
         */
        public @NotNull CacheKeyBuilder withSender(@NotNull CommandSender sender) {
            Preconditions.checkNotNull(sender, "sender");
            sb.append(":");
            if (sender instanceof Player) {
                sb.append(((Player) sender).getUniqueId());
            } else {
                sb.append(sender.getName());
            }
            hasContent = true;
            return this;
        }

        /**
         * Add a named argument value to the key.
         *
         * @param name  the argument name
         * @param value the argument value
         * @return this for chaining
         */
        public @NotNull CacheKeyBuilder withArg(@NotNull String name, @Nullable Object value) {
            Preconditions.checkNotNull(name, "name");
            sb.append(":").append(name).append("=").append(value);
            hasContent = true;
            return this;
        }

        /**
         * Add multiple argument values to the key.
         *
         * @param args map of argument names to values
         * @return this for chaining
         */
        public @NotNull CacheKeyBuilder withArgs(@NotNull Map<String, Object> args) {
            Preconditions.checkNotNull(args, "args");
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                withArg(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Add a custom suffix to the key.
         *
         * @param suffix the suffix
         * @return this for chaining
         */
        public @NotNull CacheKeyBuilder withSuffix(@NotNull String suffix) {
            Preconditions.checkNotNull(suffix, "suffix");
            sb.append(":").append(suffix);
            hasContent = true;
            return this;
        }

        /**
         * Mark this as a global cache entry (not sender-specific).
         *
         * @return this for chaining
         */
        public @NotNull CacheKeyBuilder global() {
            sb.append(":global");
            hasContent = true;
            return this;
        }

        /**
         * Build the cache key.
         *
         * @return the cache key string
         */
        public @NotNull String build() {
            return sb.toString();
        }
    }

    // ==================== Internal Classes ====================

    /**
     * Internal cache entry.
     */
    private static final class CacheEntry<T> {
        final T value;
        final long expiresAt;

        CacheEntry(T value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Cache statistics snapshot.
     */
    public static final class CacheStats {
        private final int size;
        private final int maxSize;
        private final long hits;
        private final long misses;
        private final long evictions;
        private final long defaultTtlMillis;

        CacheStats(int size, int maxSize, long hits, long misses, long evictions, long defaultTtlMillis) {
            this.size = size;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.defaultTtlMillis = defaultTtlMillis;
        }

        public int getSize() { return size; }
        public int getMaxSize() { return maxSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getEvictions() { return evictions; }
        public long getDefaultTtlMillis() { return defaultTtlMillis; }

        public double getHitRatio() {
            long total = hits + misses;
            if (total == 0) return 1.0;
            return (double) hits / total;
        }

        public double getUtilization() {
            return (double) size / maxSize;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d/%d, hits=%d, misses=%d, evictions=%d, hitRatio=%.2f}",
                size, maxSize, hits, misses, evictions, getHitRatio()
            );
        }
    }
}
