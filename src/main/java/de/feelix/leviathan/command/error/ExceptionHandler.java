package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.bukkit.command.CommandSender;

/**
 * Exception handler for custom error handling during command execution.
 * <p>
 * Allows intercepting errors and providing custom messages or behaviors.
 * When a handler returns {@code true}, the default error message is suppressed.
 */
@FunctionalInterface
public interface ExceptionHandler {
    /**
     * Handle an exception that occurred during command processing.
     *
     * @param sender the command sender
     * @param errorType the type of error that occurred
     * @param message the default error message (can be null)
     * @param exception the exception that was thrown (can be null for non-exception errors)
     * @return true to suppress the default error message, false to allow it
     */
    boolean handle(@NotNull CommandSender sender, 
                  @NotNull ErrorType errorType, 
                  @Nullable String message, 
                  @Nullable Throwable exception);
}

