package de.feelix.leviathan.command.pagination.datasource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for data sources that provide paginated data.
 * <p>
 * Contract notes:
 * <ul>
 *   <li>Offsets are 0-based; limits must be at least 1.</li>
 *   <li>Implementations should be side-effect free and thread-safe to the extent possible.</li>
 *   <li>Implementations are encouraged to provide stable, deterministic ordering.</li>
 *   <li>{@link #getIdentifier()} should be unique for a given logical data set and configuration
 *       so that caches can key on it.</li>
 * </ul>
 *
 * @param <T> The type of elements provided by this data source
 */
public interface PaginationDataSource<T> {

    /**
     * Fetches a page of data.
     *
     * @param offset The starting index (0-based). If {@code offset >= count()}, implementations should return an
     *               empty list.
     * @param limit  The maximum number of items to return (must be at least 1)
     * @return List of items for the requested page (never null)
     */
    List<T> fetch(long offset, int limit);

    /**
     * Fetches a page of data asynchronously.
     *
     * @param offset The starting index (0-based)
     * @param limit  The maximum number of items to return (must be at least 1)
     * @return CompletableFuture containing the list of items (never completes with null)
     */
    CompletableFuture<List<T>> fetchAsync(long offset, int limit);

    /**
     * Returns the total count of elements.
     * Implementations should make a best effort to keep this count consistent with pages returned by
     * {@link #fetch(long, int)}.
     *
     * @return Total number of elements (non-negative)
     */
    long count();

    /**
     * Returns the total count asynchronously.
     *
     * @return CompletableFuture containing the total count (non-negative)
     */
    CompletableFuture<Long> countAsync();

    /**
     * Checks if the data source is empty.
     * Convenience default implementation based on {@link #count()}.
     *
     * @return true if no elements exist
     */
    default boolean isEmpty() {
        return count() == 0;
    }

    /**
     * Returns a stable, unique identifier for this data source under its current configuration.
     * This value is used for caching; two logically equivalent sources should return the same identifier.
     *
     * @return Data source identifier
     */
    String getIdentifier();
}
