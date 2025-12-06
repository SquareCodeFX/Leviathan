package de.feelix.leviathan.command.pagination.datasource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-memory data source implementation backed by a List.
 * <p>
 * Optimized for in-memory collections and supports:
 * <ul>
 *   <li>Efficient random-access pagination using list indices</li>
 *   <li>Optional filtering with custom predicates</li>
 *   <li>Optional sorting with custom comparators</li>
 *   <li>Immutable data snapshot for thread safety</li>
 * </ul>
 * <p>
 * Contract notes:
 * <ul>
 *   <li>Input collections are copied; subsequent changes to the original collection are not reflected.</li>
 *   <li>Filtering and sorting are applied once during construction for predictable performance.</li>
 *   <li>Offsets are 0-based; {@code fetch(offset, limit)} clamps the end index and returns an empty list if
 *       {@code offset >= size}.</li>
 *   <li>{@link #identifier} is intended to be stable and unique for a given logical dataset; use builder to set it
 *   .</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Simple creation
 * ListDataSource<String> ds = ListDataSource.of(myList);
 *
 * // With filtering and sorting
 * ListDataSource<Player> ds = ListDataSource.<Player>builder()
 *     .data(players)
 *     .filter(p -> p.isOnline())
 *     .comparator(Comparator.comparing(Player::getName))
 *     .identifier("online-players")
 *     .build();
 * }
 * </pre>
 *
 * @param <T> the type of elements in this data source
 * @see PaginationDataSource
 * @see LazyDataSource
 * @since 1.0.0
 */
public final class ListDataSource<T> implements PaginationDataSource<T> {

    /**
     * The immutable list of filtered and sorted data
     */
    private final List<T> data;
    /**
     * Unique identifier for this data source
     */
    private final String identifier;
    /**
     * Optional comparator used for sorting (may be null)
     */
    private final Comparator<? super T> comparator;
    /**
     * Filter predicate applied to elements
     */
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

    /**
     * Create a new builder for constructing a ListDataSource.
     *
     * @param <T> the type of elements
     * @return a new Builder instance
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Create a simple ListDataSource from a collection.
     * Convenience factory method for quick creation without custom options.
     *
     * @param data the collection to wrap
     * @param <T>  the type of elements
     * @return a new ListDataSource containing all elements from the collection
     */
    public static <T> ListDataSource<T> of(Collection<T> data) {
        return ListDataSource.<T>builder()
            .data(data)
            .build();
    }

    /**
     * Create an empty data source with no elements.
     *
     * @param <T> the type of elements
     * @return an empty ListDataSource
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
     * Get all elements in the data source as an unmodifiable list.
     * The returned list reflects any filtering and sorting applied during construction.
     *
     * @return an unmodifiable list containing all elements
     */
    public List<T> getAll() {
        return data;
    }

    /**
     * Create a new data source with an additional filter applied.
     * The new filter is combined with any existing filter using AND logic.
     *
     * @param additionalFilter the additional filter predicate to apply
     * @return a new ListDataSource with the combined filter
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
     * Create a new data source with a different sort order applied.
     * The new comparator replaces any existing comparator.
     *
     * @param newComparator the comparator to use for sorting
     * @return a new ListDataSource with the specified sort order
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

    /**
     * Builder for constructing {@link ListDataSource} instances.
     * Provides a fluent API for configuring data, filtering, and sorting.
     *
     * @param <T> the type of elements
     */
    public static final class Builder<T> {
        private List<T> data = new ArrayList<>();
        private String identifier = UUID.randomUUID().toString();
        private Comparator<? super T> comparator = null;
        private Predicate<? super T> filter = t -> true;

        private Builder() {
        }

        /**
         * Set the data from a collection.
         *
         * @param data the collection of elements to paginate
         * @return this builder for method chaining
         * @throws NullPointerException if data is null
         */
        public Builder<T> data(Collection<T> data) {
            this.data = new ArrayList<>(Objects.requireNonNull(data, "Data cannot be null"));
            return this;
        }

        /**
         * Set the data from varargs.
         *
         * @param items the elements to paginate
         * @return this builder for method chaining
         */
        @SafeVarargs
        public final Builder<T> data(T... items) {
            this.data = new ArrayList<>(Arrays.asList(items));
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
         * Set the comparator for sorting elements.
         * If null, elements will maintain their original order.
         *
         * @param comparator the comparator to use for sorting, or null for no sorting
         * @return this builder for method chaining
         */
        public Builder<T> comparator(Comparator<? super T> comparator) {
            this.comparator = comparator;
            return this;
        }

        /**
         * Set the filter predicate for elements.
         * Only elements matching the predicate will be included.
         * Defaults to accepting all elements if not specified.
         *
         * @param filter the filter predicate to apply
         * @return this builder for method chaining
         * @throws NullPointerException if filter is null
         */
        public Builder<T> filter(Predicate<? super T> filter) {
            this.filter = Objects.requireNonNull(filter, "Filter cannot be null");
            return this;
        }

        /**
         * Build and return the configured ListDataSource instance.
         * Filtering and sorting are applied during construction.
         *
         * @return a new ListDataSource with the configured settings
         */
        public ListDataSource<T> build() {
            return new ListDataSource<>(this);
        }
    }
}
