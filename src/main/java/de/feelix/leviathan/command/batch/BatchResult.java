package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated result of a batch operation containing all individual entry results.
 * <p>
 * Provides methods to:
 * <ul>
 *   <li>Get success/failure counts</li>
 *   <li>Access individual entry results</li>
 *   <li>Filter by success/failure</li>
 *   <li>Calculate aggregate statistics</li>
 *   <li>Generate summary messages</li>
 * </ul>
 *
 * @param <T> the type of entities in the batch
 */
public final class BatchResult<T> {

    private final List<BatchEntry<T>> entries;
    private final long totalExecutionTimeNanos;
    private final boolean parallel;

    private BatchResult(List<BatchEntry<T>> entries, long totalExecutionTimeNanos, boolean parallel) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.totalExecutionTimeNanos = totalExecutionTimeNanos;
        this.parallel = parallel;
    }

    /**
     * Create a batch result from a list of entries.
     *
     * @param entries                the batch entries
     * @param totalExecutionTimeNanos the total execution time
     * @param parallel               whether the batch was executed in parallel
     * @param <T>                    the target type
     * @return a new batch result
     */
    public static <T> @NotNull BatchResult<T> of(@NotNull List<BatchEntry<T>> entries,
                                                   long totalExecutionTimeNanos, boolean parallel) {
        Preconditions.checkNotNull(entries, "entries");
        return new BatchResult<>(entries, totalExecutionTimeNanos, parallel);
    }

    /**
     * Create an empty batch result.
     *
     * @param <T> the target type
     * @return an empty batch result
     */
    public static <T> @NotNull BatchResult<T> empty() {
        return new BatchResult<>(Collections.emptyList(), 0, false);
    }

    // ==================== Accessors ====================

    /**
     * @return all entries in the batch
     */
    public @NotNull List<BatchEntry<T>> entries() {
        return entries;
    }

    /**
     * @return the total number of entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * @return true if the batch is empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Get an entry by index.
     *
     * @param index the index
     * @return the entry at the given index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public @NotNull BatchEntry<T> get(int index) {
        return entries.get(index);
    }

    // ==================== Success/Failure Counts ====================

    /**
     * @return the number of successful entries
     */
    public int successCount() {
        return (int) entries.stream().filter(BatchEntry::isSuccess).count();
    }

    /**
     * @return the number of failed entries
     */
    public int failureCount() {
        return (int) entries.stream().filter(BatchEntry::isFailure).count();
    }

    /**
     * @return true if all entries succeeded
     */
    public boolean allSucceeded() {
        return !entries.isEmpty() && entries.stream().allMatch(BatchEntry::isSuccess);
    }

    /**
     * @return true if all entries failed
     */
    public boolean allFailed() {
        return !entries.isEmpty() && entries.stream().allMatch(BatchEntry::isFailure);
    }

    /**
     * @return true if at least one entry succeeded
     */
    public boolean hasSuccesses() {
        return entries.stream().anyMatch(BatchEntry::isSuccess);
    }

    /**
     * @return true if at least one entry failed
     */
    public boolean hasFailures() {
        return entries.stream().anyMatch(BatchEntry::isFailure);
    }

    /**
     * @return the success rate as a percentage (0.0 to 100.0)
     */
    public double successRate() {
        if (entries.isEmpty()) {
            return 0.0;
        }
        return (successCount() * 100.0) / entries.size();
    }

    // ==================== Filtered Access ====================

    /**
     * @return only the successful entries
     */
    public @NotNull List<BatchEntry<T>> successes() {
        return entries.stream()
                .filter(BatchEntry::isSuccess)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return only the failed entries
     */
    public @NotNull List<BatchEntry<T>> failures() {
        return entries.stream()
                .filter(BatchEntry::isFailure)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return the targets of successful entries
     */
    public @NotNull List<T> successfulTargets() {
        return entries.stream()
                .filter(BatchEntry::isSuccess)
                .map(BatchEntry::target)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * @return the targets of failed entries
     */
    public @NotNull List<T> failedTargets() {
        return entries.stream()
                .filter(BatchEntry::isFailure)
                .map(BatchEntry::target)
                .collect(Collectors.toUnmodifiableList());
    }

    // ==================== Timing ====================

    /**
     * @return the total execution time in nanoseconds
     */
    public long totalExecutionTimeNanos() {
        return totalExecutionTimeNanos;
    }

    /**
     * @return the total execution time in milliseconds
     */
    public double totalExecutionTimeMillis() {
        return totalExecutionTimeNanos / 1_000_000.0;
    }

    /**
     * @return the average execution time per entry in nanoseconds
     */
    public double averageExecutionTimeNanos() {
        if (entries.isEmpty()) {
            return 0.0;
        }
        return entries.stream()
                .mapToLong(BatchEntry::executionTimeNanos)
                .average()
                .orElse(0.0);
    }

    /**
     * @return the average execution time per entry in milliseconds
     */
    public double averageExecutionTimeMillis() {
        return averageExecutionTimeNanos() / 1_000_000.0;
    }

    /**
     * @return whether the batch was executed in parallel
     */
    public boolean wasParallel() {
        return parallel;
    }

    // ==================== Summary Generation ====================

    /**
     * Generate a short summary message.
     * Example: "5/7 succeeded (71.4%)"
     *
     * @return a short summary string
     */
    public @NotNull String shortSummary() {
        if (entries.isEmpty()) {
            return "No entries processed";
        }
        return String.format("%d/%d succeeded (%.1f%%)",
                successCount(), size(), successRate());
    }

    /**
     * Generate a detailed summary message.
     *
     * @return a detailed summary string
     */
    public @NotNull String detailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6═══ Batch Operation Summary ═══\n");
        sb.append(String.format("§7Total: §f%d §7| §aSuccess: §f%d §7| §cFailed: §f%d\n",
                size(), successCount(), failureCount()));
        sb.append(String.format("§7Success Rate: §f%.1f%%\n", successRate()));
        sb.append(String.format("§7Execution Time: §f%.2fms", totalExecutionTimeMillis()));
        if (parallel) {
            sb.append(" §8(parallel)");
        } else {
            sb.append(" §8(sequential)");
        }
        sb.append("\n");

        if (hasFailures()) {
            sb.append("§c§lFailures:\n");
            int count = 0;
            for (BatchEntry<T> entry : failures()) {
                if (count >= 5) {
                    sb.append(String.format("§7  ... and %d more failures\n", failureCount() - 5));
                    break;
                }
                sb.append(String.format("§c  - %s: %s\n",
                        entry.targetDisplayName(), entry.errorMessage()));
                count++;
            }
        }

        return sb.toString();
    }

    /**
     * Generate a compact single-line summary suitable for chat.
     * Example: "§aBatch complete: §f5§7/§f7 §asuccessful §8(0.45ms)"
     *
     * @return a compact summary string
     */
    public @NotNull String compactSummary() {
        if (allSucceeded()) {
            return String.format("§aBatch complete: §f%d§7/§f%d §asuccessful §8(%.2fms)",
                    successCount(), size(), totalExecutionTimeMillis());
        } else if (allFailed()) {
            return String.format("§cBatch failed: §f0§7/§f%d §csuccessful §8(%.2fms)",
                    size(), totalExecutionTimeMillis());
        } else {
            return String.format("§eBatch partial: §f%d§7/§f%d §esuccessful, §c%d failed §8(%.2fms)",
                    successCount(), size(), failureCount(), totalExecutionTimeMillis());
        }
    }

    // ==================== Builder for Collecting Results ====================

    /**
     * Create a new builder for collecting batch results.
     *
     * @param <T> the target type
     * @return a new builder
     */
    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for collecting batch entries and creating a BatchResult.
     *
     * @param <T> the target type
     */
    public static final class Builder<T> {
        private final List<BatchEntry<T>> entries = new ArrayList<>();
        private long startTimeNanos = System.nanoTime();
        private boolean parallel = false;

        private Builder() {}

        /**
         * Mark the start of batch execution.
         *
         * @return this builder
         */
        public @NotNull Builder<T> start() {
            this.startTimeNanos = System.nanoTime();
            return this;
        }

        /**
         * Set whether this is a parallel execution.
         *
         * @param parallel true for parallel execution
         * @return this builder
         */
        public @NotNull Builder<T> parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        /**
         * Add a successful entry.
         *
         * @param target             the target
         * @param executionTimeNanos the execution time in nanoseconds
         * @return this builder
         */
        public @NotNull Builder<T> addSuccess(@NotNull T target, long executionTimeNanos) {
            entries.add(BatchEntry.success(target, executionTimeNanos, entries.size()));
            return this;
        }

        /**
         * Add a failed entry.
         *
         * @param target             the target
         * @param errorMessage       the error message
         * @param executionTimeNanos the execution time in nanoseconds
         * @return this builder
         */
        public @NotNull Builder<T> addFailure(@NotNull T target, @NotNull String errorMessage,
                                               long executionTimeNanos) {
            entries.add(BatchEntry.failure(target, errorMessage, executionTimeNanos, entries.size()));
            return this;
        }

        /**
         * Add a failed entry with exception.
         *
         * @param target             the target
         * @param exception          the exception
         * @param executionTimeNanos the execution time in nanoseconds
         * @return this builder
         */
        public @NotNull Builder<T> addFailure(@NotNull T target, @NotNull Throwable exception,
                                               long executionTimeNanos) {
            entries.add(BatchEntry.failure(target, exception, executionTimeNanos, entries.size()));
            return this;
        }

        /**
         * Add a skipped entry.
         *
         * @param target the target
         * @param reason the reason for skipping
         * @return this builder
         */
        public @NotNull Builder<T> addSkipped(@NotNull T target, @NotNull String reason) {
            entries.add(BatchEntry.skipped(target, reason, entries.size()));
            return this;
        }

        /**
         * Add a pre-built entry.
         *
         * @param entry the entry to add
         * @return this builder
         */
        public @NotNull Builder<T> addEntry(@NotNull BatchEntry<T> entry) {
            entries.add(entry);
            return this;
        }

        /**
         * Build the batch result.
         *
         * @return the batch result
         */
        public @NotNull BatchResult<T> build() {
            long totalTime = System.nanoTime() - startTimeNanos;
            return BatchResult.of(entries, totalTime, parallel);
        }
    }

    @Override
    public String toString() {
        return String.format("BatchResult[%s, parallel=%s]", shortSummary(), parallel);
    }
}
