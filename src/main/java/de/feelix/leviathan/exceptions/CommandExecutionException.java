package de.feelix.leviathan.exceptions;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

/**
 * Thrown when an exception occurs during execution of a command's action.
 * Wraps the original cause to preserve root-cause information without using
 * direct stack trace printing at the call site.
 */
public class CommandExecutionException extends RuntimeException {
    /**
     * Create a new command execution exception.
     *
     * @param message description of the execution error
     */
    public CommandExecutionException(@NotNull String message) {
        super(Preconditions.checkNotNull(message, "message"));
    }

    /**
     * Create a new command execution exception with a cause.
     *
     * @param message description of the execution error
     * @param cause   underlying cause (may be original throwable or its cause)
     */
    public CommandExecutionException(@NotNull String message, @Nullable Throwable cause) {
        super(Preconditions.checkNotNull(message, "message"), cause);
    }
}
