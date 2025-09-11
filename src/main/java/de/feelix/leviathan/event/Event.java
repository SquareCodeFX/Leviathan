package de.feelix.leviathan.event;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Marker interface for all events published on the EventBus.
 */
public interface Event {
    /**
     * Convenience method to return this event instance.
     * Helps with fluent patterns when posting events.
     */
    default @NotNull Event self() {
        return this;
    }
}
