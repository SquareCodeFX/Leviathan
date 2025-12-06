package de.feelix.leviathan.command.pagination.datasource;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for data sources that provide paginated data.
 * Follows Dependency Inversion Principle - depend on abstractions.
 *
 * @param <T> The type of elements provided by this data source
 */
public interface PaginationDataSource<T> {

    /**
     * Fetches a page of data.
     *
     * @param offset The starting index (0-based)
     * @param limit  The maximum number of items to return
     * @return List of items for the requested page
     */
    List<T> fetch(long offset, int limit);

    /**
     * Fetches a page of data asynchronously.
     *
     * @param offset The starting index (0-based)
     * @param limit  The maximum number of items to return
     * @return CompletableFuture containing the list of items
     */
    CompletableFuture<List<T>> fetchAsync(long offset, int limit);

    /**
     * Returns the total count of elements.
     *
     * @return Total number of elements
     */
    long count();

    /**
     * Returns the total count asynchronously.
     *
     * @return CompletableFuture containing the total count
     */
    CompletableFuture<Long> countAsync();

    /**
     * Checks if the data source is empty.
     *
     * @return true if no elements exist
     */
    default boolean isEmpty() {
        return count() == 0;
    }

    /**
     * Returns a unique identifier for this data source.
     * Used for caching purposes.
     *
     * @return Data source identifier
     */
    String getIdentifier();
}
