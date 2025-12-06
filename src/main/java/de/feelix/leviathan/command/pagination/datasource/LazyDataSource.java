package de.feelix.leviathan.command.pagination.datasource;

import de.feelix.leviathan.command.pagination.exception.DataSourceException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Lazy data source that delegates to callback functions for data retrieval.
 * <p>
 * Suitable for external/expensive data providers such as:
 * <ul>
 *   <li>Database queries with LIMIT/OFFSET</li>
 *   <li>REST API calls with pagination parameters</li>
 *   <li>File-based data that should be loaded on demand</li>
 *   <li>Any operation that benefits from lazy, on-demand loading</li>
 * </ul>
 * <p>
 * Contract and behavior notes:
 * <ul>
 *   <li>Offsets are 0-based; {@code limit} must be at least 1. Invalid parameters will result in
 *       {@link IllegalArgumentException}.</li>
 *   <li>If only synchronous callbacks are provided, asynchronous methods are automatically
 *       wrapped using {@link CompletableFuture#supplyAsync(java.util.function.Supplier)}.</li>
 *   <li>Identifiers should be stable and unique for a given logical dataset and configuration to enable caching.</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * LazyDataSource<Player> dataSource = LazyDataSource.<Player>builder()
 *     .fetch((offset, limit) -> database.queryPlayers(offset, limit))
 *     .count(() -> database.countPlayers())
 *     .identifier("player-list")
 *     .build();
 * }</pre>
 *
 * @param <T> the type of elements provided by this data source
 * @see PaginationDataSource
 * @see ListDataSource
 * @since 1.0.0
 */
public final class LazyDataSource<T> implements PaginationDataSource<T> {

    /**
     * Function to fetch a page of data given offset and limit
     */
    private final BiFunction<Long, Integer, List<T>> fetchFunction;
    /**
     * Function to fetch a page of data asynchronously
     */
    private final BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction;
    /**
     * Supplier to get the total count of elements
     */
    private final Supplier<Long> countSupplier;
    /**
     * Supplier to get the total count asynchronously
     */
    private final Supplier<CompletableFuture<Long>> asyncCountSupplier;
    /**
     * Unique identifier for this data source
     */
    private final String identifier;

    private LazyDataSource(Builder<T> builder) {
        this.fetchFunction = builder.fetchFunction;
        this.asyncFetchFunction = builder.asyncFetchFunction;
        this.countSupplier = builder.countSupplier;
        this.asyncCountSupplier = builder.asyncCountSupplier;
        this.identifier = builder.identifier;
    }

    /**
     * Create a new builder for constructing a LazyDataSource.
     *
     * @param <T> the type of elements
     * @return a new Builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public List<T> fetch(long offset, int limit) {
        validateParameters(offset, limit);
        try {
            return fetchFunction.apply(offset, limit);
        } catch (Exception e) {
            throw new DataSourceException("Failed to fetch data at offset " + offset + " with limit " + limit, e);
        }
    }

    @Override
    public CompletableFuture<List<T>> fetchAsync(long offset, int limit) {
        validateParameters(offset, limit);
        return asyncFetchFunction.apply(offset, limit)
            .exceptionally(e -> {
                throw new DataSourceException("Async fetch failed at offset " + offset, e);
            });
    }

    @Override
    public long count() {
        try {
            return countSupplier.get();
        } catch (Exception e) {
            throw new DataSourceException("Failed to get count", e);
        }
    }

    @Override
    public CompletableFuture<Long> countAsync() {
        return asyncCountSupplier.get()
            .exceptionally(e -> {
                throw new DataSourceException("Async count failed", e);
            });
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    private void validateParameters(long offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1: " + limit);
        }
    }

    @Override
    public String toString() {
        return String.format("LazyDataSource{id='%s'}", identifier);
    }

    /**
     * Builder for constructing {@link LazyDataSource} instances.
     * Provides a fluent API for configuring data retrieval callbacks.
     *
     * @param <T> the type of elements
     */
    public static final class Builder<T> {
        private BiFunction<Long, Integer, List<T>> fetchFunction;
        private BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction;
        private Supplier<Long> countSupplier;
        private Supplier<CompletableFuture<Long>> asyncCountSupplier;
        private String identifier = UUID.randomUUID().toString();

        private Builder() {
        }

        /**
         * Set the synchronous fetch function for retrieving paginated data.
         * If no async fetch function is set, this will be wrapped for async operations.
         *
         * @param fetchFunction function that takes (offset, limit) and returns a list of elements
         * @return this builder for method chaining
         * @throws NullPointerException if fetchFunction is null
         */
        public Builder<T> fetch(BiFunction<Long, Integer, List<T>> fetchFunction) {
            this.fetchFunction = Objects.requireNonNull(fetchFunction, "Fetch function cannot be null");
            // Default async to wrapping sync if not set
            if (this.asyncFetchFunction == null) {
                this.asyncFetchFunction = (offset, limit) ->
                    CompletableFuture.supplyAsync(() -> fetchFunction.apply(offset, limit));
            }
            return this;
        }

        /**
         * Set the asynchronous fetch function for retrieving paginated data.
         * Use this for native async operations like non-blocking database queries.
         *
         * @param asyncFetchFunction function that takes (offset, limit) and returns a CompletableFuture with elements
         * @return this builder for method chaining
         * @throws NullPointerException if asyncFetchFunction is null
         */
        public Builder<T> fetchAsync(BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction) {
            this.asyncFetchFunction = Objects.requireNonNull(asyncFetchFunction, "Async fetch function cannot be null");
            return this;
        }

        /**
         * Set the synchronous count supplier for getting the total element count.
         * If no async count supplier is set, this will be wrapped for async operations.
         *
         * @param countSupplier supplier that returns the total number of elements
         * @return this builder for method chaining
         * @throws NullPointerException if countSupplier is null
         */
        public Builder<T> count(Supplier<Long> countSupplier) {
            this.countSupplier = Objects.requireNonNull(countSupplier, "Count supplier cannot be null");
            // Default async to wrapping sync if not set
            if (this.asyncCountSupplier == null) {
                this.asyncCountSupplier = () -> CompletableFuture.supplyAsync(countSupplier);
            }
            return this;
        }

        /**
         * Set the asynchronous count supplier for getting the total element count.
         * Use this for native async operations like non-blocking database queries.
         *
         * @param asyncCountSupplier supplier that returns a CompletableFuture with the total count
         * @return this builder for method chaining
         * @throws NullPointerException if asyncCountSupplier is null
         */
        public Builder<T> countAsync(Supplier<CompletableFuture<Long>> asyncCountSupplier) {
            this.asyncCountSupplier = Objects.requireNonNull(asyncCountSupplier, "Async count supplier cannot be null");
            return this;
        }

        /**
         * Set a unique identifier for this data source.
         * Defaults to a random UUID if not specified.
         *
         * @param identifier the unique identifier string
         * @return this builder for method chaining
         * @throws NullPointerException if identifier is null
         */
        public Builder<T> identifier(String identifier) {
            this.identifier = Objects.requireNonNull(identifier, "Identifier cannot be null");
            return this;
        }

        /**
         * Build and return the configured LazyDataSource instance.
         *
         * @return a new LazyDataSource with the configured callbacks
         * @throws NullPointerException if fetch function or count supplier is not set
         */
        public LazyDataSource<T> build() {
            Objects.requireNonNull(fetchFunction, "Fetch function must be set");
            Objects.requireNonNull(countSupplier, "Count supplier must be set");
            return new LazyDataSource<>(this);
        }
    }
}
