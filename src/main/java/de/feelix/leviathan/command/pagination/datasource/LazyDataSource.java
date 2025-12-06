package de.feelix.leviathan.command.pagination.datasource;

import de.feelix.leviathan.command.pagination.exception.DataSourceException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Lazy data source that delegates to callbacks.
 * Ideal for database queries, API calls, or other external data sources.
 *
 * @param <T> The type of elements
 */
public final class LazyDataSource<T> implements PaginationDataSource<T> {

    private final BiFunction<Long, Integer, List<T>> fetchFunction;
    private final BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction;
    private final Supplier<Long> countSupplier;
    private final Supplier<CompletableFuture<Long>> asyncCountSupplier;
    private final String identifier;

    private LazyDataSource(Builder<T> builder) {
        this.fetchFunction = builder.fetchFunction;
        this.asyncFetchFunction = builder.asyncFetchFunction;
        this.countSupplier = builder.countSupplier;
        this.asyncCountSupplier = builder.asyncCountSupplier;
        this.identifier = builder.identifier;
    }

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

    public static final class Builder<T> {
        private BiFunction<Long, Integer, List<T>> fetchFunction;
        private BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction;
        private Supplier<Long> countSupplier;
        private Supplier<CompletableFuture<Long>> asyncCountSupplier;
        private String identifier = UUID.randomUUID().toString();

        private Builder() {}

        /**
         * Sets the synchronous fetch function.
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
         * Sets the asynchronous fetch function.
         */
        public Builder<T> fetchAsync(BiFunction<Long, Integer, CompletableFuture<List<T>>> asyncFetchFunction) {
            this.asyncFetchFunction = Objects.requireNonNull(asyncFetchFunction, "Async fetch function cannot be null");
            return this;
        }

        /**
         * Sets the synchronous count supplier.
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
         * Sets the asynchronous count supplier.
         */
        public Builder<T> countAsync(Supplier<CompletableFuture<Long>> asyncCountSupplier) {
            this.asyncCountSupplier = Objects.requireNonNull(asyncCountSupplier, "Async count supplier cannot be null");
            return this;
        }

        public Builder<T> identifier(String identifier) {
            this.identifier = Objects.requireNonNull(identifier, "Identifier cannot be null");
            return this;
        }

        public LazyDataSource<T> build() {
            Objects.requireNonNull(fetchFunction, "Fetch function must be set");
            Objects.requireNonNull(countSupplier, "Count supplier must be set");
            return new LazyDataSource<>(this);
        }
    }
}
