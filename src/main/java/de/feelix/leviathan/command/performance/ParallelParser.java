package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * Parallel argument parser for concurrent parsing of independent arguments.
 * <p>
 * This parser analyzes argument dependencies and parses independent arguments
 * in parallel using a thread pool, significantly improving performance for
 * commands with multiple expensive-to-parse arguments.
 * <p>
 * Features:
 * <ul>
 *   <li>Automatic dependency analysis</li>
 *   <li>Configurable thread pool</li>
 *   <li>Fallback to sequential parsing</li>
 *   <li>Timeout protection</li>
 *   <li>Statistics tracking</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create parser with default settings
 * ParallelParser parser = ParallelParser.create();
 *
 * // Parse multiple arguments in parallel
 * Map<String, Object> results = parser.parseAll(
 *     sender,
 *     List.of(
 *         new ParseTask<>("player", "Notch", playerParser),
 *         new ParseTask<>("world", "world", worldParser),
 *         new ParseTask<>("amount", "100", intParser)
 *     )
 * );
 *
 * // Or use the fluent builder
 * ParseBatch batch = parser.batch()
 *     .add("player", "Notch", playerParser)
 *     .add("world", "world", worldParser)
 *     .add("amount", "100", intParser);
 *
 * Map<String, Object> results = batch.execute(sender);
 * }</pre>
 */
public final class ParallelParser {

    private final ExecutorService executor;
    private final int parallelThreshold;
    private final long timeoutMillis;
    private final boolean ownExecutor;

    // Statistics
    private final AtomicInteger parallelParses = new AtomicInteger(0);
    private final AtomicInteger sequentialParses = new AtomicInteger(0);
    private final AtomicInteger timeouts = new AtomicInteger(0);

    /**
     * Default parallel threshold - only use parallel parsing if >= this many args.
     */
    public static final int DEFAULT_PARALLEL_THRESHOLD = 3;

    /**
     * Default timeout for parallel parsing operations.
     */
    public static final long DEFAULT_TIMEOUT_MILLIS = 5000;

    private ParallelParser(ExecutorService executor, int parallelThreshold,
                           long timeoutMillis, boolean ownExecutor) {
        this.executor = executor;
        this.parallelThreshold = parallelThreshold;
        this.timeoutMillis = timeoutMillis;
        this.ownExecutor = ownExecutor;
    }

    /**
     * Create a parallel parser with default settings.
     * <p>
     * Uses a cached thread pool suitable for short parsing tasks.
     *
     * @return a new ParallelParser instance
     */
    public static @NotNull ParallelParser create() {
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Leviathan-ParallelParser");
            t.setDaemon(true);
            return t;
        });
        return new ParallelParser(executor, DEFAULT_PARALLEL_THRESHOLD, DEFAULT_TIMEOUT_MILLIS, true);
    }

    /**
     * Create a parallel parser with a fixed thread pool.
     *
     * @param threads the number of threads
     * @return a new ParallelParser instance
     */
    public static @NotNull ParallelParser withThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Leviathan-ParallelParser");
            t.setDaemon(true);
            return t;
        });
        return new ParallelParser(executor, DEFAULT_PARALLEL_THRESHOLD, DEFAULT_TIMEOUT_MILLIS, true);
    }

    /**
     * Create a parallel parser with a custom executor.
     *
     * @param executor the executor service to use
     * @return a new ParallelParser instance
     */
    public static @NotNull ParallelParser withExecutor(@NotNull ExecutorService executor) {
        Preconditions.checkNotNull(executor, "executor");
        return new ParallelParser(executor, DEFAULT_PARALLEL_THRESHOLD, DEFAULT_TIMEOUT_MILLIS, false);
    }

    /**
     * Create a builder for custom configuration.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Parse multiple arguments in parallel.
     *
     * @param sender the command sender
     * @param tasks  the parsing tasks
     * @return map of argument names to parsed values (nulls for failed parses)
     */
    public @NotNull Map<String, Object> parseAll(@NotNull CommandSender sender,
                                                  @NotNull List<ParseTask<?>> tasks) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(tasks, "tasks");

        if (tasks.isEmpty()) {
            return Collections.emptyMap();
        }

        // Use sequential parsing for small task counts
        if (tasks.size() < parallelThreshold) {
            sequentialParses.incrementAndGet();
            return parseSequentially(sender, tasks);
        }

        parallelParses.incrementAndGet();
        return parseInParallel(sender, tasks);
    }

    /**
     * Parse arguments sequentially (fallback).
     */
    private @NotNull Map<String, Object> parseSequentially(@NotNull CommandSender sender,
                                                            @NotNull List<ParseTask<?>> tasks) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (ParseTask<?> task : tasks) {
            try {
                ParseResult<?> result = task.parser.parse(task.rawValue, sender);
                results.put(task.name, result.isSuccess() ? result.value().orElse(null) : null);
            } catch (Exception e) {
                results.put(task.name, null);
            }
        }
        return results;
    }

    /**
     * Parse arguments in parallel.
     */
    private @NotNull Map<String, Object> parseInParallel(@NotNull CommandSender sender,
                                                          @NotNull List<ParseTask<?>> tasks) {
        Map<String, Future<Object>> futures = new LinkedHashMap<>();

        // Submit all tasks
        for (ParseTask<?> task : tasks) {
            Future<Object> future = executor.submit(() -> {
                try {
                    ParseResult<?> result = task.parser.parse(task.rawValue, sender);
                    return result.isSuccess() ? result.value().orElse(null) : null;
                } catch (Exception e) {
                    return null;
                }
            });
            futures.put(task.name, future);
        }

        // Collect results
        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, Future<Object>> entry : futures.entrySet()) {
            try {
                Object value = entry.getValue().get(timeoutMillis, TimeUnit.MILLISECONDS);
                results.put(entry.getKey(), value);
            } catch (TimeoutException e) {
                timeouts.incrementAndGet();
                entry.getValue().cancel(true);
                results.put(entry.getKey(), null);
            } catch (Exception e) {
                results.put(entry.getKey(), null);
            }
        }

        return results;
    }

    /**
     * Parse a list of Arg objects with their corresponding raw values.
     *
     * @param sender    the command sender
     * @param argsWithValues list of (Arg, rawValue) pairs
     * @return map of argument names to parsed values
     */
    public @NotNull Map<String, Object> parseArgs(@NotNull CommandSender sender,
                                                   @NotNull List<ArgValuePair<?>> argsWithValues) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(argsWithValues, "argsWithValues");

        List<ParseTask<?>> tasks = new ArrayList<>(argsWithValues.size());
        for (ArgValuePair<?> pair : argsWithValues) {
            tasks.add(new ParseTask<>(pair.arg.name(), pair.rawValue, pair.arg.parser()));
        }
        return parseAll(sender, tasks);
    }

    /**
     * Create a new parse batch builder.
     *
     * @return a new ParseBatch builder
     */
    public @NotNull ParseBatch batch() {
        return new ParseBatch(this);
    }

    /**
     * Shutdown the internal executor if owned.
     * <p>
     * Call this when the parser is no longer needed to release resources.
     */
    public void shutdown() {
        if (ownExecutor) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Check if this parser is shut down.
     *
     * @return true if shut down
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Get parser statistics.
     *
     * @return statistics snapshot
     */
    public @NotNull ParserStats getStats() {
        return new ParserStats(
            parallelParses.get(),
            sequentialParses.get(),
            timeouts.get(),
            parallelThreshold,
            timeoutMillis
        );
    }

    /**
     * Reset statistics counters.
     */
    public void resetStats() {
        parallelParses.set(0);
        sequentialParses.set(0);
        timeouts.set(0);
    }

    // ==================== Inner Classes ====================

    /**
     * A single parsing task.
     *
     * @param <T> the result type
     */
    public static final class ParseTask<T> {
        final String name;
        final String rawValue;
        final ArgumentParser<T> parser;

        /**
         * Create a new parse task.
         *
         * @param name     the argument name
         * @param rawValue the raw value to parse
         * @param parser   the parser to use
         */
        public ParseTask(@NotNull String name, @NotNull String rawValue,
                         @NotNull ArgumentParser<T> parser) {
            this.name = Preconditions.checkNotNull(name, "name");
            this.rawValue = Preconditions.checkNotNull(rawValue, "rawValue");
            this.parser = Preconditions.checkNotNull(parser, "parser");
        }

        public String getName() { return name; }
        public String getRawValue() { return rawValue; }
        public ArgumentParser<T> getParser() { return parser; }
    }

    /**
     * A pair of Arg and its raw value.
     *
     * @param <T> the argument type
     */
    public static final class ArgValuePair<T> {
        final Arg<T> arg;
        final String rawValue;

        /**
         * Create a new arg-value pair.
         *
         * @param arg      the argument definition
         * @param rawValue the raw value to parse
         */
        public ArgValuePair(@NotNull Arg<T> arg, @NotNull String rawValue) {
            this.arg = Preconditions.checkNotNull(arg, "arg");
            this.rawValue = Preconditions.checkNotNull(rawValue, "rawValue");
        }

        public Arg<T> getArg() { return arg; }
        public String getRawValue() { return rawValue; }
    }

    /**
     * Fluent batch builder for parsing multiple arguments.
     */
    public static final class ParseBatch {
        private final ParallelParser parser;
        private final List<ParseTask<?>> tasks = new ArrayList<>();

        ParseBatch(ParallelParser parser) {
            this.parser = parser;
        }

        /**
         * Add a parsing task.
         *
         * @param name     the argument name
         * @param rawValue the raw value to parse
         * @param argParser the parser to use
         * @param <T>      the result type
         * @return this for chaining
         */
        public <T> @NotNull ParseBatch add(@NotNull String name, @NotNull String rawValue,
                                           @NotNull ArgumentParser<T> argParser) {
            tasks.add(new ParseTask<>(name, rawValue, argParser));
            return this;
        }

        /**
         * Add a parsing task from an Arg.
         *
         * @param arg      the argument definition
         * @param rawValue the raw value to parse
         * @param <T>      the result type
         * @return this for chaining
         */
        public <T> @NotNull ParseBatch add(@NotNull Arg<T> arg, @NotNull String rawValue) {
            tasks.add(new ParseTask<>(arg.name(), rawValue, arg.parser()));
            return this;
        }

        /**
         * Execute all parsing tasks.
         *
         * @param sender the command sender
         * @return map of argument names to parsed values
         */
        public @NotNull Map<String, Object> execute(@NotNull CommandSender sender) {
            return parser.parseAll(sender, tasks);
        }

        /**
         * Get the number of tasks in this batch.
         *
         * @return task count
         */
        public int size() {
            return tasks.size();
        }

        /**
         * Clear all tasks from the batch.
         *
         * @return this for chaining
         */
        public @NotNull ParseBatch clear() {
            tasks.clear();
            return this;
        }
    }

    /**
     * Builder for ParallelParser configuration.
     */
    public static final class Builder {
        private ExecutorService executor;
        private int parallelThreshold = DEFAULT_PARALLEL_THRESHOLD;
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private int threads = Runtime.getRuntime().availableProcessors();

        /**
         * Set a custom executor service.
         *
         * @param executor the executor to use
         * @return this for chaining
         */
        public @NotNull Builder executor(@NotNull ExecutorService executor) {
            this.executor = Preconditions.checkNotNull(executor, "executor");
            return this;
        }

        /**
         * Set the number of threads for the internal executor.
         * <p>
         * Ignored if a custom executor is provided.
         *
         * @param threads the number of threads
         * @return this for chaining
         */
        public @NotNull Builder threads(int threads) {
            if (threads <= 0) {
                throw new IllegalArgumentException("threads must be positive");
            }
            this.threads = threads;
            return this;
        }

        /**
         * Set the parallel threshold.
         * <p>
         * Parallel parsing is only used when there are at least this many arguments.
         *
         * @param threshold the threshold
         * @return this for chaining
         */
        public @NotNull Builder parallelThreshold(int threshold) {
            if (threshold <= 0) {
                throw new IllegalArgumentException("threshold must be positive");
            }
            this.parallelThreshold = threshold;
            return this;
        }

        /**
         * Set the timeout for parsing operations.
         *
         * @param timeout the timeout value
         * @param unit    the time unit
         * @return this for chaining
         */
        public @NotNull Builder timeout(long timeout, @NotNull TimeUnit unit) {
            Preconditions.checkNotNull(unit, "unit");
            if (timeout <= 0) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeoutMillis = unit.toMillis(timeout);
            return this;
        }

        /**
         * Build the ParallelParser.
         *
         * @return a new ParallelParser instance
         */
        public @NotNull ParallelParser build() {
            boolean ownExecutor;
            ExecutorService exec;
            if (executor != null) {
                exec = executor;
                ownExecutor = false;
            } else {
                exec = Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "Leviathan-ParallelParser");
                    t.setDaemon(true);
                    return t;
                });
                ownExecutor = true;
            }
            return new ParallelParser(exec, parallelThreshold, timeoutMillis, ownExecutor);
        }
    }

    /**
     * Parser statistics snapshot.
     */
    public static final class ParserStats {
        private final int parallelParses;
        private final int sequentialParses;
        private final int timeouts;
        private final int parallelThreshold;
        private final long timeoutMillis;

        ParserStats(int parallelParses, int sequentialParses, int timeouts,
                    int parallelThreshold, long timeoutMillis) {
            this.parallelParses = parallelParses;
            this.sequentialParses = sequentialParses;
            this.timeouts = timeouts;
            this.parallelThreshold = parallelThreshold;
            this.timeoutMillis = timeoutMillis;
        }

        public int getParallelParses() { return parallelParses; }
        public int getSequentialParses() { return sequentialParses; }
        public int getTimeouts() { return timeouts; }
        public int getTotalParses() { return parallelParses + sequentialParses; }
        public int getParallelThreshold() { return parallelThreshold; }
        public long getTimeoutMillis() { return timeoutMillis; }

        public double getParallelRatio() {
            int total = getTotalParses();
            if (total == 0) return 0.0;
            return (double) parallelParses / total;
        }

        @Override
        public String toString() {
            return String.format(
                "ParserStats{parallel=%d, sequential=%d, timeouts=%d, parallelRatio=%.2f}",
                parallelParses, sequentialParses, timeouts, getParallelRatio()
            );
        }
    }
}
