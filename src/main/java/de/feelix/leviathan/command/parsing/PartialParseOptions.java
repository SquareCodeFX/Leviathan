package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;

/**
 * Options for partial command parsing.
 * <p>
 * Partial parsing allows you to parse only a subset of arguments, which is useful for:
 * <ul>
 *   <li>Tab completion validation (parse only what's been typed so far)</li>
 *   <li>Progressive validation (validate as user types)</li>
 *   <li>Early error detection (stop at first error)</li>
 *   <li>Argument-specific testing (only test certain arguments)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Parse only first 2 arguments
 * PartialParseOptions options = PartialParseOptions.builder()
 *     .maxArguments(2)
 *     .build();
 *
 * // Parse until first error
 * PartialParseOptions options = PartialParseOptions.builder()
 *     .stopOnFirstError(true)
 *     .build();
 *
 * // Parse specific argument range
 * PartialParseOptions options = PartialParseOptions.builder()
 *     .startAtArgument(1)
 *     .maxArguments(3)
 *     .build();
 * }</pre>
 */
public final class PartialParseOptions {

    /**
     * Default partial parse options (parse all arguments, don't stop on errors).
     */
    public static final PartialParseOptions DEFAULT = builder().build();

    /**
     * Options that stop parsing at the first error encountered.
     */
    public static final PartialParseOptions STOP_ON_ERROR = builder().stopOnFirstError(true).build();

    private final int startAtArgument;
    private final int maxArguments;
    private final boolean stopOnFirstError;
    private final boolean skipPermissionChecks;
    private final boolean skipGuards;
    private final boolean includePartialContext;

    private PartialParseOptions(Builder builder) {
        this.startAtArgument = builder.startAtArgument;
        this.maxArguments = builder.maxArguments;
        this.stopOnFirstError = builder.stopOnFirstError;
        this.skipPermissionChecks = builder.skipPermissionChecks;
        this.skipGuards = builder.skipGuards;
        this.includePartialContext = builder.includePartialContext;
    }

    /**
     * Create a new builder for PartialParseOptions.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Create options to parse only the first N arguments.
     *
     * @param count number of arguments to parse
     * @return partial parse options
     */
    public static @NotNull PartialParseOptions firstN(int count) {
        return builder().maxArguments(count).build();
    }

    /**
     * Create options to parse a specific argument only.
     *
     * @param index the argument index (0-based)
     * @return partial parse options
     */
    public static @NotNull PartialParseOptions onlyArgument(int index) {
        return builder().startAtArgument(index).maxArguments(1).build();
    }

    /**
     * Get the argument index to start parsing from (0-based).
     *
     * @return the start argument index
     */
    public int startAtArgument() {
        return startAtArgument;
    }

    /**
     * Get the maximum number of arguments to parse.
     * A value of -1 means no limit (parse all remaining arguments).
     *
     * @return the maximum number of arguments to parse
     */
    public int maxArguments() {
        return maxArguments;
    }

    /**
     * Check if parsing should stop at the first error.
     *
     * @return true if parsing should stop at the first error
     */
    public boolean stopOnFirstError() {
        return stopOnFirstError;
    }

    /**
     * Check if permission checks should be skipped.
     *
     * @return true if permission checks should be skipped
     */
    public boolean skipPermissionChecks() {
        return skipPermissionChecks;
    }

    /**
     * Check if guards should be skipped.
     *
     * @return true if guards should be skipped
     */
    public boolean skipGuards() {
        return skipGuards;
    }

    /**
     * Check if partial context should be included in the result even on failure.
     * <p>
     * When enabled, a failed parse result will include whatever arguments
     * were successfully parsed before the error occurred.
     *
     * @return true if partial context should be included
     */
    public boolean includePartialContext() {
        return includePartialContext;
    }

    /**
     * Check if a given argument index should be parsed.
     *
     * @param argumentIndex the argument index to check
     * @return true if the argument should be parsed
     */
    public boolean shouldParseArgument(int argumentIndex) {
        if (argumentIndex < startAtArgument) {
            return false;
        }
        if (maxArguments < 0) {
            return true;
        }
        return argumentIndex < startAtArgument + maxArguments;
    }

    @Override
    public String toString() {
        return "PartialParseOptions{" +
               "startAt=" + startAtArgument +
               ", max=" + maxArguments +
               ", stopOnError=" + stopOnFirstError +
               ", skipPermissions=" + skipPermissionChecks +
               ", skipGuards=" + skipGuards +
               ", includePartial=" + includePartialContext +
               '}';
    }

    /**
     * Builder for PartialParseOptions.
     */
    public static final class Builder {
        private int startAtArgument = 0;
        private int maxArguments = -1; // -1 means no limit
        private boolean stopOnFirstError = false;
        private boolean skipPermissionChecks = false;
        private boolean skipGuards = false;
        private boolean includePartialContext = false;

        private Builder() {}

        /**
         * Set the argument index to start parsing from.
         *
         * @param index the start index (0-based)
         * @return this builder
         */
        public @NotNull Builder startAtArgument(int index) {
            Preconditions.checkArgument(index >= 0, "Start index must be non-negative");
            this.startAtArgument = index;
            return this;
        }

        /**
         * Set the maximum number of arguments to parse.
         *
         * @param max the maximum count (-1 for no limit)
         * @return this builder
         */
        public @NotNull Builder maxArguments(int max) {
            Preconditions.checkArgument(max >= -1, "Max arguments must be -1 (no limit) or non-negative");
            this.maxArguments = max;
            return this;
        }

        /**
         * Configure whether to stop parsing at the first error.
         *
         * @param stop true to stop at first error
         * @return this builder
         */
        public @NotNull Builder stopOnFirstError(boolean stop) {
            this.stopOnFirstError = stop;
            return this;
        }

        /**
         * Configure whether to skip permission checks.
         *
         * @param skip true to skip permission checks
         * @return this builder
         */
        public @NotNull Builder skipPermissionChecks(boolean skip) {
            this.skipPermissionChecks = skip;
            return this;
        }

        /**
         * Configure whether to skip guards.
         *
         * @param skip true to skip guards
         * @return this builder
         */
        public @NotNull Builder skipGuards(boolean skip) {
            this.skipGuards = skip;
            return this;
        }

        /**
         * Configure whether to include partially parsed context on failure.
         *
         * @param include true to include partial context
         * @return this builder
         */
        public @NotNull Builder includePartialContext(boolean include) {
            this.includePartialContext = include;
            return this;
        }

        /**
         * Build the PartialParseOptions.
         *
         * @return the built options
         */
        public @NotNull PartialParseOptions build() {
            return new PartialParseOptions(this);
        }
    }
}
