package de.feelix.leviathan.command.batch;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for batch command execution.
 * <p>
 * Configurable aspects:
 * <ul>
 *   <li>Maximum batch size</li>
 *   <li>Parallel vs sequential execution</li>
 *   <li>Continue on failure behavior</li>
 *   <li>Progress reporting</li>
 *   <li>Timeouts</li>
 * </ul>
 */
public final class BatchConfig {

    private final int maxBatchSize;
    private final boolean parallel;
    private final int parallelism;
    private final boolean continueOnFailure;
    private final boolean showProgress;
    private final int progressInterval;
    private final long timeoutMillis;
    private final boolean showSummary;
    private final boolean showDetailedErrors;
    private final int maxDisplayedErrors;

    private BatchConfig(Builder builder) {
        this.maxBatchSize = builder.maxBatchSize;
        this.parallel = builder.parallel;
        this.parallelism = builder.parallelism;
        this.continueOnFailure = builder.continueOnFailure;
        this.showProgress = builder.showProgress;
        this.progressInterval = builder.progressInterval;
        this.timeoutMillis = builder.timeoutMillis;
        this.showSummary = builder.showSummary;
        this.showDetailedErrors = builder.showDetailedErrors;
        this.maxDisplayedErrors = builder.maxDisplayedErrors;
    }

    /**
     * Create a new builder with default settings.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Create a default configuration.
     *
     * @return the default configuration
     */
    public static @NotNull BatchConfig defaults() {
        return builder().build();
    }

    /**
     * Create a configuration for parallel execution.
     *
     * @return a parallel configuration
     */
    public static @NotNull BatchConfig parallel() {
        return builder().parallel(true).build();
    }

    /**
     * Create a configuration for sequential execution.
     *
     * @return a sequential configuration
     */
    public static @NotNull BatchConfig sequential() {
        return builder().parallel(false).build();
    }

    // ==================== Accessors ====================

    /**
     * @return the maximum allowed batch size (0 = unlimited)
     */
    public int maxBatchSize() {
        return maxBatchSize;
    }

    /**
     * @return true if batch should be executed in parallel
     */
    public boolean isParallel() {
        return parallel;
    }

    /**
     * @return the level of parallelism (number of concurrent executions)
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * @return true if execution should continue even when entries fail
     */
    public boolean continueOnFailure() {
        return continueOnFailure;
    }

    /**
     * @return true if progress should be shown to the sender
     */
    public boolean showProgress() {
        return showProgress;
    }

    /**
     * @return how often to show progress (every N entries)
     */
    public int progressInterval() {
        return progressInterval;
    }

    /**
     * @return the timeout in milliseconds for the entire batch (0 = no timeout)
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * @return true if a summary should be shown after completion
     */
    public boolean showSummary() {
        return showSummary;
    }

    /**
     * @return true if detailed error messages should be shown
     */
    public boolean showDetailedErrors() {
        return showDetailedErrors;
    }

    /**
     * @return maximum number of errors to display in summary
     */
    public int maxDisplayedErrors() {
        return maxDisplayedErrors;
    }

    /**
     * Check if a batch size is valid.
     *
     * @param size the batch size
     * @return true if the size is within limits
     */
    public boolean isValidSize(int size) {
        return maxBatchSize <= 0 || size <= maxBatchSize;
    }

    /**
     * Check if the timeout has been exceeded.
     *
     * @param elapsedMillis the elapsed time in milliseconds
     * @return true if the timeout has been exceeded
     */
    public boolean isTimedOut(long elapsedMillis) {
        return timeoutMillis > 0 && elapsedMillis >= timeoutMillis;
    }

    /**
     * Check if progress should be shown at the given processed count.
     *
     * @param processedCount the number of entries processed
     * @return true if progress should be shown
     */
    public boolean shouldShowProgress(int processedCount) {
        return showProgress && progressInterval > 0 && processedCount % progressInterval == 0;
    }

    // ==================== Builder ====================

    /**
     * Builder for BatchConfig.
     */
    public static final class Builder {
        private int maxBatchSize = 100;
        private boolean parallel = false;
        private int parallelism = Runtime.getRuntime().availableProcessors();
        private boolean continueOnFailure = true;
        private boolean showProgress = true;
        private int progressInterval = 10;
        private long timeoutMillis = 30000; // 30 seconds default
        private boolean showSummary = true;
        private boolean showDetailedErrors = true;
        private int maxDisplayedErrors = 5;

        private Builder() {}

        /**
         * Set the maximum batch size.
         *
         * @param maxSize the maximum size (0 = unlimited)
         * @return this builder
         */
        public @NotNull Builder maxBatchSize(int maxSize) {
            if (maxSize < 0) {
                throw new IllegalArgumentException("maxBatchSize cannot be negative");
            }
            this.maxBatchSize = maxSize;
            return this;
        }

        /**
         * Set whether to execute in parallel.
         *
         * @param parallel true for parallel execution
         * @return this builder
         */
        public @NotNull Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        /**
         * Set the level of parallelism.
         *
         * @param parallelism number of concurrent executions
         * @return this builder
         */
        public @NotNull Builder parallelism(int parallelism) {
            if (parallelism < 1) {
                throw new IllegalArgumentException("parallelism must be at least 1");
            }
            this.parallelism = parallelism;
            return this;
        }

        /**
         * Set whether to continue on failure.
         *
         * @param continueOnFailure true to continue processing after failures
         * @return this builder
         */
        public @NotNull Builder continueOnFailure(boolean continueOnFailure) {
            this.continueOnFailure = continueOnFailure;
            return this;
        }

        /**
         * Alias for continueOnFailure(false) - stop on first failure.
         *
         * @return this builder
         */
        public @NotNull Builder stopOnFirstFailure() {
            return continueOnFailure(false);
        }

        /**
         * Set whether to show progress.
         *
         * @param showProgress true to show progress messages
         * @return this builder
         */
        public @NotNull Builder showProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }

        /**
         * Set the progress interval.
         *
         * @param interval show progress every N entries
         * @return this builder
         */
        public @NotNull Builder progressInterval(int interval) {
            if (interval < 1) {
                throw new IllegalArgumentException("progressInterval must be at least 1");
            }
            this.progressInterval = interval;
            return this;
        }

        /**
         * Set the timeout.
         *
         * @param timeout the timeout value
         * @param unit    the time unit
         * @return this builder
         */
        public @NotNull Builder timeout(long timeout, @NotNull TimeUnit unit) {
            Preconditions.checkNotNull(unit, "unit");
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout cannot be negative");
            }
            this.timeoutMillis = unit.toMillis(timeout);
            return this;
        }

        /**
         * Set the timeout in milliseconds.
         *
         * @param timeoutMillis the timeout in milliseconds (0 = no timeout)
         * @return this builder
         */
        public @NotNull Builder timeoutMillis(long timeoutMillis) {
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("timeoutMillis cannot be negative");
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Disable timeout.
         *
         * @return this builder
         */
        public @NotNull Builder noTimeout() {
            this.timeoutMillis = 0;
            return this;
        }

        /**
         * Set whether to show summary after completion.
         *
         * @param showSummary true to show summary
         * @return this builder
         */
        public @NotNull Builder showSummary(boolean showSummary) {
            this.showSummary = showSummary;
            return this;
        }

        /**
         * Set whether to show detailed error messages.
         *
         * @param showDetailedErrors true to show detailed errors
         * @return this builder
         */
        public @NotNull Builder showDetailedErrors(boolean showDetailedErrors) {
            this.showDetailedErrors = showDetailedErrors;
            return this;
        }

        /**
         * Set the maximum number of errors to display.
         *
         * @param maxDisplayedErrors maximum errors to display
         * @return this builder
         */
        public @NotNull Builder maxDisplayedErrors(int maxDisplayedErrors) {
            if (maxDisplayedErrors < 0) {
                throw new IllegalArgumentException("maxDisplayedErrors cannot be negative");
            }
            this.maxDisplayedErrors = maxDisplayedErrors;
            return this;
        }

        /**
         * Build the configuration.
         *
         * @return the batch configuration
         */
        public @NotNull BatchConfig build() {
            return new BatchConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format("BatchConfig[maxSize=%d, parallel=%s, continueOnFailure=%s, timeout=%dms]",
                maxBatchSize, parallel, continueOnFailure, timeoutMillis);
    }
}
