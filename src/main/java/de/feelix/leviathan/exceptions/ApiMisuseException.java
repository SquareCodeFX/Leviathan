package de.feelix.leviathan.exceptions;

/**
 * Thrown when the command API is incorrectly used by a developer at runtime,
 * such as requesting a context value with the wrong type or similar misuse
 * that is not a user input error.
 */
public class ApiMisuseException extends RuntimeException {
    /**
     * Create a new API misuse exception.
     * @param message human-readable description of the misuse
     */
    public ApiMisuseException(String message) {
        super(message);
    }

    /**
     * Create a new API misuse exception with a cause.
     * @param message human-readable description of the misuse
     * @param cause underlying cause
     */
    public ApiMisuseException(String message, Throwable cause) {
        super(message, cause);
    }
}
