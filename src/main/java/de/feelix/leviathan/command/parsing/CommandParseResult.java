package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.command.error.ErrorType;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents the result of parsing command arguments.
 * <p>
 * This class holds either a successfully parsed {@link CommandContext} or a list of
 * {@link CommandParseError}s that occurred during parsing. It provides a fluent API
 * for handling both success and failure cases without sending messages directly to the user.
 * <p>
 * Usage examples:
 * <pre>{@code
 * // Basic usage
 * CommandParseResult result = command.parse(sender, label, args);
 * if (result.isSuccess()) {
 *     CommandContext ctx = result.context();
 *     // Use the context
 * } else {
 *     List<CommandParseError> errors = result.errors();
 *     // Handle errors as needed
 * }
 *
 * // Fluent API usage
 * command.parse(sender, label, args)
 *     .ifSuccess(ctx -> {
 *         // Execute logic with parsed context
 *     })
 *     .ifFailure(errors -> {
 *         // Handle errors
 *     });
 *
 * // Execute action directly if successful
 * command.parse(sender, label, args)
 *     .executeIfSuccess(sender, (s, ctx) -> {
 *         s.sendMessage("Command executed!");
 *     });
 *
 * // Send errors to user manually
 * command.parse(sender, label, args)
 *     .ifFailure(errors -> {
 *         errors.forEach(e -> sender.sendMessage(e.message()));
 *     });
 * }</pre>
 */
public final class CommandParseResult {

    private final @Nullable CommandContext context;
    private final @NotNull List<CommandParseError> errors;
    private final @NotNull String[] rawArgs;

    private CommandParseResult(@Nullable CommandContext context,
                               @NotNull List<CommandParseError> errors,
                               @NotNull String[] rawArgs) {
        this.context = context;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.rawArgs = rawArgs.clone();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a successful parse result with the given context.
     *
     * @param context the successfully parsed command context
     * @param rawArgs the original raw arguments
     * @return a successful parse result
     */
    public static @NotNull CommandParseResult success(@NotNull CommandContext context, @NotNull String[] rawArgs) {
        Preconditions.checkNotNull(context, "context");
        Preconditions.checkNotNull(rawArgs, "rawArgs");
        return new CommandParseResult(context, Collections.emptyList(), rawArgs);
    }

    /**
     * Create a failed parse result with the given errors.
     *
     * @param errors  the list of parsing errors
     * @param rawArgs the original raw arguments
     * @return a failed parse result
     */
    public static @NotNull CommandParseResult failure(@NotNull List<CommandParseError> errors, @NotNull String[] rawArgs) {
        Preconditions.checkNotNull(errors, "errors");
        Preconditions.checkNotNull(rawArgs, "rawArgs");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Failure result must have at least one error");
        }
        return new CommandParseResult(null, errors, rawArgs);
    }

    /**
     * Create a failed parse result with a single error.
     *
     * @param error   the parsing error
     * @param rawArgs the original raw arguments
     * @return a failed parse result
     */
    public static @NotNull CommandParseResult failure(@NotNull CommandParseError error, @NotNull String[] rawArgs) {
        Preconditions.checkNotNull(error, "error");
        return failure(Collections.singletonList(error), rawArgs);
    }

    /**
     * Create a failed parse result from an error type and message.
     *
     * @param type    the error type
     * @param message the error message
     * @param rawArgs the original raw arguments
     * @return a failed parse result
     */
    public static @NotNull CommandParseResult failure(@NotNull ErrorType type, @NotNull String message, @NotNull String[] rawArgs) {
        return failure(CommandParseError.of(type, message), rawArgs);
    }

    // ==================== State Accessors ====================

    /**
     * Check if parsing was successful.
     *
     * @return true if parsing succeeded and a context is available
     */
    public boolean isSuccess() {
        return context != null;
    }

    /**
     * Check if parsing failed.
     *
     * @return true if parsing failed and errors are available
     */
    public boolean isFailure() {
        return context == null;
    }

    /**
     * Get the parsed command context.
     *
     * @return the command context, or null if parsing failed
     */
    public @Nullable CommandContext context() {
        return context;
    }

    /**
     * Get the parsed command context, throwing if parsing failed.
     *
     * @return the command context
     * @throws IllegalStateException if parsing failed
     */
    public @NotNull CommandContext contextOrThrow() {
        if (context == null) {
            throw new IllegalStateException("Cannot get context from failed parse result. Errors: " +
                errors.stream().map(CommandParseError::message).collect(Collectors.joining(", ")));
        }
        return context;
    }

    /**
     * Get the parsed command context as an Optional.
     *
     * @return Optional containing the context if successful, empty otherwise
     */
    public @NotNull Optional<CommandContext> optionalContext() {
        return Optional.ofNullable(context);
    }

    /**
     * Get the list of parsing errors.
     *
     * @return an unmodifiable list of errors (empty if parsing succeeded)
     */
    public @NotNull List<CommandParseError> errors() {
        return errors;
    }

    /**
     * Get the first parsing error.
     *
     * @return the first error, or null if parsing succeeded
     */
    public @Nullable CommandParseError firstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    /**
     * Get the first parsing error as an Optional.
     *
     * @return Optional containing the first error if present
     */
    public @NotNull Optional<CommandParseError> optionalFirstError() {
        return errors.isEmpty() ? Optional.empty() : Optional.of(errors.get(0));
    }

    /**
     * Get the number of errors.
     *
     * @return the error count (0 if successful)
     */
    public int errorCount() {
        return errors.size();
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
     * Get the original raw arguments.
     *
     * @return a clone of the raw arguments array
     */
    public @NotNull String[] rawArgs() {
        return rawArgs.clone();
    }

    // ==================== Error Filtering ====================

    /**
     * Get all errors of a specific type.
     *
     * @param type the error type to filter by
     * @return list of errors matching the specified type
     */
    public @NotNull List<CommandParseError> errorsByType(@NotNull ErrorType type) {
        Preconditions.checkNotNull(type, "type");
        return errors.stream()
            .filter(e -> e.type() == type)
            .collect(Collectors.toList());
    }

    /**
     * Get all errors in a specific category.
     *
     * @param category the error category to filter by
     * @return list of errors in the specified category
     */
    public @NotNull List<CommandParseError> errorsByCategory(@NotNull ErrorType.ErrorCategory category) {
        Preconditions.checkNotNull(category, "category");
        return errors.stream()
            .filter(e -> e.type().getCategory() == category)
            .collect(Collectors.toList());
    }

    /**
     * Get all errors for a specific argument.
     *
     * @param argumentName the argument name to filter by
     * @return list of errors for the specified argument
     */
    public @NotNull List<CommandParseError> errorsForArgument(@NotNull String argumentName) {
        Preconditions.checkNotNull(argumentName, "argumentName");
        return errors.stream()
            .filter(e -> argumentName.equals(e.argumentName()))
            .collect(Collectors.toList());
    }

    /**
     * Check if there are any access-related errors.
     *
     * @return true if there are permission, player-only, or guard errors
     */
    public boolean hasAccessErrors() {
        return errors.stream().anyMatch(CommandParseError::isAccessError);
    }

    /**
     * Check if there are any input-related errors.
     *
     * @return true if there are parsing, validation, or usage errors
     */
    public boolean hasInputErrors() {
        return errors.stream().anyMatch(CommandParseError::isInputError);
    }

    // ==================== Fluent API ====================

    /**
     * Execute an action if parsing was successful.
     *
     * @param action the action to execute with the parsed context
     * @return this result for chaining
     */
    public @NotNull CommandParseResult ifSuccess(@NotNull Consumer<CommandContext> action) {
        Preconditions.checkNotNull(action, "action");
        if (context != null) {
            action.accept(context);
        }
        return this;
    }

    /**
     * Execute an action if parsing failed.
     *
     * @param action the action to execute with the error list
     * @return this result for chaining
     */
    public @NotNull CommandParseResult ifFailure(@NotNull Consumer<List<CommandParseError>> action) {
        Preconditions.checkNotNull(action, "action");
        if (context == null) {
            action.accept(errors);
        }
        return this;
    }

    /**
     * Execute a command action if parsing was successful.
     *
     * @param sender the command sender
     * @param action the action to execute (receives sender and context)
     * @return this result for chaining
     */
    public @NotNull CommandParseResult executeIfSuccess(@NotNull CommandSender sender,
                                                         @NotNull ExecuteAction action) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(action, "action");
        if (context != null) {
            action.execute(sender, context);
        }
        return this;
    }

    /**
     * Map the context to a different value if successful.
     *
     * @param mapper the mapping function
     * @param <T>    the result type
     * @return Optional containing the mapped value if successful
     */
    public <T> @NotNull Optional<T> map(@NotNull Function<CommandContext, T> mapper) {
        Preconditions.checkNotNull(mapper, "mapper");
        return context != null ? Optional.ofNullable(mapper.apply(context)) : Optional.empty();
    }

    /**
     * Map the context to a different value, or return a default if parsing failed.
     *
     * @param mapper       the mapping function
     * @param defaultValue the default value if parsing failed
     * @param <T>          the result type
     * @return the mapped value or default
     */
    public <T> T mapOrDefault(@NotNull Function<CommandContext, T> mapper, T defaultValue) {
        Preconditions.checkNotNull(mapper, "mapper");
        return context != null ? mapper.apply(context) : defaultValue;
    }

    /**
     * Get the context or a default value.
     *
     * @param defaultContext the default context if parsing failed
     * @return the parsed context or the default
     */
    public @NotNull CommandContext orElse(@NotNull CommandContext defaultContext) {
        Preconditions.checkNotNull(defaultContext, "defaultContext");
        return context != null ? context : defaultContext;
    }

    /**
     * Get the context or throw a custom exception.
     *
     * @param exceptionSupplier supplier for the exception to throw
     * @param <X>               the exception type
     * @return the parsed context
     * @throws X if parsing failed
     */
    public <X extends Throwable> @NotNull CommandContext orElseThrow(
        @NotNull java.util.function.Supplier<? extends X> exceptionSupplier) throws X {
        Preconditions.checkNotNull(exceptionSupplier, "exceptionSupplier");
        if (context != null) {
            return context;
        }
        throw exceptionSupplier.get();
    }

    // ==================== Error Message Utilities ====================

    /**
     * Get all error messages as a list of strings.
     *
     * @return list of error messages
     */
    public @NotNull List<String> errorMessages() {
        return errors.stream()
            .map(CommandParseError::message)
            .collect(Collectors.toList());
    }

    /**
     * Get all error messages joined with the specified delimiter.
     *
     * @param delimiter the delimiter to join messages with
     * @return joined error messages
     */
    public @NotNull String joinedErrorMessages(@NotNull String delimiter) {
        Preconditions.checkNotNull(delimiter, "delimiter");
        return errors.stream()
            .map(CommandParseError::message)
            .collect(Collectors.joining(delimiter));
    }

    /**
     * Get all formatted error strings.
     *
     * @return list of formatted error strings
     */
    public @NotNull List<String> formattedErrors() {
        return errors.stream()
            .map(CommandParseError::toFormattedString)
            .collect(Collectors.toList());
    }

    /**
     * Send all error messages to a sender.
     * <p>
     * This is a convenience method for when you want to forward errors to the user.
     *
     * @param sender the sender to receive the error messages
     * @return this result for chaining
     */
    public @NotNull CommandParseResult sendErrorsTo(@NotNull CommandSender sender) {
        Preconditions.checkNotNull(sender, "sender");
        for (CommandParseError error : errors) {
            sender.sendMessage(error.message());
        }
        return this;
    }

    /**
     * Send only the first error message to a sender.
     *
     * @param sender the sender to receive the error message
     * @return this result for chaining
     */
    public @NotNull CommandParseResult sendFirstErrorTo(@NotNull CommandSender sender) {
        Preconditions.checkNotNull(sender, "sender");
        if (!errors.isEmpty()) {
            sender.sendMessage(errors.get(0).message());
        }
        return this;
    }

    // ==================== Functional Interface ====================

    /**
     * Functional interface for executing command actions.
     */
    @FunctionalInterface
    public interface ExecuteAction {
        /**
         * Execute the command action.
         *
         * @param sender  the command sender
         * @param context the parsed command context
         */
        void execute(@NotNull CommandSender sender, @NotNull CommandContext context);
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        if (context != null) {
            return "CommandParseResult{success=true, context=" + context + "}";
        } else {
            return "CommandParseResult{success=false, errors=" + errors + "}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandParseResult)) return false;
        CommandParseResult that = (CommandParseResult) o;
        return Objects.equals(context, that.context) &&
               errors.equals(that.errors) &&
               Arrays.equals(rawArgs, that.rawArgs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(context, errors);
        result = 31 * result + Arrays.hashCode(rawArgs);
        return result;
    }
}
