package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.function.Consumer;

/**
 * Represents the result of partial command parsing.
 * <p>
 * Unlike {@link CommandParseResult}, this class can contain both successfully parsed
 * arguments AND errors at the same time, which is useful for progressive validation
 * and tab completion scenarios.
 * <p>
 * Example usage:
 * <pre>{@code
 * PartialParseResult result = command.parsePartial(sender, label, args, PartialParseOptions.STOP_ON_ERROR);
 *
 * // Check what was successfully parsed
 * Map<String, Object> parsedArgs = result.parsedArguments();
 *
 * // Check what errors occurred
 * if (result.hasErrors()) {
 *     int errorIndex = result.errorArgumentIndex();
 *     // Highlight the problematic argument in UI
 * }
 *
 * // Get the index of the last successfully parsed argument
 * int lastParsed = result.lastParsedArgumentIndex();
 * }</pre>
 */
public final class PartialParseResult {

    private final @NotNull Map<String, Object> parsedArguments;
    private final @NotNull Map<String, Boolean> parsedFlags;
    private final @NotNull Map<String, Object> parsedKeyValues;
    private final @NotNull List<CommandParseError> errors;
    private final @NotNull String[] rawArgs;
    private final int argumentsParsed;
    private final int errorArgumentIndex;
    private final boolean complete;
    private final @NotNull ParseMetrics metrics;
    private final @NotNull Map<String, String> aliasMap;

    private PartialParseResult(Builder builder) {
        this.parsedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parsedArguments));
        this.parsedFlags = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parsedFlags));
        this.parsedKeyValues = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parsedKeyValues));
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.rawArgs = builder.rawArgs.clone();
        this.argumentsParsed = builder.argumentsParsed;
        this.errorArgumentIndex = builder.errorArgumentIndex;
        this.complete = builder.complete;
        this.metrics = builder.metrics;
        this.aliasMap = Collections.unmodifiableMap(new LinkedHashMap<>(builder.aliasMap));
    }

    /**
     * Create a new builder for PartialParseResult.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Create a complete result (no errors, all arguments parsed).
     *
     * @param context the fully parsed context
     * @param rawArgs the raw arguments
     * @return a complete partial parse result
     */
    public static @NotNull PartialParseResult complete(@NotNull CommandContext context, @NotNull String[] rawArgs) {
        Preconditions.checkNotNull(context, "context");
        return builder()
            .withRawArgs(rawArgs)
            .withArguments(context.allArguments())
            .withFlags(context.allFlags())
            .withKeyValues(context.allKeyValues())
            .withAliasMap(context.aliasMap())
            .complete(true)
            .build();
    }

    /**
     * Resolve an argument name or alias to the primary name.
     *
     * @param nameOrAlias the name or alias to resolve
     * @return the primary argument name
     */
    private @NotNull String resolveName(@NotNull String nameOrAlias) {
        String resolved = aliasMap.get(nameOrAlias);
        return resolved != null ? resolved : nameOrAlias;
    }

    /**
     * Get the map of successfully parsed arguments.
     *
     * @return unmodifiable map of argument name to parsed value
     */
    public @NotNull Map<String, Object> parsedArguments() {
        return parsedArguments;
    }

    /**
     * Get a specific parsed argument value.
     * Supports looking up by argument name or alias.
     *
     * @param name the argument name or alias
     * @param <T>  the expected type
     * @return the parsed value, or null if not parsed
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T> T getArgument(@NotNull String name) {
        return (T) parsedArguments.get(resolveName(name));
    }

    /**
     * Check if a specific argument was successfully parsed.
     * Supports looking up by argument name or alias.
     *
     * @param name the argument name or alias
     * @return true if the argument was parsed
     */
    public boolean hasArgument(@NotNull String name) {
        return parsedArguments.containsKey(resolveName(name));
    }

    /**
     * Get the alias map.
     *
     * @return unmodifiable map of alias to primary argument name
     */
    public @NotNull Map<String, String> aliasMap() {
        return aliasMap;
    }

    /**
     * Get the map of successfully parsed flags.
     *
     * @return unmodifiable map of flag name to value
     */
    public @NotNull Map<String, Boolean> parsedFlags() {
        return parsedFlags;
    }

    /**
     * Get the map of successfully parsed key-value pairs.
     *
     * @return unmodifiable map of key-value name to value
     */
    public @NotNull Map<String, Object> parsedKeyValues() {
        return parsedKeyValues;
    }

    /**
     * Get the list of errors encountered during parsing.
     *
     * @return unmodifiable list of errors
     */
    public @NotNull List<CommandParseError> errors() {
        return errors;
    }

    /**
     * Check if there are any errors.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Check if parsing was successful (no errors).
     *
     * @return true if no errors occurred
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Get the number of arguments successfully parsed.
     *
     * @return the count of parsed arguments
     */
    public int argumentsParsed() {
        return argumentsParsed;
    }

    /**
     * Get the index of the last successfully parsed argument (0-based).
     *
     * @return the last parsed argument index, or -1 if none were parsed
     */
    public int lastParsedArgumentIndex() {
        return argumentsParsed > 0 ? argumentsParsed - 1 : -1;
    }

    /**
     * Get the index of the argument where the first error occurred.
     *
     * @return the error argument index, or -1 if no errors
     */
    public int errorArgumentIndex() {
        return errorArgumentIndex;
    }

    /**
     * Check if all arguments were successfully parsed.
     *
     * @return true if parsing completed all arguments
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Get the original raw arguments.
     *
     * @return clone of the raw arguments array
     */
    public @NotNull String[] rawArgs() {
        return rawArgs.clone();
    }

    /**
     * Get the parse metrics.
     *
     * @return the metrics
     */
    public @NotNull ParseMetrics metrics() {
        return metrics;
    }

    /**
     * Convert to a full CommandParseResult.
     * <p>
     * If there are errors, returns a failure result.
     * If complete and no errors, returns a success result with the parsed context.
     *
     * @return a CommandParseResult representation
     */
    public @NotNull CommandParseResult toFullResult() {
        if (!errors.isEmpty()) {
            return CommandParseResult.failureWithMetrics(errors, rawArgs, metrics);
        }
        if (complete) {
            CommandContext context = new CommandContext(
                parsedArguments,
                parsedFlags,
                parsedKeyValues,
                Collections.emptyMap(),
                rawArgs,
                aliasMap
            );
            return CommandParseResult.successWithMetrics(context, rawArgs, metrics);
        }
        // Incomplete but no errors - treat as incomplete error
        return CommandParseResult.failure(
            CommandParseError.usage("Parsing incomplete: only " + argumentsParsed + " arguments parsed"),
            rawArgs
        );
    }

    /**
     * Execute an action if parsing was successful (no errors).
     *
     * @param action the action to execute
     * @return this result for chaining
     */
    public @NotNull PartialParseResult ifSuccess(@NotNull Consumer<PartialParseResult> action) {
        Preconditions.checkNotNull(action, "action");
        if (errors.isEmpty()) {
            action.accept(this);
        }
        return this;
    }

    /**
     * Execute an action if parsing had errors.
     *
     * @param action the action to execute
     * @return this result for chaining
     */
    public @NotNull PartialParseResult ifError(@NotNull Consumer<List<CommandParseError>> action) {
        Preconditions.checkNotNull(action, "action");
        if (!errors.isEmpty()) {
            action.accept(errors);
        }
        return this;
    }

    @Override
    public String toString() {
        return "PartialParseResult{" +
               "parsedArgs=" + parsedArguments.size() +
               ", errors=" + errors.size() +
               ", complete=" + complete +
               ", errorAt=" + errorArgumentIndex +
               '}';
    }

    /**
     * Builder for PartialParseResult.
     */
    public static final class Builder {
        private final Map<String, Object> parsedArguments = new LinkedHashMap<>();
        private final Map<String, Boolean> parsedFlags = new LinkedHashMap<>();
        private final Map<String, Object> parsedKeyValues = new LinkedHashMap<>();
        private final List<CommandParseError> errors = new ArrayList<>();
        private final Map<String, String> aliasMap = new LinkedHashMap<>();
        private String[] rawArgs = new String[0];
        private int argumentsParsed = 0;
        private int errorArgumentIndex = -1;
        private boolean complete = false;
        private ParseMetrics metrics = ParseMetrics.EMPTY;

        private Builder() {}

        /**
         * Add a parsed argument.
         *
         * @param name  the argument name
         * @param value the parsed value
         * @return this builder
         */
        public @NotNull Builder withArgument(@NotNull String name, Object value) {
            parsedArguments.put(name, value);
            argumentsParsed++;
            return this;
        }

        /**
         * Add multiple parsed arguments.
         *
         * @param arguments map of arguments
         * @return this builder
         */
        public @NotNull Builder withArguments(@NotNull Map<String, Object> arguments) {
            parsedArguments.putAll(arguments);
            argumentsParsed = parsedArguments.size();
            return this;
        }

        /**
         * Add a parsed flag.
         *
         * @param name  the flag name
         * @param value the flag value
         * @return this builder
         */
        public @NotNull Builder withFlag(@NotNull String name, boolean value) {
            parsedFlags.put(name, value);
            return this;
        }

        /**
         * Add multiple parsed flags.
         *
         * @param flags map of flags
         * @return this builder
         */
        public @NotNull Builder withFlags(@NotNull Map<String, Boolean> flags) {
            parsedFlags.putAll(flags);
            return this;
        }

        /**
         * Add a parsed key-value pair.
         *
         * @param name  the key-value name
         * @param value the parsed value
         * @return this builder
         */
        public @NotNull Builder withKeyValue(@NotNull String name, Object value) {
            parsedKeyValues.put(name, value);
            return this;
        }

        /**
         * Add multiple parsed key-values.
         *
         * @param keyValues map of key-values
         * @return this builder
         */
        public @NotNull Builder withKeyValues(@NotNull Map<String, Object> keyValues) {
            parsedKeyValues.putAll(keyValues);
            return this;
        }

        /**
         * Add an error.
         *
         * @param error the error
         * @return this builder
         */
        public @NotNull Builder withError(@NotNull CommandParseError error) {
            errors.add(error);
            return this;
        }

        /**
         * Add an error at a specific argument index.
         *
         * @param error          the error
         * @param argumentIndex  the argument index where the error occurred
         * @return this builder
         */
        public @NotNull Builder withErrorAt(@NotNull CommandParseError error, int argumentIndex) {
            errors.add(error);
            if (this.errorArgumentIndex < 0) {
                this.errorArgumentIndex = argumentIndex;
            }
            return this;
        }

        /**
         * Set the raw arguments.
         *
         * @param rawArgs the raw arguments
         * @return this builder
         */
        public @NotNull Builder withRawArgs(@NotNull String[] rawArgs) {
            this.rawArgs = rawArgs.clone();
            return this;
        }

        /**
         * Set the number of arguments parsed.
         *
         * @param count the count
         * @return this builder
         */
        public @NotNull Builder argumentsParsed(int count) {
            this.argumentsParsed = count;
            return this;
        }

        /**
         * Set whether parsing is complete.
         *
         * @param complete true if all arguments were parsed
         * @return this builder
         */
        public @NotNull Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        /**
         * Set the metrics.
         *
         * @param metrics the metrics
         * @return this builder
         */
        public @NotNull Builder withMetrics(@NotNull ParseMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * Set the alias map.
         *
         * @param aliasMap mapping of aliases to primary argument names
         * @return this builder
         */
        public @NotNull Builder withAliasMap(@NotNull Map<String, String> aliasMap) {
            this.aliasMap.clear();
            this.aliasMap.putAll(aliasMap);
            return this;
        }

        /**
         * Build the PartialParseResult.
         *
         * @return the built result
         */
        public @NotNull PartialParseResult build() {
            return new PartialParseResult(this);
        }
    }
}
