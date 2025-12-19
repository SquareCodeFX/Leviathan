package de.feelix.leviathan.command.argument;

import java.util.Optional;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

/**
 * Represents the outcome of parsing a single argument token.
 * <p>
 * Exactly one of {@code value} or {@code error} is present. Instances are immutable.
 * Use the factory methods {@link #success(Object)} and {@link #error(String)} to create results.
 *
 * @param <T> parsed value type
 */
public final class ParseResult<T> {
    private final T value;
    private final String error;

    private ParseResult(T value, String error) {
        this.value = value;
        this.error = error;
    }

    /**
     * Create a successful parse result.
     *
     * @param value parsed value (may be null, depending on parser contract)
     * @return success result
     */
    public static <T> @NotNull ParseResult<T> success(@Nullable T value) {
        return new ParseResult<>(value, null);
    }

    /**
     * Create a failed parse result with a human-readable error message.
     *
     * @param message error message to surface to the user
     * @return error result
     */
    public static <T> @NotNull ParseResult<T> error(@NotNull String message) {
        Preconditions.checkNotNull(message, "message");
        return new ParseResult<>(null, message);
    }

    /**
     * Alias for {@link #error(String)}.
     * Create a failed parse result with a human-readable error message.
     *
     * @param message error message to surface to the user
     * @return error result
     */
    public static <T> @NotNull ParseResult<T> failure(@NotNull String message) {
        return error(message);
    }

    /**
     * @return true if the parse succeeded
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * @return optional parsed value
     */
    public @NotNull Optional<T> value() {
        return Optional.ofNullable(value);
    }

    /**
     * @return optional error message
     */
    public @NotNull Optional<String> error() {
        return Optional.ofNullable(error);
    }

    /**
     * Direct accessor for error message.
     * @return the error message, or null if successful
     */
    public @Nullable String errorMessage() {
        return error;
    }
}
