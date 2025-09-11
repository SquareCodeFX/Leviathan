package de.feelix.leviathan.event;

/**
 * Indicates that an {@link Event} can be cancelled to stop further propagation
 * to listeners that do not opt-in to receive cancelled events.
 */
public interface Cancellable extends Event {
    boolean isCancelled();
    void setCancelled(boolean cancelled);
}
