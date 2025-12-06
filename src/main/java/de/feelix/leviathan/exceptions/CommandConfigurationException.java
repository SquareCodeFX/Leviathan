package de.feelix.leviathan.exceptions;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

/**
 * Thrown when a command is misconfigured by the plugin developer, e.g.,
 * required arguments after optional ones, duplicate argument names, invalid greedy placement,
 * or missing command declaration in plugin.yml during registration.
 */
public class CommandConfigurationException extends RuntimeException {
    /**
     * Create a new configuration exception.
     *
     * @param message human-readable description of the configuration mistake
     */
    public CommandConfigurationException(@NotNull String message) {
        super(Preconditions.checkNotNull(message, "message"));
    }

    /**
     * Create a new configuration exception with a cause.
     *
     * @param message human-readable description of the configuration mistake
     * @param cause   underlying cause
     */
    public CommandConfigurationException(@NotNull String message, @Nullable Throwable cause) {
        super(Preconditions.checkNotNull(message, "message"), cause);
    }
}
