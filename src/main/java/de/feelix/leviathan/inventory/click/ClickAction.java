package de.feelix.leviathan.inventory.click;

import de.feelix.leviathan.annotations.NotNull;

/**
 * Functional interface for handling clicks on inventory buttons.
 * Implementations should perform short, main-thread safe actions and
 * may throw exceptions which are caught and logged by the inventory system.
 */
@FunctionalInterface
public interface ClickAction {
    /**
     * Handle a click event within a FluentInventory.
     *
     * @param ctx contextual information about the click (never null)
     * @throws Exception to signal handler errors; callers will catch and log
     */
    void handle(@NotNull ClickContext ctx) throws Exception;
}
