package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context for batch command execution, providing access to targets and shared state.
 * <p>
 * BatchContext extends the regular CommandContext with batch-specific functionality:
 * <ul>
 *   <li>Access to the list of targets being processed</li>
 *   <li>Current target and index during iteration</li>
 *   <li>Shared state across batch entries</li>
 *   <li>Cancellation support</li>
 *   <li>Progress tracking</li>
 * </ul>
 *
 * @param <T> the type of entities in the batch
 */
public final class BatchContext<T> {

    private final CommandSender sender;
    private final CommandContext commandContext;
    private final List<T> targets;
    private final BatchConfig config;
    private final Map<String, Object> sharedState;
    private final AtomicInteger currentIndex;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;
    private final AtomicBoolean cancelled;
    private final long startTimeNanos;

    private BatchContext(CommandSender sender, CommandContext commandContext, List<T> targets, BatchConfig config) {
        this.sender = Preconditions.checkNotNull(sender, "sender");
        this.commandContext = Preconditions.checkNotNull(commandContext, "commandContext");
        this.targets = Collections.unmodifiableList(Preconditions.checkNotNull(targets, "targets"));
        this.config = Preconditions.checkNotNull(config, "config");
        this.sharedState = new ConcurrentHashMap<>();
        this.currentIndex = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.cancelled = new AtomicBoolean(false);
        this.startTimeNanos = System.nanoTime();
    }

    /**
     * Create a new batch context.
     *
     * @param sender         the command sender
     * @param commandContext the parsed command context
     * @param targets        the list of targets
     * @param config         the batch configuration
     * @param <T>            the target type
     * @return a new batch context
     */
    public static <T> @NotNull BatchContext<T> create(
            @NotNull CommandSender sender,
            @NotNull CommandContext commandContext,
            @NotNull List<T> targets,
            @NotNull BatchConfig config) {
        return new BatchContext<>(sender, commandContext, targets, config);
    }

    // ==================== Core Accessors ====================

    /**
     * @return the command sender
     */
    public @NotNull CommandSender sender() {
        return sender;
    }

    /**
     * @return the original command context
     */
    public @NotNull CommandContext commandContext() {
        return commandContext;
    }

    /**
     * @return the full list of targets
     */
    public @NotNull List<T> targets() {
        return targets;
    }

    /**
     * @return the batch configuration
     */
    public @NotNull BatchConfig config() {
        return config;
    }

    /**
     * @return the total number of targets
     */
    public int size() {
        return targets.size();
    }

    /**
     * @return true if there are no targets
     */
    public boolean isEmpty() {
        return targets.isEmpty();
    }

    // ==================== Current Iteration State ====================

    /**
     * @return the current target being processed
     */
    public @Nullable T currentTarget() {
        int idx = currentIndex.get();
        if (idx >= 0 && idx < targets.size()) {
            return targets.get(idx);
        }
        return null;
    }

    /**
     * @return the current index in the batch
     */
    public int currentIndex() {
        return currentIndex.get();
    }

    /**
     * Advance to the next target.
     *
     * @return the new index, or -1 if no more targets
     */
    public int advance() {
        int newIndex = currentIndex.incrementAndGet();
        return newIndex < targets.size() ? newIndex : -1;
    }

    /**
     * Set the current index.
     *
     * @param index the new index
     */
    public void setCurrentIndex(int index) {
        currentIndex.set(index);
    }

    // ==================== Progress Tracking ====================

    /**
     * Record a successful operation.
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
    }

    /**
     * Record a failed operation.
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
    }

    /**
     * @return the number of successful operations so far
     */
    public int successCount() {
        return successCount.get();
    }

    /**
     * @return the number of failed operations so far
     */
    public int failureCount() {
        return failureCount.get();
    }

    /**
     * @return the number of processed entries (success + failure)
     */
    public int processedCount() {
        return successCount.get() + failureCount.get();
    }

    /**
     * @return the progress as a percentage (0.0 to 1.0)
     */
    public double progress() {
        if (targets.isEmpty()) {
            return 1.0;
        }
        return (double) processedCount() / targets.size();
    }

    /**
     * @return the progress as a percentage (0 to 100)
     */
    public int progressPercent() {
        return (int) (progress() * 100);
    }

    /**
     * @return the elapsed time in nanoseconds
     */
    public long elapsedTimeNanos() {
        return System.nanoTime() - startTimeNanos;
    }

    /**
     * @return the elapsed time in milliseconds
     */
    public double elapsedTimeMillis() {
        return elapsedTimeNanos() / 1_000_000.0;
    }

    // ==================== Cancellation ====================

    /**
     * Request cancellation of the batch operation.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * @return true if cancellation has been requested
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * @return true if the batch should continue processing
     */
    public boolean shouldContinue() {
        return !cancelled.get();
    }

    // ==================== Shared State ====================

    /**
     * Store a value in the shared state.
     *
     * @param key   the key
     * @param value the value
     */
    public void put(@NotNull String key, @NotNull Object value) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(value, "value");
        sharedState.put(key, value);
    }

    /**
     * Retrieve a value from the shared state.
     *
     * @param key  the key
     * @param type the expected type
     * @param <V>  the value type
     * @return the value, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public @Nullable <V> V get(@NotNull String key, @NotNull Class<V> type) {
        Preconditions.checkNotNull(key, "key");
        Preconditions.checkNotNull(type, "type");
        Object value = sharedState.get(key);
        if (value != null && type.isInstance(value)) {
            return (V) value;
        }
        return null;
    }

    /**
     * Retrieve a value from the shared state with a default.
     *
     * @param key          the key
     * @param type         the expected type
     * @param defaultValue the default value
     * @param <V>          the value type
     * @return the value, or the default if not present or wrong type
     */
    public @NotNull <V> V getOrDefault(@NotNull String key, @NotNull Class<V> type, @NotNull V defaultValue) {
        V value = get(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Check if a key exists in the shared state.
     *
     * @param key the key
     * @return true if the key exists
     */
    public boolean has(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        return sharedState.containsKey(key);
    }

    /**
     * Remove a value from the shared state.
     *
     * @param key the key
     * @return the removed value, or null
     */
    public @Nullable Object remove(@NotNull String key) {
        Preconditions.checkNotNull(key, "key");
        return sharedState.remove(key);
    }

    /**
     * @return an unmodifiable view of the shared state
     */
    public @NotNull Map<String, Object> sharedState() {
        return Collections.unmodifiableMap(sharedState);
    }

    // ==================== Convenience Methods from CommandContext ====================

    /**
     * Get a value from the command context.
     *
     * @param name the argument name
     * @param type the expected type
     * @param <V>  the value type
     * @return the value, or null
     */
    public @Nullable <V> V arg(@NotNull String name, @NotNull Class<V> type) {
        return commandContext.get(name, type);
    }

    /**
     * Get a string from the command context.
     *
     * @param name         the argument name
     * @param defaultValue the default value
     * @return the string value or default
     */
    public @NotNull String argString(@NotNull String name, @NotNull String defaultValue) {
        return commandContext.getStringOrDefault(name, defaultValue);
    }

    /**
     * Get an integer from the command context.
     *
     * @param name         the argument name
     * @param defaultValue the default value
     * @return the integer value or default
     */
    public int argInt(@NotNull String name, int defaultValue) {
        Integer value = commandContext.get(name, Integer.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a boolean from the command context.
     *
     * @param name         the argument name
     * @param defaultValue the default value
     * @return the boolean value or default
     */
    public boolean argBoolean(@NotNull String name, boolean defaultValue) {
        Boolean value = commandContext.get(name, Boolean.class);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a flag from the command context.
     *
     * @param name the flag name
     * @return the flag value
     */
    public boolean flag(@NotNull String name) {
        return commandContext.getFlag(name);
    }

    // ==================== Progress Message ====================

    /**
     * Generate a progress message.
     *
     * @return a formatted progress message
     */
    public @NotNull String progressMessage() {
        return String.format("§7[%d/%d] §fProcessing... §a%d success §c%d failed",
                processedCount(), size(), successCount(), failureCount());
    }

    @Override
    public String toString() {
        return String.format("BatchContext[%d targets, %d/%d processed, cancelled=%s]",
                size(), processedCount(), size(), cancelled.get());
    }
}
