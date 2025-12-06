package de.feelix.leviathan.command.pagination.datasource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory data source implementation backed by a List.
 * Supports filtering, sorting, and efficient pagination.
 *
 * @param <T> The type of elements
 */
public final class ListDataSource<T> implements PaginationDataSource<T> {

    private final List<T> data;
    private final String identifier;
    private final Comparator<? super T> comparator;
    private final Predicate<? super T> filter;

    private ListDataSource(Builder<T> builder) {
        this.identifier = builder.identifier;
        this.comparator = builder.comparator;
        this.filter = builder.filter;

        // Apply filter and sorting during construction for efficiency
        List<T> processedData = builder.data.stream()
                .filter(filter)
                .collect(Collectors.toList());

        if (comparator != null) {
            processedData.sort(comparator);
        }

        this.data = Collections.unmodifiableList(processedData);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Creates a simple ListDataSource from a collection.
     */
    public static <T> ListDataSource<T> of(Collection<T> data) {
        return ListDataSource.<T>builder()
                .data(data)
                .build();
    }

    /**
     * Creates an empty data source.
     */
    public static <T> ListDataSource<T> empty() {
        return ListDataSource.<T>builder().build();
    }

    @Override
    public List<T> fetch(long offset, int limit) {
        validateFetchParameters(offset, limit);

        if (offset >= data.size()) {
            return Collections.emptyList();
        }

        int fromIndex = (int) offset;
        int toIndex = Math.min(fromIndex + limit, data.size());

        return new ArrayList<>(data.subList(fromIndex, toIndex));
    }

    @Override
    public CompletableFuture<List<T>> fetchAsync(long offset, int limit) {
        return CompletableFuture.supplyAsync(() -> fetch(offset, limit));
    }

    @Override
    public long count() {
        return data.size();
    }

    @Override
    public CompletableFuture<Long> countAsync() {
        return CompletableFuture.completedFuture(count());
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the underlying data as an unmodifiable list.
     */
    public List<T> getAll() {
        return data;
    }

    /**
     * Creates a new data source with an additional filter applied.
     */
    public ListDataSource<T> withFilter(Predicate<? super T> additionalFilter) {
        Predicate<T> combined = t -> filter.test(t) && additionalFilter.test(t);
        return ListDataSource.<T>builder()
                .data(data)
                .identifier(identifier + "_filtered")
                .filter(combined)
                .comparator(comparator)
                .build();
    }

    /**
     * Creates a new data source with sorting applied.
     */
    public ListDataSource<T> withSort(Comparator<? super T> newComparator) {
        return ListDataSource.<T>builder()
                .data(data)
                .identifier(identifier + "_sorted")
                .filter(filter)
                .comparator(newComparator)
                .build();
    }

    private void validateFetchParameters(long offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1: " + limit);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListDataSource<?> that = (ListDataSource<?>) o;
        return Objects.equals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public String toString() {
        return String.format("ListDataSource{id='%s', size=%d}", identifier, data.size());
    }

    public static final class Builder<T> {
        private List<T> data = new ArrayList<>();
        private String identifier = UUID.randomUUID().toString();
        private Comparator<? super T> comparator = null;
        private Predicate<? super T> filter = t -> true;

        private Builder() {}

        public Builder<T> data(Collection<T> data) {
            this.data = new ArrayList<>(Objects.requireNonNull(data, "Data cannot be null"));
            return this;
        }

        @SafeVarargs
        public final Builder<T> data(T... items) {
            this.data = new ArrayList<>(Arrays.asList(items));
            return this;
        }

        public Builder<T> identifier(String identifier) {
            this.identifier = Objects.requireNonNull(identifier, "Identifier cannot be null");
            return this;
        }

        public Builder<T> comparator(Comparator<? super T> comparator) {
            this.comparator = comparator;
            return this;
        }

        public Builder<T> filter(Predicate<? super T> filter) {
            this.filter = Objects.requireNonNull(filter, "Filter cannot be null");
            return this;
        }

        public ListDataSource<T> build() {
            return new ListDataSource<>(this);
        }
    }
}
