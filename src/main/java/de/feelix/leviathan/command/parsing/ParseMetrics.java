package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Metrics collected during command parsing.
 * <p>
 * This class provides detailed timing and statistics about the parsing process,
 * useful for performance monitoring, debugging, and optimization.
 * <p>
 * Example usage:
 * <pre>{@code
 * CommandParseResult result = command.parse(sender, label, args,
 *     ParseOptions.builder().collectMetrics(true).build());
 *
 * ParseMetrics metrics = result.metrics();
 * System.out.println("Parse time: " + metrics.totalTimeMillis() + "ms");
 * System.out.println("Arguments parsed: " + metrics.argumentsParsed());
 * }</pre>
 */
public final class ParseMetrics {

    /**
     * Empty metrics instance for when metrics collection is disabled.
     */
    public static final ParseMetrics EMPTY = new ParseMetrics(0, 0, 0, 0, 0, 0, 0, false);

    private final long totalTimeNanos;
    private final long permissionCheckTimeNanos;
    private final long guardCheckTimeNanos;
    private final long argumentParseTimeNanos;
    private final long validationTimeNanos;
    private final int argumentsParsed;
    private final int errorsEncountered;
    private final boolean metricsEnabled;

    private ParseMetrics(long totalTimeNanos,
                         long permissionCheckTimeNanos,
                         long guardCheckTimeNanos,
                         long argumentParseTimeNanos,
                         long validationTimeNanos,
                         int argumentsParsed,
                         int errorsEncountered,
                         boolean metricsEnabled) {
        this.totalTimeNanos = totalTimeNanos;
        this.permissionCheckTimeNanos = permissionCheckTimeNanos;
        this.guardCheckTimeNanos = guardCheckTimeNanos;
        this.argumentParseTimeNanos = argumentParseTimeNanos;
        this.validationTimeNanos = validationTimeNanos;
        this.argumentsParsed = argumentsParsed;
        this.errorsEncountered = errorsEncountered;
        this.metricsEnabled = metricsEnabled;
    }

    /**
     * Create a new metrics builder.
     *
     * @return a new builder instance
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Check if metrics collection was enabled for this parse.
     *
     * @return true if metrics were collected
     */
    public boolean isEnabled() {
        return metricsEnabled;
    }

    /**
     * Get the total parsing time in nanoseconds.
     *
     * @return total time in nanoseconds
     */
    public long totalTimeNanos() {
        return totalTimeNanos;
    }

    /**
     * Get the total parsing time in milliseconds.
     *
     * @return total time in milliseconds
     */
    public double totalTimeMillis() {
        return totalTimeNanos / 1_000_000.0;
    }

    /**
     * Get the permission check time in nanoseconds.
     *
     * @return permission check time in nanoseconds
     */
    public long permissionCheckTimeNanos() {
        return permissionCheckTimeNanos;
    }

    /**
     * Get the guard check time in nanoseconds.
     *
     * @return guard check time in nanoseconds
     */
    public long guardCheckTimeNanos() {
        return guardCheckTimeNanos;
    }

    /**
     * Get the argument parsing time in nanoseconds.
     *
     * @return argument parsing time in nanoseconds
     */
    public long argumentParseTimeNanos() {
        return argumentParseTimeNanos;
    }

    /**
     * Get the validation time in nanoseconds.
     *
     * @return validation time in nanoseconds
     */
    public long validationTimeNanos() {
        return validationTimeNanos;
    }

    /**
     * Get the number of arguments successfully parsed.
     *
     * @return number of arguments parsed
     */
    public int argumentsParsed() {
        return argumentsParsed;
    }

    /**
     * Get the number of errors encountered during parsing.
     *
     * @return number of errors encountered
     */
    public int errorsEncountered() {
        return errorsEncountered;
    }

    /**
     * Get a formatted summary of the metrics.
     *
     * @return formatted metrics summary
     */
    public @NotNull String toSummary() {
        if (!metricsEnabled) {
            return "ParseMetrics{disabled}";
        }
        return String.format(
            "ParseMetrics{total=%.2fms, permissions=%.2fms, guards=%.2fms, parsing=%.2fms, validation=%.2fms, args=%d, errors=%d}",
            totalTimeMillis(),
            permissionCheckTimeNanos / 1_000_000.0,
            guardCheckTimeNanos / 1_000_000.0,
            argumentParseTimeNanos / 1_000_000.0,
            validationTimeNanos / 1_000_000.0,
            argumentsParsed,
            errorsEncountered
        );
    }

    @Override
    public String toString() {
        return toSummary();
    }

    /**
     * Builder for ParseMetrics.
     */
    public static final class Builder {
        private long totalTimeNanos = 0;
        private long permissionCheckTimeNanos = 0;
        private long guardCheckTimeNanos = 0;
        private long argumentParseTimeNanos = 0;
        private long validationTimeNanos = 0;
        private int argumentsParsed = 0;
        private int errorsEncountered = 0;

        private Builder() {}

        public @NotNull Builder totalTimeNanos(long nanos) {
            this.totalTimeNanos = nanos;
            return this;
        }

        public @NotNull Builder permissionCheckTimeNanos(long nanos) {
            this.permissionCheckTimeNanos = nanos;
            return this;
        }

        public @NotNull Builder guardCheckTimeNanos(long nanos) {
            this.guardCheckTimeNanos = nanos;
            return this;
        }

        public @NotNull Builder argumentParseTimeNanos(long nanos) {
            this.argumentParseTimeNanos = nanos;
            return this;
        }

        public @NotNull Builder validationTimeNanos(long nanos) {
            this.validationTimeNanos = nanos;
            return this;
        }

        public @NotNull Builder argumentsParsed(int count) {
            this.argumentsParsed = count;
            return this;
        }

        public @NotNull Builder errorsEncountered(int count) {
            this.errorsEncountered = count;
            return this;
        }

        public @NotNull Builder incrementArgumentsParsed() {
            this.argumentsParsed++;
            return this;
        }

        public @NotNull Builder incrementErrorsEncountered() {
            this.errorsEncountered++;
            return this;
        }

        public @NotNull ParseMetrics build() {
            return new ParseMetrics(
                totalTimeNanos,
                permissionCheckTimeNanos,
                guardCheckTimeNanos,
                argumentParseTimeNanos,
                validationTimeNanos,
                argumentsParsed,
                errorsEncountered,
                true
            );
        }
    }
}
