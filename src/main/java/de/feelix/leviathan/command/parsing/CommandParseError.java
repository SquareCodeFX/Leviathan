package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.error.ErrorType;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single parsing error that occurred during command argument parsing.
 * <p>
 * This class is immutable and provides detailed information about parsing failures,
 * including the error type, human-readable message, and optionally the argument name
 * and raw input that caused the error.
 * <p>
 * Example usage:
 * <pre>{@code
 * CommandParseError error = CommandParseError.of(ErrorType.PARSING, "Invalid integer value")
 *     .forArgument("count")
 *     .withInput("abc");
 * }</pre>
 */
public final class CommandParseError {

    private final ErrorType type;
    private final String message;
    private final @Nullable String argumentName;
    private final @Nullable String rawInput;
    private final @NotNull List<String> suggestions;

    private CommandParseError(@NotNull ErrorType type,
                              @NotNull String message,
                              @Nullable String argumentName,
                              @Nullable String rawInput,
                              @Nullable List<String> suggestions) {
        this.type = Preconditions.checkNotNull(type, "type");
        this.message = Preconditions.checkNotNull(message, "message");
        this.argumentName = argumentName;
        this.rawInput = rawInput;
        this.suggestions = suggestions != null
            ? Collections.unmodifiableList(new ArrayList<>(suggestions))
            : Collections.emptyList();
    }

    // ==================== Factory Methods ====================

    /**
     * Create a new parse error with the specified type and message.
     *
     * @param type    the error type category
     * @param message the human-readable error message
     * @return a new CommandParseError instance
     */
    public static @NotNull CommandParseError of(@NotNull ErrorType type, @NotNull String message) {
        return new CommandParseError(type, message, null, null, null);
    }

    /**
     * Create a permission error.
     *
     * @param message the permission error message
     * @return a new CommandParseError for permission failures
     */
    public static @NotNull CommandParseError permission(@NotNull String message) {
        return new CommandParseError(ErrorType.PERMISSION, message, null, null, null);
    }

    /**
     * Create a player-only error.
     *
     * @param message the player-only error message
     * @return a new CommandParseError for player-only commands
     */
    public static @NotNull CommandParseError playerOnly(@NotNull String message) {
        return new CommandParseError(ErrorType.PLAYER_ONLY, message, null, null, null);
    }

    /**
     * Create a guard failed error.
     *
     * @param message the guard failure message
     * @return a new CommandParseError for guard failures
     */
    public static @NotNull CommandParseError guardFailed(@NotNull String message) {
        return new CommandParseError(ErrorType.GUARD_FAILED, message, null, null, null);
    }

    /**
     * Create a cooldown error.
     *
     * @param message the cooldown error message (should include remaining time)
     * @return a new CommandParseError for cooldown failures
     */
    public static @NotNull CommandParseError cooldown(@NotNull String message) {
        return new CommandParseError(ErrorType.GUARD_FAILED, message, null, null, null);
    }

    /**
     * Create a confirmation required error.
     *
     * @param message the confirmation message
     * @return a new CommandParseError for confirmation requirements
     */
    public static @NotNull CommandParseError confirmationRequired(@NotNull String message) {
        return new CommandParseError(ErrorType.GUARD_FAILED, message, null, null, null);
    }

    /**
     * Create a parsing error for a specific argument.
     *
     * @param argumentName the name of the argument that failed parsing
     * @param message      the parsing error message
     * @return a new CommandParseError for parsing failures
     */
    public static @NotNull CommandParseError parsing(@NotNull String argumentName, @NotNull String message) {
        return new CommandParseError(ErrorType.PARSING, message, argumentName, null, null);
    }

    /**
     * Create a validation error for a specific argument.
     *
     * @param argumentName the name of the argument that failed validation
     * @param message      the validation error message
     * @return a new CommandParseError for validation failures
     */
    public static @NotNull CommandParseError validation(@NotNull String argumentName, @NotNull String message) {
        return new CommandParseError(ErrorType.VALIDATION, message, argumentName, null, null);
    }

    /**
     * Create an argument permission error.
     *
     * @param argumentName the name of the argument requiring permission
     * @param message      the permission error message
     * @return a new CommandParseError for argument permission failures
     */
    public static @NotNull CommandParseError argumentPermission(@NotNull String argumentName, @NotNull String message) {
        return new CommandParseError(ErrorType.ARGUMENT_PERMISSION, message, argumentName, null, null);
    }

    /**
     * Create a usage error (wrong number of arguments).
     *
     * @param message the usage error message
     * @return a new CommandParseError for usage failures
     */
    public static @NotNull CommandParseError usage(@NotNull String message) {
        return new CommandParseError(ErrorType.USAGE, message, null, null, null);
    }

    /**
     * Create a cross-argument validation error.
     *
     * @param message the cross-validation error message
     * @return a new CommandParseError for cross-argument validation failures
     */
    public static @NotNull CommandParseError crossValidation(@NotNull String message) {
        return new CommandParseError(ErrorType.CROSS_VALIDATION, message, null, null, null);
    }

    /**
     * Create an internal error.
     *
     * @param message the internal error message
     * @return a new CommandParseError for internal errors
     */
    public static @NotNull CommandParseError internal(@NotNull String message) {
        return new CommandParseError(ErrorType.INTERNAL_ERROR, message, null, null, null);
    }

    /**
     * Create a subcommand not found error.
     *
     * @param subcommandName the name of the subcommand that was not found
     * @param message        the error message
     * @return a new CommandParseError for subcommand not found
     */
    public static @NotNull CommandParseError subcommandNotFound(@NotNull String subcommandName, @NotNull String message) {
        return new CommandParseError(ErrorType.USAGE, message, null, subcommandName, null);
    }

    /**
     * Create a subcommand not found error with suggestions.
     *
     * @param subcommandName the name of the subcommand that was not found
     * @param message        the error message
     * @param suggestions    similar subcommand suggestions
     * @return a new CommandParseError for subcommand not found with suggestions
     */
    public static @NotNull CommandParseError subcommandNotFound(@NotNull String subcommandName,
                                                                 @NotNull String message,
                                                                 @NotNull List<String> suggestions) {
        return new CommandParseError(ErrorType.USAGE, message, null, subcommandName, suggestions);
    }

    // ==================== Builder Methods (Immutable) ====================

    /**
     * Create a new error with the specified argument name.
     *
     * @param argumentName the argument name
     * @return a new CommandParseError with the argument name set
     */
    public @NotNull CommandParseError forArgument(@NotNull String argumentName) {
        Preconditions.checkNotNull(argumentName, "argumentName");
        return new CommandParseError(this.type, this.message, argumentName, this.rawInput, this.suggestions);
    }

    /**
     * Create a new error with the specified raw input.
     *
     * @param input the raw input that caused the error
     * @return a new CommandParseError with the raw input set
     */
    public @NotNull CommandParseError withInput(@NotNull String input) {
        Preconditions.checkNotNull(input, "input");
        return new CommandParseError(this.type, this.message, this.argumentName, input, this.suggestions);
    }

    /**
     * Create a new error with a different message.
     *
     * @param message the new message
     * @return a new CommandParseError with the updated message
     */
    public @NotNull CommandParseError withMessage(@NotNull String message) {
        Preconditions.checkNotNull(message, "message");
        return new CommandParseError(this.type, message, this.argumentName, this.rawInput, this.suggestions);
    }

    /**
     * Create a new error with "did you mean" suggestions.
     *
     * @param suggestions list of suggested corrections
     * @return a new CommandParseError with suggestions
     */
    public @NotNull CommandParseError withSuggestions(@NotNull List<String> suggestions) {
        Preconditions.checkNotNull(suggestions, "suggestions");
        return new CommandParseError(this.type, this.message, this.argumentName, this.rawInput, suggestions);
    }

    /**
     * Create a new error with "did you mean" suggestions.
     *
     * @param suggestions varargs suggested corrections
     * @return a new CommandParseError with suggestions
     */
    public @NotNull CommandParseError withSuggestions(@NotNull String... suggestions) {
        Preconditions.checkNotNull(suggestions, "suggestions");
        return new CommandParseError(this.type, this.message, this.argumentName, this.rawInput,
            java.util.Arrays.asList(suggestions));
    }

    // ==================== Accessors ====================

    /**
     * Get the error type category.
     *
     * @return the error type
     */
    public @NotNull ErrorType type() {
        return type;
    }

    /**
     * Get the human-readable error message.
     *
     * @return the error message
     */
    public @NotNull String message() {
        return message;
    }

    /**
     * Get the name of the argument that caused the error, if applicable.
     *
     * @return the argument name, or null if not applicable
     */
    public @Nullable String argumentName() {
        return argumentName;
    }

    /**
     * Get the raw input that caused the error, if available.
     *
     * @return the raw input, or null if not available
     */
    public @Nullable String rawInput() {
        return rawInput;
    }

    /**
     * Check if this error is related to a specific argument.
     *
     * @return true if an argument name is associated with this error
     */
    public boolean hasArgumentName() {
        return argumentName != null;
    }

    /**
     * Check if this error has raw input information.
     *
     * @return true if raw input is available
     */
    public boolean hasRawInput() {
        return rawInput != null;
    }

    /**
     * Get the "did you mean" suggestions for this error.
     *
     * @return an unmodifiable list of suggestions (empty if none)
     */
    public @NotNull List<String> suggestions() {
        return suggestions;
    }

    /**
     * Check if this error has suggestions.
     *
     * @return true if suggestions are available
     */
    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }

    /**
     * Check if this error is in the access category (permission, player-only, guard).
     *
     * @return true if this is an access-related error
     */
    public boolean isAccessError() {
        return type.getCategory() == ErrorType.ErrorCategory.ACCESS;
    }

    /**
     * Check if this error is in the input category (parsing, validation, usage).
     *
     * @return true if this is an input-related error
     */
    public boolean isInputError() {
        return type.getCategory() == ErrorType.ErrorCategory.INPUT;
    }

    /**
     * Get a formatted string representation of this error suitable for display.
     *
     * @return a formatted error string
     */
    public @NotNull String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type.name()).append("] ");
        if (argumentName != null) {
            sb.append("Argument '").append(argumentName).append("': ");
        }
        sb.append(message);
        if (rawInput != null) {
            sb.append(" (input: '").append(rawInput).append("')");
        }
        if (!suggestions.isEmpty()) {
            sb.append(" Did you mean: ").append(String.join(", ", suggestions)).append("?");
        }
        return sb.toString();
    }

    /**
     * Get a user-friendly message including suggestions if available.
     *
     * @return the message with optional suggestions
     */
    public @NotNull String toUserMessage() {
        if (suggestions.isEmpty()) {
            return message;
        }
        return message + " Did you mean: " + String.join(", ", suggestions) + "?";
    }

    @Override
    public String toString() {
        return "CommandParseError{" +
               "type=" + type +
               ", message='" + message + '\'' +
               (argumentName != null ? ", argument='" + argumentName + '\'' : "") +
               (rawInput != null ? ", input='" + rawInput + '\'' : "") +
               (!suggestions.isEmpty() ? ", suggestions=" + suggestions : "") +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandParseError)) return false;
        CommandParseError that = (CommandParseError) o;
        return type == that.type &&
               message.equals(that.message) &&
               java.util.Objects.equals(argumentName, that.argumentName) &&
               java.util.Objects.equals(rawInput, that.rawInput) &&
               suggestions.equals(that.suggestions);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, message, argumentName, rawInput, suggestions);
    }
}
