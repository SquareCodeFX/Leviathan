package de.feelix.leviathan.command.async;

/**
 * Cancellation token for cooperative cancellation of async command actions.
 * <p>
 * This class is used by async command handlers to check if the operation should be cancelled.
 * The token uses a volatile boolean for thread-safe cancellation signaling.
 */
public final class CancellationToken {
    private volatile boolean cancelled;

    /**
     * Marks this token as cancelled.
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Checks if this token has been cancelled.
     *
     * @return true if cancelled, false otherwise
     */
    public boolean cancelled() {
        return cancelled;
    }
}
