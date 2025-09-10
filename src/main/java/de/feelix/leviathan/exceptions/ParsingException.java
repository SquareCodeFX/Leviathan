package de.feelix.leviathan.exceptions;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

/**
 * Thrown for developer-side parsing configuration/implementation errors.
 * Not for user input mistakes. Use this to signal problems like:
 * <ul>
 *   <li>Building a parser with invalid state (e.g., empty oneOf)</li>
 *   <li>A parser implementation violating contracts (e.g., returning null lists)</li>
 * </ul>
 */
public class ParsingException extends RuntimeException {
    /**
     * Create a new parsing exception.
     * @param message human-readable description of the developer error
     */
    public ParsingException(@NotNull String message) {
        super(Preconditions.checkNotNull(message, "message"));
    }

    /**
     * Create a new parsing exception with a cause.
     * @param message human-readable description of the developer error
     * @param cause underlying cause
     */
    public ParsingException(@NotNull String message, @Nullable Throwable cause) {
        super(Preconditions.checkNotNull(message, "message"), cause);
    }
}
