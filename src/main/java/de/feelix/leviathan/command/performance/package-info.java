/**
 * Performance optimization utilities for the Leviathan command framework.
 * <p>
 * This package provides tools and utilities for improving command execution
 * performance, reducing memory allocations, and caching expensive operations.
 *
 * <h2>Core Components</h2>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.ObjectPool}</h3>
 * <p>Thread-safe object pooling to reduce garbage collection pressure.
 * Use for frequently created short-lived objects like StringBuilders.
 * <pre>{@code
 * ObjectPool<StringBuilder> pool = ObjectPool.create(
 *     StringBuilder::new,
 *     sb -> sb.setLength(0),
 *     50
 * );
 *
 * String result = pool.withPooled(sb -> {
 *     sb.append("Hello ").append("World");
 *     return sb.toString();
 * });
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.ArgumentCache}</h3>
 * <p>Caches frequently-used argument values like player names, world names, and materials.
 * <pre>{@code
 * // Get cached player names
 * List<String> players = ArgumentCache.getPlayerNames();
 *
 * // Cache custom values
 * String result = ArgumentCache.getOrCompute("myKey", () -> expensiveOp(), 5, TimeUnit.MINUTES);
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.LazyArgument}</h3>
 * <p>Defers argument parsing until the value is actually needed.
 * Useful when some arguments may not be used in all code paths.
 * <pre>{@code
 * LazyArgument<Player> lazyPlayer = LazyArgument.of("Notch", playerParser, sender);
 *
 * // Value is parsed only when get() is called
 * if (someCondition) {
 *     Player player = lazyPlayer.get();
 * }
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.ParallelParser}</h3>
 * <p>Parses independent arguments in parallel using a thread pool.
 * <pre>{@code
 * ParallelParser parser = ParallelParser.create();
 *
 * Map<String, Object> results = parser.batch()
 *     .add("player", "Notch", playerParser)
 *     .add("world", "world", worldParser)
 *     .add("amount", "100", intParser)
 *     .execute(sender);
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.CommandPrecompiler}</h3>
 * <p>Pre-compiles command structures at registration time for faster runtime access.
 * <pre>{@code
 * CompiledCommand compiled = CommandPrecompiler.compile("mycommand", args);
 *
 * int index = compiled.getArgumentIndex("player");
 * Pattern pattern = compiled.getValidationPattern("email");
 * List<String> completions = compiled.getStaticCompletions("gamemode");
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.ResultCache}</h3>
 * <p>Caches expensive command execution results.
 * <pre>{@code
 * ResultCache cache = ResultCache.create(5, TimeUnit.MINUTES);
 *
 * Object result = cache.getOrCompute(
 *     CacheKeyBuilder.forCommand("stats")
 *         .withSender(sender)
 *         .withArg("type", "weekly")
 *         .build(),
 *     () -> computeWeeklyStats()
 * );
 * }</pre>
 *
 * <h3>{@link de.feelix.leviathan.command.performance.PerformanceManager}</h3>
 * <p>Central management class for all performance features.
 * <pre>{@code
 * // Get the global manager
 * PerformanceManager perf = PerformanceManager.getInstance();
 *
 * // Access individual components
 * ObjectPool<StringBuilder> sbPool = perf.getStringBuilderPool();
 * ResultCache resultCache = perf.getResultCache();
 *
 * // Get combined statistics
 * PerformanceStats stats = perf.getStats();
 * }</pre>
 *
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Use ObjectPool for frequently created objects in hot paths</li>
 *   <li>Use ArgumentCache for player/world/material completions</li>
 *   <li>Use LazyArgument for optional or conditionally-used arguments</li>
 *   <li>Use ParallelParser only when you have 3+ expensive arguments to parse</li>
 *   <li>Use CommandPrecompiler for commands with complex validation patterns</li>
 *   <li>Use ResultCache for expensive operations that produce stable results</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All classes in this package are designed to be thread-safe and can be used
 * safely in concurrent command execution scenarios.
 *
 * @since 1.3.0
 */
package de.feelix.leviathan.command.performance;
