package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import org.bukkit.command.CommandSender;

/**
 * Functional interface representing the action to execute when a command invocation
 * has been successfully parsed and validated.
 * <p>
 * This is the synchronous command execution handler. For asynchronous execution
 * with cancellation and progress reporting support, see {@link AsyncCommandAction}.
 */
@FunctionalInterface
public interface CommandAction {
    /**
     * Execute the command action.
     *
     * @param sender The command sender.
     * @param ctx    Parsed argument context.
     */
    void execute(@NotNull CommandSender sender, @NotNull CommandContext ctx);
}
