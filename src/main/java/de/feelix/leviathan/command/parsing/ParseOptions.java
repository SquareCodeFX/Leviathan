package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Configuration options for the command parsing process.
 * <p>
 * This class allows developers to customize parsing behavior without
 * modifying the command definition. Use {@link #builder()} to create
 * instances with a fluent API.
 * <p>
 * Example usage:
 * <pre>{@code
 * ParseOptions options = ParseOptions.builder()
 *     .checkCooldowns(true)
 *     .includeSubcommands(true)
 *     .collectAllErrors(true)
 *     .includeSuggestions(true)
 *     .build();
 *
 * CommandParseResult result = command.parse(sender, label, args, options);
 * }</pre>
 */
public final class ParseOptions {

    /**
     * Default options: no cooldowns, no subcommand routing, fail-fast errors, with suggestions.
     */
    public static final ParseOptions DEFAULT = builder().build();

    /**
     * Strict options: includes cooldowns, subcommand routing, with suggestions.
     */
    public static final ParseOptions STRICT = builder()
        .checkCooldowns(true)
        .includeSubcommands(true)
        .includeSuggestions(true)
        .build();

    /**
     * Lenient options: collect all errors, include suggestions.
     */
    public static final ParseOptions LENIENT = builder()
        .collectAllErrors(true)
        .includeSuggestions(true)
        .build();

    private final boolean checkCooldowns;
    private final boolean includeSubcommands;
    private final boolean collectAllErrors;
    private final boolean includeSuggestions;
    private final boolean checkConfirmation;
    private final boolean skipGuards;
    private final boolean skipPermissionChecks;
    private final boolean enableAutoCorrection;
    private final double autoCorrectThreshold;
    private final int maxAutoCorrections;
    private final boolean collectMetrics;
    private final boolean enableQuotedStrings;

    private ParseOptions(Builder builder) {
        this.checkCooldowns = builder.checkCooldowns;
        this.includeSubcommands = builder.includeSubcommands;
        this.collectAllErrors = builder.collectAllErrors;
        this.includeSuggestions = builder.includeSuggestions;
        this.checkConfirmation = builder.checkConfirmation;
        this.skipGuards = builder.skipGuards;
        this.skipPermissionChecks = builder.skipPermissionChecks;
        this.enableAutoCorrection = builder.enableAutoCorrection;
        this.autoCorrectThreshold = builder.autoCorrectThreshold;
        this.maxAutoCorrections = builder.maxAutoCorrections;
        this.collectMetrics = builder.collectMetrics;
        this.enableQuotedStrings = builder.enableQuotedStrings;
    }

    /**
     * Create a new builder for ParseOptions.
     *
     * @return a new builder instance
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Whether to check cooldowns during parsing.
     * If true, parsing will fail if the command is on cooldown.
     *
     * @return true if cooldown checks are enabled
     */
    public boolean checkCooldowns() {
        return checkCooldowns;
    }

    /**
     * Whether to handle subcommand routing during parsing.
     * If true, the parser will delegate to subcommands when appropriate.
     *
     * @return true if subcommand routing is enabled
     */
    public boolean includeSubcommands() {
        return includeSubcommands;
    }

    /**
     * Whether to collect all errors instead of failing on the first error.
     * Useful for validation UIs that want to show all problems at once.
     *
     * @return true if all errors should be collected
     */
    public boolean collectAllErrors() {
        return collectAllErrors;
    }

    /**
     * Whether to include "did you mean" suggestions in parsing errors.
     *
     * @return true if suggestions should be included
     */
    public boolean includeSuggestions() {
        return includeSuggestions;
    }

    /**
     * Whether to check confirmation requirements during parsing.
     * If true and command requires confirmation, parsing will fail on first attempt.
     *
     * @return true if confirmation checks are enabled
     */
    public boolean checkConfirmation() {
        return checkConfirmation;
    }

    /**
     * Whether to skip guard checks during parsing.
     * Useful for dry-run scenarios where you only want to validate input format.
     *
     * @return true if guards should be skipped
     */
    public boolean skipGuards() {
        return skipGuards;
    }

    /**
     * Whether to skip permission checks during parsing.
     * Useful for admin tools that validate commands for other users.
     *
     * @return true if permission checks should be skipped
     */
    public boolean skipPermissionChecks() {
        return skipPermissionChecks;
    }

    /**
     * Whether auto-correction is enabled for argument parsing.
     * <p>
     * When enabled, the parser will automatically correct typos in argument values
     * if the similarity to a valid value exceeds the threshold.
     * <p>
     * Example: If a player types "diamnod" and "diamond" is a valid choice,
     * it will be auto-corrected to "diamond".
     *
     * @return true if auto-correction is enabled
     */
    public boolean enableAutoCorrection() {
        return enableAutoCorrection;
    }

    /**
     * The minimum similarity threshold for auto-correction (0.0 to 1.0).
     * <p>
     * Higher values require closer matches. A threshold of 0.8 means
     * the input must be at least 80% similar to the target.
     *
     * @return the similarity threshold (default: 0.8)
     */
    public double autoCorrectThreshold() {
        return autoCorrectThreshold;
    }

    /**
     * Maximum number of auto-corrections to apply in a single parse.
     * <p>
     * This prevents too many corrections which might indicate the user
     * doesn't understand the command.
     *
     * @return the maximum number of auto-corrections (default: 3)
     */
    public int maxAutoCorrections() {
        return maxAutoCorrections;
    }

    /**
     * Whether to collect performance metrics during parsing.
     *
     * @return true if metrics collection is enabled
     */
    public boolean collectMetrics() {
        return collectMetrics;
    }

    /**
     * Whether to enable quoted string parsing.
     * <p>
     * When enabled, strings enclosed in double or single quotes are treated as single tokens,
     * even if they contain spaces.
     * <p>
     * Example: {@code "hello world"} is parsed as a single token "hello world".
     *
     * @return true if quoted string parsing is enabled
     */
    public boolean enableQuotedStrings() {
        return enableQuotedStrings;
    }

    /**
     * Builder for {@link ParseOptions}.
     */
    public static final class Builder {
        private boolean checkCooldowns = false;
        private boolean includeSubcommands = false;
        private boolean collectAllErrors = false;
        private boolean includeSuggestions = true;
        private boolean checkConfirmation = false;
        private boolean skipGuards = false;
        private boolean skipPermissionChecks = false;
        private boolean enableAutoCorrection = false;
        private double autoCorrectThreshold = 0.8;
        private int maxAutoCorrections = 3;
        private boolean collectMetrics = false;
        private boolean enableQuotedStrings = false;

        private Builder() {}

        /**
         * Enable or disable cooldown checks.
         *
         * @param check true to check cooldowns
         * @return this builder
         */
        public @NotNull Builder checkCooldowns(boolean check) {
            this.checkCooldowns = check;
            return this;
        }

        /**
         * Enable or disable subcommand routing.
         *
         * @param include true to handle subcommands
         * @return this builder
         */
        public @NotNull Builder includeSubcommands(boolean include) {
            this.includeSubcommands = include;
            return this;
        }

        /**
         * Enable or disable collecting all errors.
         *
         * @param collect true to collect all errors
         * @return this builder
         */
        public @NotNull Builder collectAllErrors(boolean collect) {
            this.collectAllErrors = collect;
            return this;
        }

        /**
         * Enable or disable "did you mean" suggestions.
         *
         * @param include true to include suggestions
         * @return this builder
         */
        public @NotNull Builder includeSuggestions(boolean include) {
            this.includeSuggestions = include;
            return this;
        }

        /**
         * Enable or disable confirmation checks.
         *
         * @param check true to check confirmation
         * @return this builder
         */
        public @NotNull Builder checkConfirmation(boolean check) {
            this.checkConfirmation = check;
            return this;
        }

        /**
         * Enable or disable skipping guards.
         *
         * @param skip true to skip guards
         * @return this builder
         */
        public @NotNull Builder skipGuards(boolean skip) {
            this.skipGuards = skip;
            return this;
        }

        /**
         * Enable or disable skipping permission checks.
         *
         * @param skip true to skip permission checks
         * @return this builder
         */
        public @NotNull Builder skipPermissionChecks(boolean skip) {
            this.skipPermissionChecks = skip;
            return this;
        }

        /**
         * Enable or disable auto-correction for argument parsing.
         * <p>
         * When enabled, typos in argument values will be automatically corrected
         * if a close match is found.
         *
         * @param enable true to enable auto-correction
         * @return this builder
         */
        public @NotNull Builder enableAutoCorrection(boolean enable) {
            this.enableAutoCorrection = enable;
            return this;
        }

        /**
         * Set the similarity threshold for auto-correction.
         *
         * @param threshold threshold between 0.0 and 1.0 (default: 0.8)
         * @return this builder
         * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
         */
        public @NotNull Builder autoCorrectThreshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
            }
            this.autoCorrectThreshold = threshold;
            return this;
        }

        /**
         * Set the maximum number of auto-corrections per parse.
         *
         * @param max maximum corrections (default: 3)
         * @return this builder
         * @throws IllegalArgumentException if max is negative
         */
        public @NotNull Builder maxAutoCorrections(int max) {
            if (max < 0) {
                throw new IllegalArgumentException("Max auto-corrections must be non-negative");
            }
            this.maxAutoCorrections = max;
            return this;
        }

        /**
         * Enable or disable metrics collection during parsing.
         *
         * @param collect true to collect metrics
         * @return this builder
         */
        public @NotNull Builder collectMetrics(boolean collect) {
            this.collectMetrics = collect;
            return this;
        }

        /**
         * Enable or disable quoted string parsing.
         * <p>
         * When enabled, strings enclosed in double or single quotes are treated as single tokens,
         * even if they contain spaces. This allows arguments like "hello world" to be parsed
         * as a single value.
         * <p>
         * Example:
         * <pre>{@code
         * /command "hello world" player
         * // Without quoted strings: ["\"hello", "world\"", "player"]
         * // With quoted strings:    ["hello world", "player"]
         * }</pre>
         *
         * @param enable true to enable quoted string parsing
         * @return this builder
         */
        public @NotNull Builder enableQuotedStrings(boolean enable) {
            this.enableQuotedStrings = enable;
            return this;
        }

        /**
         * Build the ParseOptions instance.
         *
         * @return a new ParseOptions instance
         */
        public @NotNull ParseOptions build() {
            return new ParseOptions(this);
        }
    }

    @Override
    public String toString() {
        return "ParseOptions{" +
               "checkCooldowns=" + checkCooldowns +
               ", includeSubcommands=" + includeSubcommands +
               ", collectAllErrors=" + collectAllErrors +
               ", includeSuggestions=" + includeSuggestions +
               ", checkConfirmation=" + checkConfirmation +
               ", skipGuards=" + skipGuards +
               ", skipPermissionChecks=" + skipPermissionChecks +
               ", enableAutoCorrection=" + enableAutoCorrection +
               ", autoCorrectThreshold=" + autoCorrectThreshold +
               ", maxAutoCorrections=" + maxAutoCorrections +
               ", collectMetrics=" + collectMetrics +
               ", enableQuotedStrings=" + enableQuotedStrings +
               '}';
    }
}
