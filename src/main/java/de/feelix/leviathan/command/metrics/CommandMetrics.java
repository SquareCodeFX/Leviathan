package de.feelix.leviathan.command.metrics;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.error.ErrorType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe metrics collector for command execution statistics.
 * <p>
 * Tracks:
 * <ul>
 *   <li>Total executions</li>
 *   <li>Successful executions</li>
 *   <li>Failed executions (by error type)</li>
 *   <li>Permission denials</li>
 *   <li>Validation failures</li>
 *   <li>Average execution time</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand cmd = SlashCommand.create("mycommand")
 *     .enableMetrics()
 *     .build();
 *
 * // Later, retrieve metrics:
 * CommandMetrics metrics = cmd.metrics();
 * Map<String, Object> stats = metrics.getSnapshot();
 * System.out.println("Total executions: " + stats.get("totalExecutions"));
 * }</pre>
 */
public final class CommandMetrics {

    private final LongAdder totalExecutions = new LongAdder();
    private final LongAdder successfulExecutions = new LongAdder();
    private final LongAdder failedExecutions = new LongAdder();
    private final Map<ErrorType, LongAdder> errorsByType = new ConcurrentHashMap<>();

    // Execution time tracking
    private final LongAdder totalExecutionTimeMs = new LongAdder();
    private final AtomicLong minExecutionTimeMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxExecutionTimeMs = new AtomicLong(0);

    // Timestamp tracking
    private final AtomicLong firstExecutionTime = new AtomicLong(0);
    private final AtomicLong lastExecutionTime = new AtomicLong(0);

    /**
     * Record a successful command execution.
     *
     * @param executionTimeMs time taken in milliseconds
     */
    public void recordSuccess(long executionTimeMs) {
        totalExecutions.increment();
        successfulExecutions.increment();
        recordExecutionTime(executionTimeMs);
        updateTimestamps();
    }

    /**
     * Record a failed command execution.
     *
     * @param errorType       the type of error that occurred
     * @param executionTimeMs time taken in milliseconds (may be 0 for early failures)
     */
    public void recordFailure(@NotNull ErrorType errorType, long executionTimeMs) {
        totalExecutions.increment();
        failedExecutions.increment();
        errorsByType.computeIfAbsent(errorType, k -> new LongAdder()).increment();
        if (executionTimeMs > 0) {
            recordExecutionTime(executionTimeMs);
        }
        updateTimestamps();
    }

    /**
     * Record execution time and update min/max.
     */
    private void recordExecutionTime(long timeMs) {
        totalExecutionTimeMs.add(timeMs);

        // Update min
        long currentMin;
        do {
            currentMin = minExecutionTimeMs.get();
            if (timeMs >= currentMin) break;
        } while (!minExecutionTimeMs.compareAndSet(currentMin, timeMs));

        // Update max
        long currentMax;
        do {
            currentMax = maxExecutionTimeMs.get();
            if (timeMs <= currentMax) break;
        } while (!maxExecutionTimeMs.compareAndSet(currentMax, timeMs));
    }

    /**
     * Update first/last execution timestamps.
     */
    private void updateTimestamps() {
        long now = System.currentTimeMillis();
        firstExecutionTime.compareAndSet(0, now);
        lastExecutionTime.set(now);
    }

    /**
     * Get the total number of executions.
     *
     * @return total executions
     */
    public long getTotalExecutions() {
        return totalExecutions.sum();
    }

    /**
     * Get the number of successful executions.
     *
     * @return successful executions
     */
    public long getSuccessfulExecutions() {
        return successfulExecutions.sum();
    }

    /**
     * Get the number of failed executions.
     *
     * @return failed executions
     */
    public long getFailedExecutions() {
        return failedExecutions.sum();
    }

    /**
     * Get the success rate as a percentage (0-100).
     *
     * @return success rate percentage, or 0 if no executions
     */
    public double getSuccessRate() {
        long total = totalExecutions.sum();
        if (total == 0) return 0.0;
        return (successfulExecutions.sum() * 100.0) / total;
    }

    /**
     * Get the average execution time in milliseconds.
     *
     * @return average time in ms, or 0 if no successful executions
     */
    public double getAverageExecutionTimeMs() {
        long successful = successfulExecutions.sum();
        if (successful == 0) return 0.0;
        return (double) totalExecutionTimeMs.sum() / successful;
    }

    /**
     * Get the minimum execution time in milliseconds.
     *
     * @return min time in ms, or 0 if no executions
     */
    public long getMinExecutionTimeMs() {
        long min = minExecutionTimeMs.get();
        return (min == Long.MAX_VALUE) ? 0 : min;
    }

    /**
     * Get the maximum execution time in milliseconds.
     *
     * @return max time in ms
     */
    public long getMaxExecutionTimeMs() {
        return maxExecutionTimeMs.get();
    }

    /**
     * Get error count by type.
     *
     * @param errorType the error type
     * @return count for that error type
     */
    public long getErrorCount(@NotNull ErrorType errorType) {
        LongAdder adder = errorsByType.get(errorType);
        return (adder != null) ? adder.sum() : 0;
    }

    /**
     * Get timestamp of first execution.
     *
     * @return first execution timestamp (millis since epoch), or 0 if never executed
     */
    public long getFirstExecutionTime() {
        return firstExecutionTime.get();
    }

    /**
     * Get timestamp of last execution.
     *
     * @return last execution timestamp (millis since epoch), or 0 if never executed
     */
    public long getLastExecutionTime() {
        return lastExecutionTime.get();
    }

    /**
     * Get a snapshot of all metrics as a map.
     * Useful for serialization or display.
     *
     * @return map of metric names to values
     */
    public @NotNull Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();

        snapshot.put("totalExecutions", getTotalExecutions());
        snapshot.put("successfulExecutions", getSuccessfulExecutions());
        snapshot.put("failedExecutions", getFailedExecutions());
        snapshot.put("successRate", String.format("%.2f%%", getSuccessRate()));
        snapshot.put("averageExecutionTimeMs", String.format("%.2f", getAverageExecutionTimeMs()));
        snapshot.put("minExecutionTimeMs", getMinExecutionTimeMs());
        snapshot.put("maxExecutionTimeMs", getMaxExecutionTimeMs());
        snapshot.put("firstExecutionTime", getFirstExecutionTime());
        snapshot.put("lastExecutionTime", getLastExecutionTime());

        // Add error breakdown
        Map<String, Long> errors = new LinkedHashMap<>();
        for (ErrorType type : ErrorType.values()) {
            long count = getErrorCount(type);
            if (count > 0) {
                errors.put(type.name(), count);
            }
        }
        snapshot.put("errorsByType", errors);

        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Reset all metrics to zero.
     */
    public void reset() {
        totalExecutions.reset();
        successfulExecutions.reset();
        failedExecutions.reset();
        errorsByType.clear();
        totalExecutionTimeMs.reset();
        minExecutionTimeMs.set(Long.MAX_VALUE);
        maxExecutionTimeMs.set(0);
        firstExecutionTime.set(0);
        lastExecutionTime.set(0);
    }

    @Override
    public String toString() {
        return "CommandMetrics{" +
               "total=" + getTotalExecutions() +
               ", success=" + getSuccessfulExecutions() +
               ", failed=" + getFailedExecutions() +
               ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
               ", avgTime=" + String.format("%.1fms", getAverageExecutionTimeMs()) +
               '}';
    }
}
