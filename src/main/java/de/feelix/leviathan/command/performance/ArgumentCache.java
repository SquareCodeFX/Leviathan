package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.LazyCleanupProvider;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * High-performance caching system for frequently-used argument values.
 * <p>
 * This cache reduces parsing overhead by storing commonly requested values
 * like player names, world names, materials, and custom argument results.
 * <p>
 * Features:
 * <ul>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Configurable TTL (time-to-live) per cache type</li>
 *   <li>Automatic lazy cleanup of expired entries</li>
 *   <li>Pre-built caches for common Bukkit types</li>
 *   <li>Custom cache creation for user-defined types</li>
 *   <li>Statistics tracking and monitoring</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Get cached player names
 * List<String> players = ArgumentCache.getPlayerNames();
 *
 * // Get or compute a custom value
 * String result = ArgumentCache.custom("myKey", () -> expensiveComputation(), 5, TimeUnit.MINUTES);
 *
 * // Create a typed cache for custom objects
 * TypedCache<MyObject> myCache = ArgumentCache.createTypedCache(1, TimeUnit.MINUTES);
 * myCache.put("key", myObject);
 * }</pre>
 */
public final class ArgumentCache {

    private ArgumentCache() {
        // Static utility class
    }

    // ==================== Global Configuration ====================

    private static volatile long defaultTtlMillis = TimeUnit.SECONDS.toMillis(30);
    private static volatile int maxCacheSize = 1000;
    private static volatile boolean enabled = true;

    // ==================== Internal Caches ====================

    // Player names cache
    private static final CachedValue<List<String>> playerNamesCache = new CachedValue<>(
        () -> {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            List<String> names = new ArrayList<>(players.size());
            for (Player p : players) {
                names.add(p.getName());
            }
            return Collections.unmodifiableList(names);
        },
        TimeUnit.SECONDS.toMillis(5) // Refresh every 5 seconds
    );

    // World names cache
    private static final CachedValue<List<String>> worldNamesCache = new CachedValue<>(
        () -> {
            List<World> worlds = Bukkit.getWorlds();
            List<String> names = new ArrayList<>(worlds.size());
            for (World w : worlds) {
                names.add(w.getName());
            }
            return Collections.unmodifiableList(names);
        },
        TimeUnit.SECONDS.toMillis(30) // Worlds change rarely
    );

    // Material names cache (static, never changes)
    private static final List<String> MATERIAL_NAMES;
    private static final Set<String> MATERIAL_NAMES_SET;

    static {
        Material[] materials = Material.values();
        List<String> names = new ArrayList<>(materials.length);
        Set<String> namesSet = new HashSet<>(materials.length);
        for (Material m : materials) {
            String name = m.name().toLowerCase(Locale.ROOT);
            names.add(name);
            namesSet.add(name);
        }
        MATERIAL_NAMES = Collections.unmodifiableList(names);
        MATERIAL_NAMES_SET = Collections.unmodifiableSet(namesSet);
    }

    // Generic key-value cache with TTL
    private static final Map<String, CacheEntry<?>> genericCache = new ConcurrentHashMap<>();
    private static final LazyCleanupProvider cleanupProvider = LazyCleanupProvider.withInterval(100);

    // Statistics
    private static final java.util.concurrent.atomic.AtomicLong hits = new java.util.concurrent.atomic.AtomicLong(0);
    private static final java.util.concurrent.atomic.AtomicLong misses = new java.util.concurrent.atomic.AtomicLong(0);

    // ==================== Player Names ====================

    /**
     * Get cached list of online player names.
     * <p>
     * This is refreshed every 5 seconds automatically.
     *
     * @return unmodifiable list of player names
     */
    public static @NotNull List<String> getPlayerNames() {
        if (!enabled) {
            return getPlayerNamesFresh();
        }
        return playerNamesCache.get();
    }

    /**
     * Get player names matching a prefix (for tab completion).
     *
     * @param prefix the prefix to match (case-insensitive)
     * @return list of matching player names
     */
    public static @NotNull List<String> getPlayerNamesStartingWith(@NotNull String prefix) {
        Preconditions.checkNotNull(prefix, "prefix");
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> all = getPlayerNames();
        List<String> result = new ArrayList<>();
        for (String name : all) {
            if (name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * Get fresh (non-cached) player names.
     *
     * @return list of current online player names
     */
    private static @NotNull List<String> getPlayerNamesFresh() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        List<String> names = new ArrayList<>(players.size());
        for (Player p : players) {
            names.add(p.getName());
        }
        return names;
    }

    /**
     * Invalidate the player names cache.
     * <p>
     * Call this when players join/leave if immediate accuracy is needed.
     */
    public static void invalidatePlayerNames() {
        playerNamesCache.invalidate();
    }

    // ==================== World Names ====================

    /**
     * Get cached list of world names.
     *
     * @return unmodifiable list of world names
     */
    public static @NotNull List<String> getWorldNames() {
        if (!enabled) {
            return getWorldNamesFresh();
        }
        return worldNamesCache.get();
    }

    /**
     * Get world names matching a prefix.
     *
     * @param prefix the prefix to match (case-insensitive)
     * @return list of matching world names
     */
    public static @NotNull List<String> getWorldNamesStartingWith(@NotNull String prefix) {
        Preconditions.checkNotNull(prefix, "prefix");
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> all = getWorldNames();
        List<String> result = new ArrayList<>();
        for (String name : all) {
            if (name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                result.add(name);
            }
        }
        return result;
    }

    private static @NotNull List<String> getWorldNamesFresh() {
        List<World> worlds = Bukkit.getWorlds();
        List<String> names = new ArrayList<>(worlds.size());
        for (World w : worlds) {
            names.add(w.getName());
        }
        return names;
    }

    /**
     * Invalidate the world names cache.
     */
    public static void invalidateWorldNames() {
        worldNamesCache.invalidate();
    }

    // ==================== Material Names ====================

    /**
     * Get all material names (lowercase).
     * <p>
     * This is a static list that never changes during runtime.
     *
     * @return unmodifiable list of material names
     */
    public static @NotNull List<String> getMaterialNames() {
        return MATERIAL_NAMES;
    }

    /**
     * Get material names matching a prefix.
     *
     * @param prefix the prefix to match (case-insensitive)
     * @return list of matching material names
     */
    public static @NotNull List<String> getMaterialNamesStartingWith(@NotNull String prefix) {
        Preconditions.checkNotNull(prefix, "prefix");
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String name : MATERIAL_NAMES) {
            if (name.startsWith(lowerPrefix)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * Check if a material name is valid.
     *
     * @param name the name to check (case-insensitive)
     * @return true if valid
     */
    public static boolean isValidMaterial(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return MATERIAL_NAMES_SET.contains(name.toLowerCase(Locale.ROOT));
    }

    // ==================== Generic Cache ====================

    /**
     * Get or compute a cached value with default TTL.
     *
     * @param key      the cache key
     * @param supplier the supplier to compute the value if not cached
     * @param <T>      the value type
     * @return the cached or computed value
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T getOrCompute(@NotNull String key, @NotNull Supplier<T> supplier) {
        return getOrCompute(key, supplier, defaultTtlMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Get or compute a cached value with custom TTL.
     *
     * @param key      the cache key
     * @param supplier the supplier to compute the value if not cached
     * @param ttl      time-to-live value
     * @param unit     time unit for TTL
     * @param <T>      the value type
     * @return the cached or computed value
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T getOrCompute(@NotNull String key, @NotNull Supplier<T> supplier,
                                                long ttl, @NotNull TimeUnit unit) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(supplier, "supplier");
        Preconditions.checkNotNull(unit, "unit");

        if (!enabled) {
            return supplier.get();
        }

        cleanupProvider.maybeCleanup(ArgumentCache::cleanupExpired);

        CacheEntry<?> entry = genericCache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return (T) entry.value;
        }

        // Compute new value
        misses.incrementAndGet();
        T value = supplier.get();
        if (value != null && genericCache.size() < maxCacheSize) {
            long expiresAt = System.currentTimeMillis() + unit.toMillis(ttl);
            genericCache.put(key, new CacheEntry<>(value, expiresAt));
        }
        return value;
    }

    /**
     * Put a value directly into the cache.
     *
     * @param key   the cache key
     * @param value the value to cache
     * @param ttl   time-to-live value
     * @param unit  time unit for TTL
     * @param <T>   the value type
     */
    public static <T> void put(@NotNull String key, @NotNull T value, long ttl, @NotNull TimeUnit unit) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(value, "value");
        Preconditions.checkNotNull(unit, "unit");

        if (!enabled || genericCache.size() >= maxCacheSize) {
            return;
        }

        cleanupProvider.maybeCleanup(ArgumentCache::cleanupExpired);
        long expiresAt = System.currentTimeMillis() + unit.toMillis(ttl);
        genericCache.put(key, new CacheEntry<>(value, expiresAt));
    }

    /**
     * Get a cached value if present and not expired.
     *
     * @param key the cache key
     * @param <T> the value type
     * @return the cached value, or null if not present/expired
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T get(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        CacheEntry<?> entry = genericCache.get(key);
        if (entry != null && !entry.isExpired()) {
            hits.incrementAndGet();
            return (T) entry.value;
        }
        return null;
    }

    /**
     * Check if a key is cached and not expired.
     *
     * @param key the cache key
     * @return true if cached and valid
     */
    public static boolean isCached(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        CacheEntry<?> entry = genericCache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Invalidate a specific cache entry.
     *
     * @param key the cache key to invalidate
     */
    public static void invalidate(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        genericCache.remove(key);
    }

    /**
     * Invalidate all entries matching a key prefix.
     *
     * @param prefix the key prefix to match
     */
    public static void invalidateByPrefix(@NotNull String prefix) {
        Preconditions.checkNotNull(prefix, "prefix");
        genericCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Clear all cached entries.
     */
    public static void clearAll() {
        genericCache.clear();
        playerNamesCache.invalidate();
        worldNamesCache.invalidate();
    }

    /**
     * Cleanup expired entries from the generic cache.
     *
     * @return number of entries removed
     */
    public static int cleanupExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry<?>>> it = genericCache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    // ==================== Typed Cache Factory ====================

    /**
     * Create a new typed cache for custom objects.
     *
     * @param ttl  time-to-live for entries
     * @param unit time unit for TTL
     * @param <T>  the value type
     * @return a new TypedCache instance
     */
    public static <T> @NotNull TypedCache<T> createTypedCache(long ttl, @NotNull TimeUnit unit) {
        return new TypedCache<>(unit.toMillis(ttl));
    }

    /**
     * Create a new typed cache with default TTL (30 seconds).
     *
     * @param <T> the value type
     * @return a new TypedCache instance
     */
    public static <T> @NotNull TypedCache<T> createTypedCache() {
        return new TypedCache<>(defaultTtlMillis);
    }

    // ==================== Configuration ====================

    /**
     * Set the default TTL for cache entries.
     *
     * @param ttl  time-to-live value
     * @param unit time unit
     */
    public static void setDefaultTtl(long ttl, @NotNull TimeUnit unit) {
        Preconditions.checkNotNull(unit, "unit");
        defaultTtlMillis = unit.toMillis(ttl);
    }

    /**
     * Set the maximum cache size.
     *
     * @param size the maximum number of entries
     */
    public static void setMaxCacheSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        maxCacheSize = size;
    }

    /**
     * Enable or disable caching globally.
     *
     * @param enable true to enable, false to disable
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
    }

    /**
     * Check if caching is enabled.
     *
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ==================== Statistics ====================

    /**
     * Get cache statistics.
     *
     * @return cache statistics snapshot
     */
    public static @NotNull CacheStats getStats() {
        return new CacheStats(
            genericCache.size(),
            maxCacheSize,
            hits.get(),
            misses.get(),
            enabled
        );
    }

    /**
     * Reset statistics counters.
     */
    public static void resetStats() {
        hits.set(0);
        misses.set(0);
    }

    /**
     * Get the cache hit ratio.
     *
     * @return hit ratio between 0.0 and 1.0
     */
    public static double getHitRatio() {
        long totalHits = hits.get();
        long totalMisses = misses.get();
        long total = totalHits + totalMisses;
        if (total == 0) return 1.0;
        return (double) totalHits / total;
    }

    // ==================== Internal Classes ====================

    /**
     * Internal cache entry with expiration.
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
     * A cached value with lazy refresh.
     */
    private static final class CachedValue<T> {
        private final Supplier<T> supplier;
        private final long ttlMillis;
        private volatile T value;
        private volatile long expiresAt;

        CachedValue(Supplier<T> supplier, long ttlMillis) {
            this.supplier = supplier;
            this.ttlMillis = ttlMillis;
            this.expiresAt = 0;
        }

        T get() {
            long now = System.currentTimeMillis();
            if (value == null || now > expiresAt) {
                synchronized (this) {
                    if (value == null || now > expiresAt) {
                        value = supplier.get();
                        expiresAt = now + ttlMillis;
                    }
                }
            }
            return value;
        }

        void invalidate() {
            expiresAt = 0;
        }
    }

    /**
     * A typed cache for specific object types.
     *
     * @param <T> the value type
     */
    public static final class TypedCache<T> {
        private final Map<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();
        private final long ttlMillis;
        private final LazyCleanupProvider cleanup = LazyCleanupProvider.withInterval(50);

        TypedCache(long ttlMillis) {
            this.ttlMillis = ttlMillis;
        }

        /**
         * Get or compute a value.
         *
         * @param key      the cache key
         * @param supplier supplier to compute if not cached
         * @return the cached or computed value
         */
        public @Nullable T getOrCompute(@NotNull String key, @NotNull Supplier<T> supplier) {
            Preconditions.checkNotNull(key, "key");
            Preconditions.checkNotNull(supplier, "supplier");

            cleanup.maybeCleanup(this::cleanupExpired);

            CacheEntry<T> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }

            T value = supplier.get();
            if (value != null) {
                cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
            }
            return value;
        }

        /**
         * Put a value in the cache.
         *
         * @param key   the cache key
         * @param value the value to cache
         */
        public void put(@NotNull String key, @NotNull T value) {
            Preconditions.checkNotNull(key, "key");
            Preconditions.checkNotNull(value, "value");
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMillis));
        }

        /**
         * Get a cached value.
         *
         * @param key the cache key
         * @return the cached value, or null if not present/expired
         */
        public @Nullable T get(@NotNull String key) {
            Preconditions.checkNotNull(key, "key");
            CacheEntry<T> entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.value;
            }
            return null;
        }

        /**
         * Invalidate a cache entry.
         *
         * @param key the key to invalidate
         */
        public void invalidate(@NotNull String key) {
            cache.remove(key);
        }

        /**
         * Clear all entries.
         */
        public void clear() {
            cache.clear();
        }

        /**
         * Get current cache size.
         *
         * @return the number of entries
         */
        public int size() {
            return cache.size();
        }

        private int cleanupExpired() {
            int removed = 0;
            Iterator<Map.Entry<String, CacheEntry<T>>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().isExpired()) {
                    it.remove();
                    removed++;
                }
            }
            return removed;
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
        private final boolean enabled;

        CacheStats(int size, int maxSize, long hits, long misses, boolean enabled) {
            this.size = size;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.enabled = enabled;
        }

        public int getSize() { return size; }
        public int getMaxSize() { return maxSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public boolean isEnabled() { return enabled; }

        public double getHitRatio() {
            long total = hits + misses;
            if (total == 0) return 1.0;
            return (double) hits / total;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d/%d, hits=%d, misses=%d, hitRatio=%.2f, enabled=%s}",
                size, maxSize, hits, misses, getHitRatio(), enabled
            );
        }
    }
}
