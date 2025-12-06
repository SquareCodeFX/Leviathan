package de.feelix.leviathan.command.pagination.domain;

import de.feelix.leviathan.command.pagination.config.PaginationConfig;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable container for paginated data with metadata.
 * Generic type T represents the type of items in the page.
 * 
 * @param <T> The type of elements in this page
 */
public final class PaginatedResult<T> implements Iterable<T> {

    private final List<T> items;
    private final PageInfo pageInfo;
    private final NavigationWindow navigationWindow;
    private final Instant createdAt;
    private final String queryId;
    private final Map<String, Object> metadata;

    private PaginatedResult(Builder<T> builder) {
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.pageInfo = builder.pageInfo;
        this.navigationWindow = builder.navigationWindow;
        this.createdAt = builder.createdAt;
        this.queryId = builder.queryId;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Factory method for creating a PaginatedResult.
     */
    public static <T> PaginatedResult<T> of(List<T> items, PageInfo pageInfo, PaginationConfig config) {
        return PaginatedResult.<T>builder()
                .items(items)
                .pageInfo(pageInfo)
                .navigationWindow(NavigationWindow.from(pageInfo, config))
                .build();
    }

    /**
     * Creates an empty result.
     */
    public static <T> PaginatedResult<T> empty(int pageSize, PaginationConfig config) {
        PageInfo pageInfo = PageInfo.of(1, 0, pageSize);
        return of(Collections.emptyList(), pageInfo, config);
    }

    // Collection operations
    public T get(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasContent() {
        return !items.isEmpty();
    }

    public Optional<T> first() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public Optional<T> last() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(items.size() - 1));
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    /**
     * Maps items to a new type.
     */
    public <R> PaginatedResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "Mapper function cannot be null");
        List<R> mappedItems = items.stream()
                .map(mapper)
                .collect(Collectors.toList());
        
        return PaginatedResult.<R>builder()
                .items(mappedItems)
                .pageInfo(this.pageInfo)
                .navigationWindow(this.navigationWindow)
                .queryId(this.queryId)
                .metadata(this.metadata)
                .createdAt(this.createdAt)
                .build();
    }

    /**
     * Filters items while maintaining pagination metadata.
     */
    public PaginatedResult<T> filter(java.util.function.Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<T> filteredItems = items.stream()
                .filter(predicate)
                .collect(Collectors.toList());
        
        return PaginatedResult.<T>builder()
                .items(filteredItems)
                .pageInfo(this.pageInfo)
                .navigationWindow(this.navigationWindow)
                .queryId(this.queryId)
                .metadata(this.metadata)
                .createdAt(this.createdAt)
                .build();
    }

    // Navigation shortcuts
    public boolean hasNextPage() {
        return pageInfo.hasNextPage();
    }

    public boolean hasPreviousPage() {
        return pageInfo.hasPreviousPage();
    }

    public int getCurrentPage() {
        return pageInfo.getCurrentPage();
    }

    public int getTotalPages() {
        return pageInfo.getTotalPages();
    }

    public long getTotalElements() {
        return pageInfo.getTotalElements();
    }

    // Getters
    public List<T> getItems() {
        return items;
    }

    public PageInfo getPageInfo() {
        return pageInfo;
    }

    public NavigationWindow getNavigationWindow() {
        return navigationWindow;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getQueryId() {
        return queryId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Optional<Object> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Checks if this result is still fresh based on TTL.
     */
    public boolean isFresh(java.time.Duration ttl) {
        return createdAt.plus(ttl).isAfter(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaginatedResult<?> that = (PaginatedResult<?>) o;
        return Objects.equals(items, that.items) &&
                Objects.equals(pageInfo, that.pageInfo) &&
                Objects.equals(queryId, that.queryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, pageInfo, queryId);
    }

    @Override
    public String toString() {
        return String.format("PaginatedResult{%s, items=%d, queryId='%s'}",
                pageInfo, items.size(), queryId);
    }

    public static final class Builder<T> {
        private List<T> items = new ArrayList<>();
        private PageInfo pageInfo;
        private NavigationWindow navigationWindow;
        private Instant createdAt = Instant.now();
        private String queryId = UUID.randomUUID().toString();
        private Map<String, Object> metadata = new HashMap<>();

        private Builder() {}

        public Builder<T> items(List<T> items) {
            this.items = Objects.requireNonNull(items, "Items cannot be null");
            return this;
        }

        public Builder<T> pageInfo(PageInfo pageInfo) {
            this.pageInfo = Objects.requireNonNull(pageInfo, "PageInfo cannot be null");
            return this;
        }

        public Builder<T> navigationWindow(NavigationWindow navigationWindow) {
            this.navigationWindow = Objects.requireNonNull(navigationWindow, "NavigationWindow cannot be null");
            return this;
        }

        public Builder<T> createdAt(Instant createdAt) {
            this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
            return this;
        }

        public Builder<T> queryId(String queryId) {
            this.queryId = Objects.requireNonNull(queryId, "QueryId cannot be null");
            return this;
        }

        public Builder<T> metadata(Map<String, Object> metadata) {
            this.metadata = Objects.requireNonNull(metadata, "Metadata cannot be null");
            return this;
        }

        public Builder<T> addMetadata(String key, Object value) {
            this.metadata.put(
                    Objects.requireNonNull(key, "Metadata key cannot be null"),
                    Objects.requireNonNull(value, "Metadata value cannot be null")
            );
            return this;
        }

        public PaginatedResult<T> build() {
            Objects.requireNonNull(pageInfo, "PageInfo must be set");
            Objects.requireNonNull(navigationWindow, "NavigationWindow must be set");
            return new PaginatedResult<>(this);
        }
    }
}
