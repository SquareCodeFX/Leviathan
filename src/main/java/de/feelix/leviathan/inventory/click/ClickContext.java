package de.feelix.leviathan.inventory.click;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Context information for a click inside a FluentInventory.
 */
public final class ClickContext {
    private final Player player;
    private final InventoryClickEvent event;
    private final int slot;
    private final @Nullable ItemStack clickedItem;

    /**
     * Create a new click context.
     *
     * @param player the clicking player (never null)
     * @param event  the underlying Bukkit click event (never null)
     * @param slot   the raw slot index in the top inventory that was clicked
     * @param clickedItem the ItemStack present in the clicked slot at the time of the event (may be null)
     */
    public ClickContext(@NotNull Player player, @NotNull InventoryClickEvent event, int slot, @Nullable ItemStack clickedItem) {
        this.player = player;
        this.event = event;
        this.slot = slot;
        this.clickedItem = clickedItem;
    }

    /**
         * The player who performed the click.
         */
        public @NotNull Player player() { return player; }
    /**
         * The underlying Bukkit InventoryClickEvent for advanced use cases.
         */
        public @NotNull InventoryClickEvent event() { return event; }
    /**
         * The raw slot index in the top inventory that was clicked (0-based).
         */
        public int slot() { return slot; }
    /**
         * The ItemStack that was present in the clicked slot at the time of the click.
         * May be null when the slot was empty.
         */
        public @Nullable ItemStack clickedItem() { return clickedItem; }

    /**
         * Convenience accessor for the Bukkit ClickType of this event.
         */
        public ClickType clickType() { return event.getClick(); }
}
