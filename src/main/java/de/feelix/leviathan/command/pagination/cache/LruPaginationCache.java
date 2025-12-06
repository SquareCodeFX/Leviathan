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
 * Thread-safe LRU cache implementation with TTL support.
 * Uses LinkedHashMap for LRU ordering and ReadWriteLock for concurrency.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public final class LruPaginationCache<K, V> implements PaginationCache<K, V> {

    private final int maxSize;
    private final Duration defaultTtl;
    private final Map<K, CacheEntry<V>> cache;
    private final ReadWriteLock lock;
    private final ExecutorService executor;
    private final ScheduledExecutorService cleanupScheduler;

    private final AtomicLong hitCount;
    private final AtomicLong missCount;
    private final AtomicLong evictionCount;
    private final AtomicLong loadSuccessCount;
    private final AtomicLong loadFailureCount;
    private final AtomicLong totalLoadTimeNanos;

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

    public static <K, V> Builder<K, V> builder() {
        return new Builder<>();
    }

    public static <K, V> LruPaginationCache<K, V> fromConfig(PaginationConfig config) {
        return LruPaginationCache.<K, V>builder()
                .maxSize(config.getCacheMaxSize())
                .defaultTtl(config.getCacheTtl())
                .build();
    }

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

    public static final class Builder<K, V> {
        private int maxSize = 1000;
        private Duration defaultTtl = Duration.ofMinutes(5);
        private ExecutorService executor = ForkJoinPool.commonPool();

        private Builder() {}

        public Builder<K, V> maxSize(int maxSize) {
            if (maxSize < 1) {
                throw new IllegalArgumentException("Max size must be at least 1");
            }
            this.maxSize = maxSize;
            return this;
        }

        public Builder<K, V> defaultTtl(Duration defaultTtl) {
            this.defaultTtl = Objects.requireNonNull(defaultTtl, "Default TTL cannot be null");
            return this;
        }

        public Builder<K, V> executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "Executor cannot be null");
            return this;
        }

        public LruPaginationCache<K, V> build() {
            return new LruPaginationCache<>(this);
        }
    }
}
