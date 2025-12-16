package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.command.error.ErrorType;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;

/**
 * Builder for creating {@link CommandParseResult} instances, primarily for testing purposes.
 * <p>
 * This builder allows you to manually construct parse results without actually running
 * the parsing logic, which is useful for unit testing command handlers.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create a successful result for testing
 * CommandParseResult mockResult = ParseResultBuilder.success()
 *     .withArgument("player", mockPlayer)
 *     .withArgument("amount", 10)
 *     .withFlag("silent", true)
 *     .withKeyValue("reason", "test")
 *     .build();
 *
 * // Create a failure result for testing error handling
 * CommandParseResult errorResult = ParseResultBuilder.failure()
 *     .withError(CommandParseError.parsing("amount", "Invalid number"))
 *     .build();
 * }</pre>
 */
public final class ParseResultBuilder {

    private boolean success;
    private final Map<String, Object> arguments = new LinkedHashMap<>();
    private final Map<String, Boolean> flags = new LinkedHashMap<>();
    private final Map<String, Object> keyValues = new LinkedHashMap<>();
    private final Map<String, List<Object>> multiValues = new LinkedHashMap<>();
    private final Map<String, String> aliasMap = new LinkedHashMap<>();
    private final List<CommandParseError> errors = new ArrayList<>();
    private String[] rawArgs = new String[0];
    private ParseMetrics metrics = ParseMetrics.EMPTY;

    private ParseResultBuilder(boolean success) {
        this.success = success;
    }

    /**
     * Create a builder for a successful parse result.
     *
     * @return a new builder configured for success
     */
    public static @NotNull ParseResultBuilder success() {
        return new ParseResultBuilder(true);
    }

    /**
     * Create a builder for a failed parse result.
     *
     * @return a new builder configured for failure
     */
    public static @NotNull ParseResultBuilder failure() {
        return new ParseResultBuilder(false);
    }

    /**
     * Add a parsed argument value.
     *
     * @param name  the argument name
     * @param value the parsed value
     * @return this builder
     */
    public @NotNull ParseResultBuilder withArgument(@NotNull String name, Object value) {
        Preconditions.checkNotNull(name, "name");
        arguments.put(name, value);
        return this;
    }

    /**
     * Add multiple argument values.
     *
     * @param arguments map of argument names to values
     * @return this builder
     */
    public @NotNull ParseResultBuilder withArguments(@NotNull Map<String, Object> arguments) {
        Preconditions.checkNotNull(arguments, "arguments");
        this.arguments.putAll(arguments);
        return this;
    }

    /**
     * Add a flag value.
     *
     * @param name  the flag name
     * @param value the flag value (true/false)
     * @return this builder
     */
    public @NotNull ParseResultBuilder withFlag(@NotNull String name, boolean value) {
        Preconditions.checkNotNull(name, "name");
        flags.put(name, value);
        return this;
    }

    /**
     * Add a key-value pair.
     *
     * @param name  the key-value name
     * @param value the parsed value
     * @return this builder
     */
    public @NotNull ParseResultBuilder withKeyValue(@NotNull String name, Object value) {
        Preconditions.checkNotNull(name, "name");
        keyValues.put(name, value);
        return this;
    }

    /**
     * Add a multi-value entry.
     *
     * @param name   the multi-value name
     * @param values the list of values
     * @return this builder
     */
    public @NotNull ParseResultBuilder withMultiValue(@NotNull String name, @NotNull List<Object> values) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(values, "values");
        multiValues.put(name, new ArrayList<>(values));
        return this;
    }

    /**
     * Add a multi-value entry with varargs.
     *
     * @param name   the multi-value name
     * @param values the values
     * @return this builder
     */
    public @NotNull ParseResultBuilder withMultiValue(@NotNull String name, Object... values) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(values, "values");
        multiValues.put(name, Arrays.asList(values));
        return this;
    }

    /**
     * Add an error to the result.
     *
     * @param error the parse error
     * @return this builder
     */
    public @NotNull ParseResultBuilder withError(@NotNull CommandParseError error) {
        Preconditions.checkNotNull(error, "error");
        errors.add(error);
        this.success = false;
        return this;
    }

    /**
     * Add multiple errors to the result.
     *
     * @param errors the parse errors
     * @return this builder
     */
    public @NotNull ParseResultBuilder withErrors(@NotNull List<CommandParseError> errors) {
        Preconditions.checkNotNull(errors, "errors");
        this.errors.addAll(errors);
        if (!errors.isEmpty()) {
            this.success = false;
        }
        return this;
    }

    /**
     * Add a simple error by type and message.
     *
     * @param type    the error type
     * @param message the error message
     * @return this builder
     */
    public @NotNull ParseResultBuilder withError(@NotNull ErrorType type, @NotNull String message) {
        return withError(CommandParseError.of(type, message));
    }

    /**
     * Add a parsing error for an argument.
     *
     * @param argumentName the argument name
     * @param message      the error message
     * @return this builder
     */
    public @NotNull ParseResultBuilder withParsingError(@NotNull String argumentName, @NotNull String message) {
        return withError(CommandParseError.parsing(argumentName, message));
    }

    /**
     * Add a validation error for an argument.
     *
     * @param argumentName the argument name
     * @param message      the error message
     * @return this builder
     */
    public @NotNull ParseResultBuilder withValidationError(@NotNull String argumentName, @NotNull String message) {
        return withError(CommandParseError.validation(argumentName, message));
    }

    /**
     * Add a permission error.
     *
     * @param message the error message
     * @return this builder
     */
    public @NotNull ParseResultBuilder withPermissionError(@NotNull String message) {
        return withError(CommandParseError.permission(message));
    }

    /**
     * Set the raw arguments array.
     *
     * @param rawArgs the raw arguments
     * @return this builder
     */
    public @NotNull ParseResultBuilder withRawArgs(@NotNull String... rawArgs) {
        Preconditions.checkNotNull(rawArgs, "rawArgs");
        this.rawArgs = rawArgs.clone();
        return this;
    }

    /**
     * Set the parse metrics.
     *
     * @param metrics the metrics
     * @return this builder
     */
    public @NotNull ParseResultBuilder withMetrics(@NotNull ParseMetrics metrics) {
        Preconditions.checkNotNull(metrics, "metrics");
        this.metrics = metrics;
        return this;
    }

    /**
     * Set the alias map for argument alias resolution.
     *
     * @param aliasMap mapping of aliases to primary argument names
     * @return this builder
     */
    public @NotNull ParseResultBuilder withAliasMap(@NotNull Map<String, String> aliasMap) {
        Preconditions.checkNotNull(aliasMap, "aliasMap");
        this.aliasMap.clear();
        this.aliasMap.putAll(aliasMap);
        return this;
    }

    /**
     * Add a single alias mapping.
     *
     * @param alias       the alias name
     * @param primaryName the primary argument name
     * @return this builder
     */
    public @NotNull ParseResultBuilder withAlias(@NotNull String alias, @NotNull String primaryName) {
        Preconditions.checkNotNull(alias, "alias");
        Preconditions.checkNotNull(primaryName, "primaryName");
        this.aliasMap.put(alias, primaryName);
        return this;
    }

    /**
     * Build the parse result.
     *
     * @return a new CommandParseResult
     * @throws IllegalStateException if building a failure result without errors
     */
    public @NotNull CommandParseResult build() {
        if (success) {
            CommandContext context = new CommandContext(
                arguments,
                flags,
                keyValues,
                multiValues,
                rawArgs,
                aliasMap
            );
            return CommandParseResult.successWithMetrics(context, rawArgs, metrics);
        } else {
            if (errors.isEmpty()) {
                throw new IllegalStateException("Failure result must have at least one error");
            }
            return CommandParseResult.failureWithMetrics(errors, rawArgs, metrics);
        }
    }

    /**
     * Create a copy of an existing result with modifications.
     *
     * @param original the original result to copy from
     * @return a new builder initialized with the original's values
     */
    public static @NotNull ParseResultBuilder from(@NotNull CommandParseResult original) {
        Preconditions.checkNotNull(original, "original");
        ParseResultBuilder builder = new ParseResultBuilder(original.isSuccess());
        builder.rawArgs = original.rawArgs();
        builder.metrics = original.metrics();

        if (original.isSuccess()) {
            CommandContext ctx = original.context();
            if (ctx != null) {
                builder.arguments.putAll(ctx.allArguments());
                builder.flags.putAll(ctx.allFlags());
                builder.keyValues.putAll(ctx.allKeyValues());
                builder.aliasMap.putAll(ctx.aliasMap());
            }
        } else {
            builder.errors.addAll(original.errors());
        }

        return builder;
    }
}
