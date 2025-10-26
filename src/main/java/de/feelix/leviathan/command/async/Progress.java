package de.feelix.leviathan.command.async;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Progress reporter for long-running async command actions.
 * <p>
 * Implementations should be thread-safe, as they may be called from async execution contexts.
 * Progress messages are typically sent back to the command sender on the main server thread.
 */
@FunctionalInterface
public interface Progress {
    /**
     * Report progress to the command sender.
     *
     * @param message the progress message to send
     */
    void report(@NotNull String message);
}

