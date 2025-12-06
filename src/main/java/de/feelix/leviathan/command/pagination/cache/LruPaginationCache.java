package de.feelix.leviathan.command.pagination.cache;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.exception.CacheException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Thread-safe LRU (Least Recently Used) cache implementation with TTL (Time-To-Live) support.
 * <p>
 * Key characteristics:
 * <ul>
 *   <li>LRU eviction policy — least recently accessed entries are removed first when capacity is reached</li>
 *   <li>TTL-based expiration — entries automatically expire after a configurable duration</li>
 *   <li>Thread-safety — protects access with a {@link ReadWriteLock} for high read concurrency</li>
 *   <li>Asynchronous access — non-blocking variants provided via {@link CompletableFuture}</li>
 *   <li>Background maintenance — periodic cleanup of expired entries via a daemon scheduler</li>
 *   <li>Comprehensive statistics — hits, misses, evictions, load success/failure, aggregate load time</li>
 * </ul>
 * <p>
 * Notes on statistics and units:
 * <ul>
 *   <li>Internal load durations are measured in nanoseconds and converted to milliseconds in
 *   {@link #getStats()} so {@link CacheStats#getAverageLoadTime()} returns milliseconds.</li>
 *   <li>The scheduler thread is a daemon; it will not prevent JVM shutdown. Call {@link #shutdown()} to
 *   stop it early if you create many caches dynamically.</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * LruPaginationCache<String, List<Item>> cache = LruPaginationCache.<String, List<Item>>builder()
 *     .maxSize(500)
 *     .defaultTtl(Duration.ofMinutes(10))
 *     .build();
 *
 * // Store and retrieve values
 * cache.put("key", items);
 * Optional<List<Item>> cached = cache.get("key");
 *
 * // Compute if absent
 * List<Item> result = cache.getOrCompute("key", () -> loadExpensiveData());
 * }
 * </pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values
 * @see PaginationCache
 * @see CacheStats
 * @since 1.0.0
 */
public final class LruPaginationCache<K, V> implements PaginationCache<K, V> {

    /**
     * Maximum number of entries the cache can hold
     */
    private final int maxSize;
    /**
     * Default time-to-live duration for cache entries
     */
    private final Duration defaultTtl;
    /**
     * The underlying LRU map storing cache entries
     */
    private final Map<K, CacheEntry<V>> cache;
    /**
     * Lock for thread-safe read/write operations
     */
    private final ReadWriteLock lock;
    /**
     * Executor service for async operations
     */
    private final ExecutorService executor;
    /**
     * Scheduler for periodic cleanup of expired entries
     */
    private final ScheduledExecutorService cleanupScheduler;

    /**
     * Counter for cache hits (successful lookups)
     */
    private final AtomicLong hitCount;
    /**
     * Counter for cache misses (failed lookups)
     */
    private final AtomicLong missCount;
    /**
     * Counter for entries evicted from cache
     */
    private final AtomicLong evictionCount;
    /**
     * Counter for successful load operations
     */
    private final AtomicLong loadSuccessCount;
    /**
     * Counter for failed load operations
     */
    private final AtomicLong loadFailureCount;
    /**
     * Total time spent loading entries in nanoseconds
     */
    private final AtomicLong totalLoadTimeNanos;

    /**
     * Interval between automatic cleanup runs for expired entries
     */
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private LruPaginationCache(Builder<K, V> builder) {
        this.maxSize = builder.maxSize;
        this.defaultTtl = builder.defaultTtl;
        this.executor = builder.executor;
        this.lock = new ReentrantReadWriteLock();

        // LinkedHashMap with access-order for LRU
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                if (size() > maxSize) {
                    evictionCount.incrementAndGet();
                    return true;
                }
                return false;
            }
        };

        this.hitCount = new AtomicLong(0);
        this.missCount = new AtomicLong(0);
        this.evictionCount = new AtomicLong(0);
        this.loadSuccessCount = new AtomicLong(0);
        this.loadFailureCount = new AtomicLong(0);
        this.totalLoadTimeNanos = new AtomicLong(0);

        // Schedule periodic cleanup of expired entries
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pagination-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduleCleanup();
    }

    /**
     * Create a new builder for constructing an LruPaginationCache.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     * @return a new Builder instance
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    /**
     * Create a new LruPaginationCache from a PaginationConfig.
     * Convenience factory method that extracts cache settings from the config.
     *
     * @param config the pagination configuration containing cache settings
     * @param <K>    the type of keys
     * @param <V>    the type of values
     * @return a new LruPaginationCache configured according to the config
     */
    public static <K, V> LruPaginationCache<K, V> fromConfig(PaginationConfig config) {
        return LruPaginationCache.<K, V>builder()
            .maxSize(config.getCacheMaxSize())
            .defaultTtl(config.getCacheTtl())
            .build();
    }

    /**
     * Schedule periodic cleanup of expired entries.
     * Called during cache initialization. Uses a fixed-rate schedule with an interval defined by
     * {@link #CLEANUP_INTERVAL}. The cleanup removes expired entries and increments eviction count.
     */
    private void scheduleCleanup() {
        cleanupScheduler.scheduleAtFixedRate(
            this::removeExpiredEntries,
            CLEANUP_INTERVAL.toMillis(),
            CLEANUP_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    private void removeExpiredEntries() {
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<K, CacheEntry<V>>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<K, CacheEntry<V>> entry = it.next();
                if (entry.getValue().isExpired()) {
                    it.remove();
                    evictionCount.incrementAndGet();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "Cache key cannot be null");

        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                missCount.incrementAndGet();
                return Optional.empty();
            }

            if (entry.isExpired()) {
                missCount.incrementAndGet();
                // Upgrade to write lock for removal
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                    evictionCount.incrementAndGet();
                    lock.readLock().lock();
                } finally {
                    lock.writeLock().unlock();
                }
                return Optional.empty();
            }

            hitCount.incrementAndGet();
            return Optional.of(entry.getValue());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Optional<V>> getAsync(K key) {
        return CompletableFuture.supplyAsync(() -> get(key), executor);
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtl);
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        Objects.requireNonNull(value, "Cache value cannot be null");
        Objects.requireNonNull(ttl, "TTL cannot be null");

        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry<>(value, ttl));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CompletableFuture<Void> putAsync(K key, V value) {
        return CompletableFuture.runAsync(() -> put(key, value), executor);
    }

    @Override
    public V getOrCompute(K key, Supplier<V> loader) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        Objects.requireNonNull(loader, "Loader cannot be null");

        Optional<V> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        long startTime = System.nanoTime();
        try {
            V value = loader.get();
            long loadTime = System.nanoTime() - startTime;
            totalLoadTimeNanos.addAndGet(loadTime);
            loadSuccessCount.incrementAndGet();

            put(key, value);
            return value;
        } catch (Exception e) {
            loadFailureCount.incrementAndGet();
            throw new CacheException("Failed to load value for key: " + key, e);
        }
    }

    @Override
    public CompletableFuture<V> getOrComputeAsync(K key, Supplier<CompletableFuture<V>> loader) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        Objects.requireNonNull(loader, "Loader cannot be null");

        return getAsync(key).thenCompose(optionalValue -> {
            if (optionalValue.isPresent()) {
                return CompletableFuture.completedFuture(optionalValue.get());
            }

            long startTime = System.nanoTime();
            return loader.get()
                .thenApply(value -> {
                    long loadTime = System.nanoTime() - startTime;
                    totalLoadTimeNanos.addAndGet(loadTime);
                    loadSuccessCount.incrementAndGet();
                    put(key, value);
                    return value;
                })
                .exceptionally(e -> {
                    loadFailureCount.incrementAndGet();
                    throw new CacheException("Failed to load value for key: " + key, e);
                });
        });
    }

    @Override
    public void invalidate(K key) {
        Objects.requireNonNull(key, "Cache key cannot be null");

        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAll() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(K key) {
        return get(key).isPresent();
    }

    @Override
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            return CacheStats.builder()
                .hitCount(hitCount.get())
                .missCount(missCount.get())
                .evictionCount(evictionCount.get())
                .loadSuccessCount(loadSuccessCount.get())
                .loadFailureCount(loadFailureCount.get())
                .totalLoadTime(TimeUnit.NANOSECONDS.toMillis(totalLoadTimeNanos.get()))
                .currentSize(cache.size())
                .maxSize(maxSize)
                .build();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Shuts down the cache and releases resources.
     * <p>
     * Stops the background cleanup scheduler. Cache content remains in-memory until GC.
     * Safe to call multiple times.
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal cache entry with TTL support.
     */
    private static final class CacheEntry<V> {
        private final V value;
        private final Instant expiresAt;

        CacheEntry(V value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        V getValue() {
            return value;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Builder for constructing {@link LruPaginationCache} instances.
     * Provides a fluent API for configuring cache settings.
     *
     * @param <K> the type of keys
     * @param <V> the type of values
     */
    public static final class Builder<K, V> {
        private int maxSize = 1000;
        private Duration defaultTtl = Duration.ofMinutes(5);
        private ExecutorService executor = ForkJoinPool.commonPool();

        private Builder() {
        }

        /**
         * Set the maximum number of entries the cache can hold.
         * When the limit is reached, least recently used entries are evicted.
         *
         * @param maxSize the maximum cache size (must be at least 1)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if maxSize is less than 1
         */
        public Builder<K, V> maxSize(int maxSize) {
            if (maxSize < 1) {
                throw new IllegalArgumentException("Max size must be at least 1");
            }
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Set the default time-to-live for cache entries.
         * Entries will automatically expire and be removed after this duration.
         *
         * @param defaultTtl the default TTL duration (must not be null)
         * @return this builder for method chaining
         * @throws NullPointerException if defaultTtl is null
         */
        public Builder<K, V> defaultTtl(Duration defaultTtl) {
            this.defaultTtl = Objects.requireNonNull(defaultTtl, "Default TTL cannot be null");
            return this;
        }

        /**
         * Set the executor service for async cache operations.
         * Defaults to the common ForkJoinPool if not specified.
         *
         * @param executor the executor service for async operations (must not be null)
         * @return this builder for method chaining
         * @throws NullPointerException if executor is null
         */
        public Builder<K, V> executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
            return this;
        }

        /**
         * Build and return the configured LruPaginationCache instance.
         *
         * @return a new LruPaginationCache with the configured settings
         */
        public LruPaginationCache<K, V> build() {
            return new LruPaginationCache<>(this);
        }
    }
}
