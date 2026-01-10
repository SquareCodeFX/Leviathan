package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

/**
 * Represents a single entry in a batch operation with its execution result.
 * <p>
 * Each entry tracks:
 * <ul>
 *   <li>The target entity being processed</li>
 *   <li>Whether the operation succeeded or failed</li>
 *   <li>Any error message if the operation failed</li>
 *   <li>The exception that caused failure, if any</li>
 *   <li>Execution time for this specific entry</li>
 * </ul>
 *
 * @param <T> the type of the target entity
 */
public final class BatchEntry<T> {

    private final T target;
    private final boolean success;
    private final @Nullable String errorMessage;
    private final @Nullable Throwable exception;
    private final long executionTimeNanos;
    private final int index;

    private BatchEntry(T target, boolean success, @Nullable String errorMessage,
                       @Nullable Throwable exception, long executionTimeNanos, int index) {
        this.target = target;
        this.success = success;
        this.errorMessage = errorMessage;
        this.exception = exception;
        this.executionTimeNanos = executionTimeNanos;
        this.index = index;
    }

    /**
     * Create a successful batch entry.
     *
     * @param target             the target entity
     * @param executionTimeNanos the execution time in nanoseconds
     * @param index              the index in the batch
     * @param <T>                the target type
     * @return a successful batch entry
     */
    public static <T> @NotNull BatchEntry<T> success(@NotNull T target, long executionTimeNanos, int index) {
        Preconditions.checkNotNull(target, "target");
        return new BatchEntry<>(target, true, null, null, executionTimeNanos, index);
    }

    /**
     * Create a failed batch entry with an error message.
     *
     * @param target             the target entity
     * @param errorMessage       the error message
     * @param executionTimeNanos the execution time in nanoseconds
     * @param index              the index in the batch
     * @param <T>                the target type
     * @return a failed batch entry
     */
    public static <T> @NotNull BatchEntry<T> failure(@NotNull T target, @NotNull String errorMessage,
                                                       long executionTimeNanos, int index) {
        Preconditions.checkNotNull(target, "target");
        Preconditions.checkNotNull(errorMessage, "errorMessage");
        return new BatchEntry<>(target, false, errorMessage, null, executionTimeNanos, index);
    }

    /**
     * Create a failed batch entry with an exception.
     *
     * @param target             the target entity
     * @param exception          the exception that caused the failure
     * @param executionTimeNanos the execution time in nanoseconds
     * @param index              the index in the batch
     * @param <T>                the target type
     * @return a failed batch entry
     */
    public static <T> @NotNull BatchEntry<T> failure(@NotNull T target, @NotNull Throwable exception,
                                                       long executionTimeNanos, int index) {
        Preconditions.checkNotNull(target, "target");
        Preconditions.checkNotNull(exception, "exception");
        String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        return new BatchEntry<>(target, false, message, exception, executionTimeNanos, index);
    }

    /**
     * Create a skipped batch entry (e.g., due to permission check).
     *
     * @param target the target entity
     * @param reason the reason for skipping
     * @param index  the index in the batch
     * @param <T>    the target type
     * @return a skipped batch entry
     */
    public static <T> @NotNull BatchEntry<T> skipped(@NotNull T target, @NotNull String reason, int index) {
        Preconditions.checkNotNull(target, "target");
        Preconditions.checkNotNull(reason, "reason");
        return new BatchEntry<>(target, false, "Skipped: " + reason, null, 0, index);
    }

    /**
     * @return the target entity for this entry
     */
    public @NotNull T target() {
        return target;
    }

    /**
     * @return true if the operation succeeded
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return true if the operation failed
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * @return the error message, or null if successful
     */
    public @Nullable String errorMessage() {
        return errorMessage;
    }

    /**
     * @return the exception that caused failure, or null if successful or no exception
     */
    public @Nullable Throwable exception() {
        return exception;
    }

    /**
     * @return the execution time in nanoseconds
     */
    public long executionTimeNanos() {
        return executionTimeNanos;
    }

    /**
     * @return the execution time in milliseconds
     */
    public double executionTimeMillis() {
        return executionTimeNanos / 1_000_000.0;
    }

    /**
     * @return the index of this entry in the batch
     */
    public int index() {
        return index;
    }

    /**
     * Get a display name for the target.
     * Attempts to use common methods like getName(), name(), or toString().
     *
     * @return a display-friendly name for the target
     */
    public @NotNull String targetDisplayName() {
        if (target == null) {
            return "null";
        }

        // Try common naming methods via reflection
        try {
            var method = target.getClass().getMethod("getName");
            Object result = method.invoke(target);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception ignored) {
            // Fall through to next attempt
        }

        try {
            var method = target.getClass().getMethod("name");
            Object result = method.invoke(target);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception ignored) {
            // Fall through to toString
        }

        return target.toString();
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("BatchEntry[%d: %s -> SUCCESS (%.2fms)]",
                    index, targetDisplayName(), executionTimeMillis());
        } else {
            return String.format("BatchEntry[%d: %s -> FAILED: %s]",
                    index, targetDisplayName(), errorMessage);
        }
    }
}
