package de.feelix.leviathan.exceptions;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Thrown to indicate an error while loading or saving configuration files
 * via the FileAPI/config formats (I/O failures, parse errors, or persistence issues).
 * <p>
 * This exception is unchecked to simplify use in plugin code paths where
 * configuration access is common and typically not recoverable at the call site.
 */
public class ConfigException extends RuntimeException {
    /**
     * Create a new ConfigException with a message.
     *
     * @param message a non-null description of the configuration error
     */
    public ConfigException(@NotNull String message) {
        super(message);
    }

    /**
     * Create a new ConfigException with a message and a cause.
     *
     * @param message a non-null description of the configuration error
     * @param cause   the underlying cause
     */
    public ConfigException(@NotNull String message, Throwable cause) {
        super(message, cause);
    }
}
