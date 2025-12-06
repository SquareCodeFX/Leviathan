package de.feelix.leviathan.command.pagination.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Interface for pagination cache implementations.
 * <p>
 * Follows the Interface Segregation Principle with a minimal, focused contract
 * that supports both synchronous and asynchronous cache operations.
 * <p>
 * Implementations should be thread-safe and support TTL-based expiration.
 *
 * @param <K> the type of keys used to identify cached entries
 * @param <V> the type of values stored in the cache
 * @see LruPaginationCache
 * @see CacheStats
 * @since 1.0.0
 */
public interface PaginationCache<K, V> {

    /**
     * Retrieve a value from the cache by its key.
     *
     * @param key the key whose associated value is to be returned
     * @return an Optional containing the cached value, or empty if not found or expired
     */
    Optional<V> get(K key);

    /**
     * Retrieve a value from the cache asynchronously.
     *
     * @param key the key whose associated value is to be returned
     * @return a CompletableFuture containing an Optional with the cached value
     */
    CompletableFuture<Optional<V>> getAsync(K key);

    /**
     * Store a value in the cache with the default TTL.
     *
     * @param key the key with which the value is to be associated
     * @param value the value to be cached
     */
    void put(K key, V value);

    /**
     * Store a value in the cache with a custom TTL.
     *
     * @param key the key with which the value is to be associated
     * @param value the value to be cached
     * @param ttl the time-to-live duration for this entry
     */
    void put(K key, V value, Duration ttl);

    /**
     * Store a value in the cache asynchronously with the default TTL.
     *
     * @param key the key with which the value is to be associated
     * @param value the value to be cached
     * @return a CompletableFuture that completes when the value is stored
     */
    CompletableFuture<Void> putAsync(K key, V value);

    /**
     * Get a value from the cache, or compute and cache it if absent.
     * This is an atomic operation that prevents multiple simultaneous loads for the same key.
     *
     * @param key the key whose associated value is to be returned or computed
     * @param loader the supplier function to compute the value if not cached
     * @return the cached or newly computed value
     */
    V getOrCompute(K key, Supplier<V> loader);

    /**
     * Get a value from the cache asynchronously, or compute and cache it if absent.
     * This is an atomic operation that prevents multiple simultaneous loads for the same key.
     *
     * @param key the key whose associated value is to be returned or computed
     * @param loader the supplier function that returns a CompletableFuture with the value
     * @return a CompletableFuture containing the cached or newly computed value
     */
    CompletableFuture<V> getOrComputeAsync(K key, Supplier<CompletableFuture<V>> loader);

    /**
     * Remove a specific entry from the cache.
     *
     * @param key the key whose entry is to be removed
     */
    void invalidate(K key);

    /**
     * Remove all entries from the cache.
     */
    void invalidateAll();

    /**
     * Get the current number of entries in the cache.
     *
     * @return the number of cached entries
     */
    int size();

    /**
     * Get statistics about cache performance (hits, misses, evictions, etc.).
     *
     * @return the current cache statistics
     */
    CacheStats getStats();

    /**
     * Check if a key exists in the cache and is not expired.
     *
     * @param key the key to check
     * @return true if the key is present and not expired, false otherwise
     */
    boolean contains(K key);
}
