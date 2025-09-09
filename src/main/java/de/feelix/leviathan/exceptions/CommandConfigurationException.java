package de.feelix.leviathan.exceptions;

/**
 * Thrown when a command is misconfigured by the plugin developer, e.g.,
 * required arguments after optional ones, duplicate argument names, invalid greedy placement,
 * or missing command declaration in plugin.yml during registration.
 */
public class CommandConfigurationException extends RuntimeException {
    /**
     * Create a new configuration exception.
     * @param message human-readable description of the configuration mistake
     */
    public CommandConfigurationException(String message) {
        super(message);
    }

    /**
     * Create a new configuration exception with a cause.
     * @param message human-readable description of the configuration mistake
     * @param cause underlying cause
     */
    public CommandConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
