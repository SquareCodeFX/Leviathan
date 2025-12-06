package de.feelix.leviathan.command.pagination.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Interface for pagination cache implementations.
 * Follows Interface Segregation Principle - minimal, focused contract.
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface PaginationCache<K, V> {

    /**
     * Retrieves a value from the cache.
     */
    Optional<V> get(K key);

    /**
     * Retrieves a value asynchronously.
     */
    CompletableFuture<Optional<V>> getAsync(K key);

    /**
     * Stores a value in the cache with the configured TTL.
     */
    void put(K key, V value);

    /**
     * Stores a value with a custom TTL.
     */
    void put(K key, V value, Duration ttl);

    /**
     * Stores a value asynchronously.
     */
    CompletableFuture<Void> putAsync(K key, V value);

    /**
     * Gets a value if present, otherwise computes and caches it.
     */
    V getOrCompute(K key, Supplier<V> loader);

    /**
     * Gets a value if present, otherwise computes and caches it asynchronously.
     */
    CompletableFuture<V> getOrComputeAsync(K key, Supplier<CompletableFuture<V>> loader);

    /**
     * Removes a value from the cache.
     */
    void invalidate(K key);

    /**
     * Clears all cached entries.
     */
    void invalidateAll();

    /**
     * Returns the number of cached entries.
     */
    int size();

    /**
     * Returns cache statistics.
     */
    CacheStats getStats();

    /**
     * Checks if a key is present in the cache.
     */
    boolean contains(K key);
}
