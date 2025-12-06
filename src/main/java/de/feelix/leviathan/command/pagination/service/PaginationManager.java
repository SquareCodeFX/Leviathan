package de.feelix.leviathan.command.pagination.service;

import de.feelix.leviathan.command.pagination.cache.CacheStats;
import de.feelix.leviathan.command.pagination.cache.LruPaginationCache;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.pagination.datasource.ListDataSource;
import de.feelix.leviathan.command.pagination.datasource.PaginationDataSource;
import de.feelix.leviathan.command.pagination.domain.PaginatedResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-level pagination manager that coordinates multiple data sources,
 * services, and renderers. Provides a unified API for pagination operations.
 */
public final class PaginationManager {

    private final PaginationConfig defaultConfig;
    private final Map<String, PaginationService<?>> services;
    private final LruPaginationCache<Object, Object> sharedCache;

    private PaginationManager(Builder builder) {
        this.defaultConfig = builder.defaultConfig;
        this.services = new ConcurrentHashMap<>(builder.services);
        this.sharedCache = builder.sharedCache;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a quick pagination from a collection.
     */
    public <T> PaginatedResult<T> paginate(Collection<T> items, int pageNumber) {
        return paginate(items, pageNumber, defaultConfig);
    }

    /**
     * Creates a quick pagination with custom config.
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
     */
    public <T> CompletableFuture<PaginatedResult<T>> paginateAsync(Collection<T> items, int pageNumber) {
        return CompletableFuture.supplyAsync(() -> paginate(items, pageNumber));
    }

    /**
     * Registers a named pagination service.
     */
    public <T> void registerService(String name, PaginationService<T> service) {
        Objects.requireNonNull(name, "Service name cannot be null");
        Objects.requireNonNull(service, "Service cannot be null");
        services.put(name, service);
    }

    /**
     * Gets a registered service by name.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<PaginationService<T>> getService(String name) {
        return Optional.ofNullable((PaginationService<T>) services.get(name));
    }

    /**
     * Removes a registered service.
     */
    public void unregisterService(String name) {
        services.remove(name);
    }

    /**
     * Lists all registered service names.
     */
    public Set<String> listServices() {
        return Collections.unmodifiableSet(services.keySet());
    }

    /**
     * Gets a page from a named service.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<PaginatedResult<T>> getPage(String serviceName, int pageNumber) {
        return getService(serviceName)
                .map(service -> (PaginatedResult<T>) service.getPage(pageNumber));
    }

    /**
     * Gets a page asynchronously from a named service.
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Optional<PaginatedResult<T>>> getPageAsync(String serviceName, int pageNumber) {
        Optional<PaginationService<T>> serviceOpt = getService(serviceName);

        if (serviceOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return serviceOpt.get()
                .getPageAsync(pageNumber)
                .thenApply(Optional::of);
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
     * Returns the default configuration.
     */
    public PaginationConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * Creates a new service builder with default config.
     */
    public <T> PaginationService.Builder<T> newServiceBuilder() {
        return PaginationService.<T>builder().config(defaultConfig);
    }

    /**
     * Creates a service for a data source and registers it.
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

    public static final class Builder {
        private PaginationConfig defaultConfig = PaginationConfig.defaults();
        private final Map<String, PaginationService<?>> services = new HashMap<>();
        private LruPaginationCache<Object, Object> sharedCache;

        private Builder() {}

        public Builder defaultConfig(PaginationConfig config) {
            this.defaultConfig = Objects.requireNonNull(config, "Config cannot be null");
            return this;
        }

        public <T> Builder registerService(String name, PaginationService<T> service) {
            services.put(
                    Objects.requireNonNull(name, "Name cannot be null"),
                    Objects.requireNonNull(service, "Service cannot be null")
            );
            return this;
        }

        public Builder withSharedCache() {
            this.sharedCache = LruPaginationCache.fromConfig(defaultConfig);
            return this;
        }

        public Builder withSharedCache(LruPaginationCache<Object, Object> cache) {
            this.sharedCache = cache;
            return this;
        }

        public PaginationManager build() {
            return new PaginationManager(this);
        }
    }
}
