package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * High-level API for applying performance optimizations to command handling.
 * <p>
 * This class provides a simplified interface for using the performance features
 * without needing to understand the underlying implementation details.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Cache an expensive operation
 * Object result = PerformanceOptimizer.cached("myCommand:stats", () -> computeStats());
 *
 * // Parse arguments lazily
 * LazyArgument<Player> player = PerformanceOptimizer.lazy("Notch", playerParser, sender);
 *
 * // Build strings efficiently
 * String message = PerformanceOptimizer.buildString(sb ->
 *     sb.append("Hello, ").append(playerName).append("!")
 * );
 *
 * // Pre-compile a command
 * PerformanceOptimizer.precompile("myCommand", args);
 * }</pre>
 */
public final class PerformanceOptimizer {

    private PerformanceOptimizer() {
        // Static utility class
    }

    // ==================== Caching ====================

    /**
     * Get or compute a cached value with default TTL (5 minutes).
     *
     * @param key      the cache key
     * @param supplier the value supplier
     * @param <T>      the value type
     * @return the cached or computed value
     */
    public static <T> @Nullable T cached(@NotNull String key, @NotNull Supplier<T> supplier) {
        return PerformanceManager.getInstance().getResultCache().getOrCompute(key, supplier);
    }

    /**
     * Get or compute a cached value with custom TTL.
     *
     * @param key      the cache key
     * @param supplier the value supplier
     * @param ttl      time-to-live
     * @param unit     time unit
     * @param <T>      the value type
     * @return the cached or computed value
     */
    public static <T> @Nullable T cached(@NotNull String key, @NotNull Supplier<T> supplier,
                                          long ttl, @NotNull TimeUnit unit) {
        return PerformanceManager.getInstance().getResultCache()
            .getOrCompute(key, supplier, unit.toMillis(ttl));
    }

    /**
     * Cache a command execution result.
     *
     * @param commandName the command name
     * @param context     the command context
     * @param sender      the sender
     * @param supplier    the value supplier
     * @param <T>         the value type
     * @return the cached or computed value
     */
    public static <T> @Nullable T cachedResult(@NotNull String commandName,
                                                @NotNull CommandContext context,
                                                @NotNull CommandSender sender,
                                                @NotNull Supplier<T> supplier) {
        return PerformanceManager.getInstance().getResultCache()
            .getOrCompute(commandName, context, sender, supplier);
    }

    /**
     * Invalidate a cache entry.
     *
     * @param key the cache key
     */
    public static void invalidateCache(@NotNull String key) {
        PerformanceManager.getInstance().getResultCache().invalidate(key);
    }

    /**
     * Invalidate all cache entries for a command.
     *
     * @param commandName the command name
     */
    public static void invalidateCommand(@NotNull String commandName) {
        PerformanceManager.getInstance().getResultCache().invalidateCommand(commandName);
    }

    // ==================== Lazy Parsing ====================

    /**
     * Create a lazy argument that defers parsing until needed.
     *
     * @param rawValue the raw value to parse
     * @param parser   the parser
     * @param sender   the command sender
     * @param <T>      the value type
     * @return a lazy argument
     */
    public static <T> @NotNull LazyArgument<T> lazy(@Nullable String rawValue,
                                                     @NotNull ArgumentParser<T> parser,
                                                     @NotNull CommandSender sender) {
        return LazyArgument.of(rawValue, parser, sender);
    }

    /**
     * Create a lazy argument from a pre-parsed value.
     *
     * @param value the value
     * @param <T>   the value type
     * @return a lazy argument with the value already set
     */
    public static <T> @NotNull LazyArgument<T> lazyValue(@Nullable T value) {
        return LazyArgument.ofValue(value);
    }

    /**
     * Create a lazy argument from a supplier.
     *
     * @param supplier the value supplier
     * @param <T>      the value type
     * @return a lazy argument
     */
    public static <T> @NotNull LazyArgument<T> lazySupplier(@NotNull Supplier<T> supplier) {
        return LazyArgument.fromSupplier(supplier);
    }

    // ==================== Parallel Parsing ====================

    /**
     * Parse multiple arguments in parallel.
     *
     * @param sender the command sender
     * @param tasks  the parsing tasks
     * @return map of argument names to parsed values
     */
    public static @NotNull Map<String, Object> parseParallel(@NotNull CommandSender sender,
                                                              @NotNull List<ParallelParser.ParseTask<?>> tasks) {
        return PerformanceManager.getInstance().getParallelParser().parseAll(sender, tasks);
    }

    /**
     * Create a new parallel parse batch.
     *
     * @return a new batch builder
     */
    public static @NotNull ParallelParser.ParseBatch parallelBatch() {
        return PerformanceManager.getInstance().getParallelParser().batch();
    }

    // ==================== Object Pooling ====================

    /**
     * Build a string using a pooled StringBuilder.
     *
     * @param builder the builder function
     * @return the built string
     */
    public static @NotNull String buildString(@NotNull java.util.function.Consumer<StringBuilder> builder) {
        return PerformanceManager.getInstance().buildString(builder);
    }

    /**
     * Execute an action with a pooled StringBuilder.
     *
     * @param action the action
     * @param <R>    the result type
     * @return the action result
     */
    public static <R> R withStringBuilder(@NotNull Function<StringBuilder, R> action) {
        return PerformanceManager.getInstance().getStringBuilderPool().withPooled(action);
    }

    /**
     * Execute an action with a pooled ArrayList.
     *
     * @param action the action
     * @param <T>    the list element type
     * @param <R>    the result type
     * @return the action result
     */
    public static <T, R> R withArrayList(@NotNull Function<ArrayList<T>, R> action) {
        ObjectPool<ArrayList<T>> pool = PerformanceManager.getInstance().getArrayListPool();
        return pool.withPooled(action);
    }

    /**
     * Execute an action with a pooled HashMap.
     *
     * @param action the action
     * @param <K>    the map key type
     * @param <V>    the map value type
     * @param <R>    the result type
     * @return the action result
     */
    public static <K, V, R> R withHashMap(@NotNull Function<HashMap<K, V>, R> action) {
        ObjectPool<HashMap<K, V>> pool = PerformanceManager.getInstance().getHashMapPool();
        return pool.withPooled(action);
    }

    // ==================== Command Precompilation ====================

    /**
     * Pre-compile a command structure.
     *
     * @param commandName the command name
     * @param args        the argument list
     * @return the compiled command
     */
    public static @NotNull CommandPrecompiler.CompiledCommand precompile(@NotNull String commandName,
                                                                          @NotNull List<Arg<?>> args) {
        return CommandPrecompiler.compile(commandName, args);
    }

    /**
     * Get a previously compiled command.
     *
     * @param commandName the command name
     * @return the compiled command, or null if not found
     */
    public static @Nullable CommandPrecompiler.CompiledCommand getCompiled(@NotNull String commandName) {
        return CommandPrecompiler.get(commandName);
    }

    /**
     * Check if a command is pre-compiled.
     *
     * @param commandName the command name
     * @return true if pre-compiled
     */
    public static boolean isPrecompiled(@NotNull String commandName) {
        return CommandPrecompiler.isCompiled(commandName);
    }

    // ==================== Argument Caching ====================

    /**
     * Get cached player names (refreshed every 5 seconds).
     *
     * @return list of online player names
     */
    public static @NotNull List<String> cachedPlayerNames() {
        return ArgumentCache.getPlayerNames();
    }

    /**
     * Get cached world names.
     *
     * @return list of world names
     */
    public static @NotNull List<String> cachedWorldNames() {
        return ArgumentCache.getWorldNames();
    }

    /**
     * Get cached material names.
     *
     * @return list of material names
     */
    public static @NotNull List<String> cachedMaterialNames() {
        return ArgumentCache.getMaterialNames();
    }

    /**
     * Get player names starting with a prefix (for tab completion).
     *
     * @param prefix the prefix
     * @return matching player names
     */
    public static @NotNull List<String> playerNamesStartingWith(@NotNull String prefix) {
        return ArgumentCache.getPlayerNamesStartingWith(prefix);
    }

    /**
     * Get material names starting with a prefix.
     *
     * @param prefix the prefix
     * @return matching material names
     */
    public static @NotNull List<String> materialNamesStartingWith(@NotNull String prefix) {
        return ArgumentCache.getMaterialNamesStartingWith(prefix);
    }

    // ==================== Statistics ====================

    /**
     * Get combined performance statistics.
     *
     * @return statistics snapshot
     */
    public static @NotNull PerformanceManager.PerformanceStats getStats() {
        return PerformanceManager.getInstance().getStats();
    }

    /**
     * Get a compact statistics summary.
     *
     * @return summary string
     */
    public static @NotNull String getStatsSummary() {
        return getStats().toSummary();
    }

    /**
     * Reset all statistics counters.
     */
    public static void resetStats() {
        PerformanceManager.getInstance().resetStats();
    }

    // ==================== Configuration ====================

    /**
     * Enable or disable performance optimizations globally.
     *
     * @param enabled true to enable
     */
    public static void setEnabled(boolean enabled) {
        PerformanceManager.getInstance().setEnabled(enabled);
    }

    /**
     * Check if performance optimizations are enabled.
     *
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return PerformanceManager.getInstance().isEnabled();
    }

    /**
     * Clear all caches.
     */
    public static void clearAllCaches() {
        PerformanceManager.getInstance().clearCaches();
    }

    /**
     * Prewarm object pools.
     */
    public static void prewarmPools() {
        PerformanceManager.getInstance().prewarmPools();
    }

    // ==================== Lifecycle ====================

    /**
     * Initialize the performance system with custom configuration.
     *
     * @param config the configuration
     */
    public static void initialize(@NotNull PerformanceManager.PerformanceConfig config) {
        PerformanceManager.initialize(config);
    }

    /**
     * Shutdown the performance system.
     * <p>
     * Should be called on plugin disable.
     */
    public static void shutdown() {
        PerformanceManager.getInstance().shutdown();
    }
}
