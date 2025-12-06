package de.feelix.leviathan.command.pagination.service;

import de.feelix.leviathan.command.pagination.cache.CacheStats;
import de.feelix.leviathan.command.pagination.cache.LruPaginationCache;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.datasource.ListDataSource;
import de.feelix.leviathan.command.pagination.datasource.PaginationDataSource;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level pagination manager that coordinates multiple data sources,
 * services, and renderers. Provides a unified API for pagination operations.
 */
public final class PaginationManager {

    /** Default configuration applied to newly created services.
     * -- GETTER --
     *  Returns the default configuration.
     *
     * @return default pagination config
     */
    @Getter
    private final PaginationConfig defaultConfig;
    /** Registry of named services managed by this manager. */
    private final Map<String, PaginationService<?>> services;
    /** Optional shared cache that can be reused across services. */
    private final LruPaginationCache<Object, Object> sharedCache;

    private PaginationManager(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.services = new ConcurrentHashMap<>(builder.services);
        this.sharedCache = builder.sharedCache;
    }

    /**
     * Create a new builder for {@link PaginationManager}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a quick pagination from a collection.
     *
     * @param items      items to paginate
     * @param pageNumber 1-based page number
     * @param <T>        element type
     * @return paginated result for the requested page
     */
    public <T> PaginatedResult<T> paginate(Collection<T> items, int pageNumber) {
        return paginate(items, pageNumber, defaultConfig);
    }

    /**
     * Creates a quick pagination with custom config.
     *
     * @param items      items to paginate
     * @param pageNumber 1-based page number
     * @param config     pagination config to use
     * @param <T>        element type
     * @return paginated result for the requested page
     */
    public <T> PaginatedResult<T> paginate(Collection<T> items, int pageNumber, PaginationConfig config) {
        ListDataSource<T> dataSource = ListDataSource.of(items);
       PaginationService<T> service = PaginationService.<T>builder()
                .config(config)
                .dataSource(dataSource)
                .build();

        return service.getPage(pageNumber);
    }

    /**
     * Creates a quick pagination asynchronously.
     *
     * @param items      items to paginate
     * @param pageNumber 1-based page number
     * @param <T>        element type
     * @return future completing with the paginated result
     */
    public <T> CompletableFuture<PaginatedResult<T>> paginateAsync(Collection<T> items, int pageNumber) {
        return CompletableFuture.supplyAsync(() -> paginate(items, pageNumber));
    }

    /**
     * Registers a named pagination service.
     *
     * @param name    unique service name
     * @param service service instance to register
     * @param <T>     element type of the service
     */
    public <T> void registerService(String name, PaginationService<T> service) {
        Objects.requireNonNull(name, "Service name cannot be null");
        Objects.requireNonNull(service, "Service cannot be null");
        services.put(name, service);
    }

    /**
     * Gets a registered service by name.
     *
     * @param name service name
     * @param <T>  element type
     * @return optional service instance
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<PaginationService<T>> getService(String name) {
        return Optional.ofNullable((PaginationService<T>) services.get(name));
    }

    /**
     * Removes a registered service.
     *
     * @param name service name to remove
     */
    public void unregisterService(String name) {
        services.remove(name);
    }

    /**
     * Lists all registered service names.
     *
     * @return unmodifiable set of registered service names
     */
    public Set<String> listServices() {
        return Collections.unmodifiableSet(services.keySet());
    }

    /**
     * Gets a page from a named service.
     *
     * @param serviceName registered service name
     * @param pageNumber  1-based page number
     * @param <T>         element type
     * @return optional page result
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<PaginatedResult<T>> getPage(String serviceName, int pageNumber) {
        return getService(serviceName)
                .map(service -> (PaginatedResult<T>) service.getPage(pageNumber));
    }

    /**
     * Gets a page asynchronously from a named service.
     *
     * @param serviceName registered service name
     * @param pageNumber  1-based page number
     * @param <T>         element type
     * @return future completing with an optional page result
     */
    public <T> CompletableFuture<Optional<PaginatedResult<T>>> getPageAsync(String serviceName, int pageNumber) {
        Optional<PaginationService<T>> serviceOpt = getService(serviceName);

        return serviceOpt.map(tPaginationService -> tPaginationService
            .getPageAsync(pageNumber)
            .thenApply(Optional::of)).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));

    }

    /**
     * Invalidates all caches across all services.
     */
    public void invalidateAllCaches() {
        services.values().forEach(service -> {
            try {
                service.invalidateCache();
            } catch (Exception ignored) {
                // Some services might not have cache enabled
            }
        });

        if (sharedCache != null) {
            sharedCache.invalidateAll();
        }
    }

    /**
     * Returns combined cache statistics.
     *
     * @return map of service name to cache stats (includes "shared" if shared cache present)
     */
    public Map<String, CacheStats> getAllCacheStats() {
        Map<String, CacheStats> stats = new HashMap<>();

        services.forEach((name, service) ->
                service.getCacheStats().ifPresent(s -> stats.put(name, s)));

        if (sharedCache != null) {
            stats.put("shared", sharedCache.getStats());
        }

        return Collections.unmodifiableMap(stats);
    }

    /**
     * Creates a new service builder with default config.
     *
     * @param <T> element type
     * @return a pre-configured {@link PaginationService.Builder}
     */
    public <T> PaginationService.Builder<T> newServiceBuilder() {
        return PaginationService.<T>builder().config(defaultConfig);
    }

    /**
     * Creates a service for a data source and registers it.
     *
     * @param name       service name
     * @param dataSource data source for the service
     * @param <T>        element type
     * @return the created and registered service
     */
    public <T> PaginationService<T> createAndRegister(String name, PaginationDataSource<T> dataSource) {
        PaginationService<T> service = PaginationService.<T>builder()
                .config(defaultConfig)
                .dataSource(dataSource)
                .withDefaultCache()
                .build();

        registerService(name, service);
        return service;
    }

    /**
     * Shuts down the manager and releases resources.
     */
    public void shutdown() {
        if (sharedCache != null) {
            sharedCache.shutdown();
        }
        services.clear();
    }

    /**
     * Builder for {@link PaginationManager}.
     */
    public static final class Builder {
        /** Default configuration used when creating services via this manager. */
        private PaginationConfig defaultConfig = PaginationConfig.defaults();
        /** Initial services to register. */
        private final Map<String, PaginationService<?>> services = new HashMap<>();
        /** Optional shared cache to be managed and reused across services. */
        private LruPaginationCache<Object, Object> sharedCache;

        private Builder() {}

        /**
         * Set the default configuration for services created by this manager.
         */
        public Builder defaultConfig(PaginationConfig config) {
            this.defaultConfig = Objects.requireNonNull(config, "Config cannot be null");
            return this;
        }

        /**
         * Pre-register a service with the builder.
         */
        public <T> Builder registerService(String name, PaginationService<T> service) {
            services.put(
                    Objects.requireNonNull(name, "Name cannot be null"),
                    Objects.requireNonNull(service, "Service cannot be null")
            );
            return this;
        }

        /**
         * Create and attach a shared LRU cache using the default configuration.
         */
        public Builder withSharedCache() {
            this.sharedCache = LruPaginationCache.fromConfig(defaultConfig);
            return this;
        }

        /**
         * Attach an existing shared cache instance.
         */
        public Builder withSharedCache(LruPaginationCache<Object, Object> cache) {
            this.sharedCache = cache;
            return this;
        }

        /**
         * Build the {@link PaginationManager} instance.
         */
        public PaginationManager build() {
            return new PaginationManager(this);
        }
    }
}
