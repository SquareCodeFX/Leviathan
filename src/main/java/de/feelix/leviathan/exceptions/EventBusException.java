package de.feelix.leviathan.exceptions;

import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

/**
 * Runtime exception thrown when a listener invocation fails or an event cannot be posted.
 */
public class EventBusException extends RuntimeException {
    public EventBusException(@NotNull String message) {
        super(Preconditions.checkNotNull(message, "message"));
    }

    public EventBusException(@NotNull String message, @Nullable Throwable cause) {
        super(Preconditions.checkNotNull(message, "message"), cause);
    }
}
