package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Executor for batch command operations.
 * <p>
 * Supports both sequential and parallel execution of batch operations,
 * with progress tracking, cancellation, and result aggregation.
 * <p>
 * Example usage:
 * <pre>{@code
 * List<Player> players = // get players
 * BatchAction<Player> action = (player, ctx) -> player.heal(20);
 *
 * BatchResult<Player> result = BatchExecutor.execute(
 *     plugin,
 *     sender,
 *     commandContext,
 *     players,
 *     action,
 *     BatchConfig.parallel()
 * );
 *
 * sender.sendMessage(result.compactSummary());
 * }</pre>
 */
public final class BatchExecutor {

    private static final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Leviathan-Batch-Worker");
        t.setDaemon(true);
        return t;
    });

    private BatchExecutor() {
        throw new AssertionError("Utility class");
    }

    /**
     * Execute a batch operation.
     *
     * @param plugin         the plugin instance
     * @param sender         the command sender
     * @param commandContext the command context
     * @param targets        the list of targets
     * @param action         the action to execute for each target
     * @param config         the batch configuration
     * @param <T>            the target type
     * @return the batch result
     */
    public static <T> @NotNull BatchResult<T> execute(
            @NotNull JavaPlugin plugin,
            @NotNull CommandSender sender,
            @NotNull CommandContext commandContext,
            @NotNull List<T> targets,
            @NotNull BatchAction<T> action,
            @NotNull BatchConfig config) {

        Preconditions.checkNotNull(plugin, "plugin");
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(commandContext, "commandContext");
        Preconditions.checkNotNull(targets, "targets");
        Preconditions.checkNotNull(action, "action");
        Preconditions.checkNotNull(config, "config");

        // Validate batch size
        if (!config.isValidSize(targets.size())) {
            BatchResult.Builder<T> builder = BatchResult.builder();
            for (T target : targets) {
                builder.addSkipped(target, "Batch size exceeds maximum of " + config.maxBatchSize());
            }
            return builder.build();
        }

        // Empty batch
        if (targets.isEmpty()) {
            return BatchResult.empty();
        }

        // Create batch context
        BatchContext<T> batchContext = BatchContext.create(sender, commandContext, targets, config);

        // Execute based on configuration
        if (config.isParallel()) {
            return executeParallel(plugin, batchContext, action, config);
        } else {
            return executeSequential(plugin, batchContext, action, config);
        }
    }

    /**
     * Execute a batch operation asynchronously.
     *
     * @param plugin         the plugin instance
     * @param sender         the command sender
     * @param commandContext the command context
     * @param targets        the list of targets
     * @param action         the action to execute for each target
     * @param config         the batch configuration
     * @param <T>            the target type
     * @return a future that completes with the batch result
     */
    public static <T> @NotNull CompletableFuture<BatchResult<T>> executeAsync(
            @NotNull JavaPlugin plugin,
            @NotNull CommandSender sender,
            @NotNull CommandContext commandContext,
            @NotNull List<T> targets,
            @NotNull BatchAction<T> action,
            @NotNull BatchConfig config) {

        return CompletableFuture.supplyAsync(
                () -> execute(plugin, sender, commandContext, targets, action, config),
                SHARED_EXECUTOR
        );
    }

    /**
     * Execute a batch sequentially.
     */
    private static <T> @NotNull BatchResult<T> executeSequential(
            @NotNull JavaPlugin plugin,
            @NotNull BatchContext<T> context,
            @NotNull BatchAction<T> action,
            @NotNull BatchConfig config) {

        BatchResult.Builder<T> resultBuilder = BatchResult.<T>builder().start().parallel(false);
        List<T> targets = context.targets();

        for (int i = 0; i < targets.size(); i++) {
            // Check cancellation
            if (context.isCancelled()) {
                // Skip remaining targets
                for (int j = i; j < targets.size(); j++) {
                    resultBuilder.addSkipped(targets.get(j), "Batch cancelled");
                }
                break;
            }

            // Check timeout
            if (config.isTimedOut((long) context.elapsedTimeMillis())) {
                for (int j = i; j < targets.size(); j++) {
                    resultBuilder.addSkipped(targets.get(j), "Batch timed out");
                }
                break;
            }

            T target = targets.get(i);
            context.setCurrentIndex(i);

            long startNanos = System.nanoTime();
            try {
                action.execute(target, context);
                long elapsed = System.nanoTime() - startNanos;
                resultBuilder.addSuccess(target, elapsed);
                context.recordSuccess();
            } catch (Exception e) {
                long elapsed = System.nanoTime() - startNanos;
                resultBuilder.addFailure(target, e, elapsed);
                context.recordFailure();

                // Check if we should stop on failure
                if (!config.continueOnFailure()) {
                    for (int j = i + 1; j < targets.size(); j++) {
                        resultBuilder.addSkipped(targets.get(j), "Stopped after failure");
                    }
                    break;
                }
            }

            // Show progress if configured
            if (config.shouldShowProgress(context.processedCount())) {
                context.sender().sendMessage(context.progressMessage());
            }
        }

        return resultBuilder.build();
    }

    /**
     * Execute a batch in parallel.
     */
    private static <T> @NotNull BatchResult<T> executeParallel(
            @NotNull JavaPlugin plugin,
            @NotNull BatchContext<T> context,
            @NotNull BatchAction<T> action,
            @NotNull BatchConfig config) {

        BatchResult.Builder<T> resultBuilder = BatchResult.<T>builder().start().parallel(true);
        List<T> targets = context.targets();
        int parallelism = config.parallelism();

        // Use semaphore to control parallelism
        Semaphore semaphore = new Semaphore(parallelism);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Create array to hold results (indexed by position)
        @SuppressWarnings("unchecked")
        BatchEntry<T>[] results = new BatchEntry[targets.size()];

        // Submit all tasks
        CompletableFuture<?>[] futures = new CompletableFuture[targets.size()];

        for (int i = 0; i < targets.size(); i++) {
            final int index = i;
            final T target = targets.get(i);

            futures[i] = CompletableFuture.runAsync(() -> {
                // Check if cancelled before starting
                if (context.isCancelled()) {
                    results[index] = BatchEntry.skipped(target, "Batch cancelled", index);
                    return;
                }

                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results[index] = BatchEntry.skipped(target, "Interrupted", index);
                    return;
                }

                try {
                    // Check timeout
                    if (config.isTimedOut((long) context.elapsedTimeMillis())) {
                        results[index] = BatchEntry.skipped(target, "Batch timed out", index);
                        return;
                    }

                    long startNanos = System.nanoTime();
                    try {
                        action.execute(target, context);
                        long elapsed = System.nanoTime() - startNanos;
                        results[index] = BatchEntry.success(target, elapsed, index);
                        context.recordSuccess();
                    } catch (Exception e) {
                        long elapsed = System.nanoTime() - startNanos;
                        results[index] = BatchEntry.failure(target, e, elapsed, index);
                        context.recordFailure();

                        // If not continuing on failure, cancel remaining
                        if (!config.continueOnFailure()) {
                            context.cancel();
                        }
                    }

                    // Progress update
                    int completed = completedCount.incrementAndGet();
                    if (config.shouldShowProgress(completed)) {
                        // Run on main thread for thread safety
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            context.sender().sendMessage(context.progressMessage());
                        });
                    }

                } finally {
                    semaphore.release();
                }
            }, SHARED_EXECUTOR);
        }

        // Wait for all tasks to complete
        try {
            CompletableFuture.allOf(futures).get(
                    config.timeoutMillis() > 0 ? config.timeoutMillis() : Long.MAX_VALUE,
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            // Timeout or interruption - mark remaining as skipped
            plugin.getLogger().log(Level.WARNING, "Batch execution interrupted", e);
        }

        // Collect results
        for (int i = 0; i < results.length; i++) {
            if (results[i] != null) {
                resultBuilder.addEntry(results[i]);
            } else {
                resultBuilder.addSkipped(targets.get(i), "Not completed");
            }
        }

        return resultBuilder.build();
    }

    /**
     * Shutdown the shared executor.
     * Call this when the plugin is disabled.
     */
    public static void shutdown() {
        SHARED_EXECUTOR.shutdown();
        try {
            if (!SHARED_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                SHARED_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            SHARED_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
