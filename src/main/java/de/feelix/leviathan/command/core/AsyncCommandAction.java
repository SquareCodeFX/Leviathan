package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.async.CancellationToken;
import de.feelix.leviathan.command.async.Progress;
import org.bukkit.command.CommandSender;

/**
 * Extended asynchronous command action supporting cancellation and progress reporting.
 * <p>
 * This interface provides advanced async execution capabilities including:
 * <ul>
 *   <li>Cooperative cancellation via {@link CancellationToken}</li>
 *   <li>Progress reporting via {@link Progress}</li>
 *   <li>Optional timeout support</li>
 * </ul>
 * Use via Builder.executesAsync(AsyncCommandAction, timeoutMillis).
 */
@FunctionalInterface
public interface AsyncCommandAction {
    /**
     * Execute the async command action.
     *
     * @param sender the command sender
     * @param ctx parsed argument context
     * @param token cancellation token to check for cancellation requests
     * @param progress progress reporter for sending status updates
     */
    void execute(@NotNull CommandSender sender,
                 @NotNull CommandContext ctx,
                 @NotNull CancellationToken token,
                 @NotNull Progress progress);
}
