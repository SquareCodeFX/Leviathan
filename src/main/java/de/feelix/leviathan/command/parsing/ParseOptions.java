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

    private ParseOptions(Builder builder) {
        this.checkCooldowns = builder.checkCooldowns;
        this.includeSubcommands = builder.includeSubcommands;
        this.collectAllErrors = builder.collectAllErrors;
        this.includeSuggestions = builder.includeSuggestions;
        this.checkConfirmation = builder.checkConfirmation;
        this.skipGuards = builder.skipGuards;
        this.skipPermissionChecks = builder.skipPermissionChecks;
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
               '}';
    }
}
