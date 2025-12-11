package de.feelix.leviathan.command.completion;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Thread-safe caching layer for tab completions.
 * <p>
 * Use this to cache expensive completion lookups (database queries, API calls, etc.)
 * and prevent server lag during tab completion.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a cache with 5-minute TTL
 * CompletionCache cache = CompletionCache.withTTL(5, TimeUnit.MINUTES);
 *
 * // In your completion provider:
 * ArgContext.builder()
 *     .completionsDynamic(ctx -> cache.getOrCompute("players",
 *         () -> fetchPlayersFromDatabase()))
 *     .build();
 * }</pre>
 */
public final class CompletionCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSize;

    /**
     * A cached entry with expiration time.
     */
    private static final class CacheEntry {
        final List<String> completions;
        final long expiresAt;

        CacheEntry(List<String> completions, long expiresAt) {
            this.completions = completions;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private CompletionCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
    }

    /**
     * Create a cache with the specified TTL and no size limit.
     *
     * @param ttl  time-to-live value
     * @param unit time unit for TTL
     * @return a new CompletionCache instance
     */
    public static @NotNull CompletionCache withTTL(long ttl, @NotNull TimeUnit unit) {
        Preconditions.checkNotNull(unit, "unit");
        if (ttl <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        return new CompletionCache(unit.toMillis(ttl), Integer.MAX_VALUE);
    }

    /**
     * Create a cache with the specified TTL and maximum size.
     *
     * @param ttl     time-to-live value
     * @param unit    time unit for TTL
     * @param maxSize maximum number of cached entries
     * @return a new CompletionCache instance
     */
    public static @NotNull CompletionCache withTTLAndSize(long ttl, @NotNull TimeUnit unit, int maxSize) {
        Preconditions.checkNotNull(unit, "unit");
        if (ttl <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        return new CompletionCache(unit.toMillis(ttl), maxSize);
    }

    /**
     * Create a cache with default settings (5 minutes TTL, max 100 entries).
     *
     * @return a new CompletionCache instance with default settings
     */
    public static @NotNull CompletionCache createDefault() {
        return new CompletionCache(5 * 60 * 1000L, 100);
    }

    /**
     * Get completions from cache, or compute and cache them if missing/expired.
     *
     * @param key      cache key
     * @param supplier supplier to compute completions if not cached
     * @return the cached or computed completions
     */
    public @NotNull List<String> getOrCompute(@NotNull String key, @NotNull Supplier<List<String>> supplier) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(supplier, "supplier");

        CacheEntry entry = cache.get(key);

        // Check if entry exists and is not expired
        if (entry != null && !entry.isExpired()) {
            return new ArrayList<>(entry.completions);
        }

        // Compute new completions
        List<String> completions = supplier.get();
        if (completions == null) {
            completions = Collections.emptyList();
        }

        // Store in cache
        put(key, completions);

        return new ArrayList<>(completions);
    }

    /**
     * Get completions from cache if present and not expired.
     *
     * @param key cache key
     * @return the cached completions, or null if not cached or expired
     */
    public @Nullable List<String> get(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");

        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return new ArrayList<>(entry.completions);
    }

    /**
     * Store completions in the cache.
     *
     * @param key         cache key
     * @param completions completions to cache
     */
    public void put(@NotNull String key, @NotNull List<String> completions) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(completions, "completions");

        // Enforce max size by removing expired entries first
        if (cache.size() >= maxSize) {
            evictExpired();
        }

        // If still at max size, remove oldest entry
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        long expiresAt = System.currentTimeMillis() + ttlMillis;
        cache.put(key, new CacheEntry(new ArrayList<>(completions), expiresAt));
    }

    /**
     * Invalidate a specific cache entry.
     *
     * @param key cache key to invalidate
     */
    public void invalidate(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        cache.remove(key);
    }

    /**
     * Invalidate all entries matching a key prefix.
     *
     * @param prefix key prefix to match
     */
    public void invalidateByPrefix(@NotNull String prefix) {
        Preconditions.checkNotNull(prefix, "prefix");
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Clear all cached entries.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get the current number of cached entries.
     *
     * @return cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Check if a key is cached and not expired.
     *
     * @param key cache key
     * @return true if cached and not expired
     */
    public boolean isCached(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Remove all expired entries from the cache.
     *
     * @return number of entries removed
     */
    public int evictExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Remove the oldest entry from the cache.
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestExpiry = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            if (e.getValue().expiresAt < oldestExpiry) {
                oldestExpiry = e.getValue().expiresAt;
                oldestKey = e.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    /**
     * Get cache statistics.
     *
     * @return a map containing cache statistics
     */
    public @NotNull Map<String, Object> getStats() {
        evictExpired(); // Clean up first
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("size", cache.size());
        stats.put("maxSize", maxSize);
        stats.put("ttlMillis", ttlMillis);
        return stats;
    }

    // ==================== Static Helper Methods ====================

    /**
     * Create a caching completion provider that wraps another provider.
     * Useful for wrapping expensive dynamic completion providers.
     *
     * @param cache    the cache to use
     * @param key      cache key
     * @param provider the original provider to wrap
     * @return a caching completion provider
     */
    public static @NotNull ArgContext.DynamicCompletionProvider cached(
        @NotNull CompletionCache cache,
        @NotNull String key,
        @NotNull ArgContext.DynamicCompletionProvider provider) {
        Preconditions.checkNotNull(cache, "cache");
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(provider, "provider");
        return ctx -> cache.getOrCompute(key, () -> provider.provide(ctx));
    }

    /**
     * Create a caching completion provider with a dynamic key based on sender.
     * Each sender gets their own cache entry.
     *
     * @param cache    the cache to use
     * @param keyBase  base cache key
     * @param provider the original provider to wrap
     * @return a caching completion provider with per-sender keys
     */
    public static @NotNull ArgContext.DynamicCompletionProvider cachedPerSender(
        @NotNull CompletionCache cache,
        @NotNull String keyBase,
        @NotNull ArgContext.DynamicCompletionProvider provider) {
        Preconditions.checkNotNull(cache, "cache");
        Preconditions.checkNotNull(keyBase, "keyBase");
        Preconditions.checkNotNull(provider, "provider");
        return ctx -> {
            String key = keyBase + ":" + ctx.sender().getName();
            return cache.getOrCompute(key, () -> provider.provide(ctx));
        };
    }
}
