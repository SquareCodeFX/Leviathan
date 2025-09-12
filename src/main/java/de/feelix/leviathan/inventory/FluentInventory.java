package de.feelix.leviathan.inventory;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.inventory.click.ClickContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A fluent, modular UI wrapper around a Bukkit Inventory.
 */
public class FluentInventory {

    private final int size;
    private String title;
    private final Map<Integer, ItemButton> buttons = new HashMap<>();
    private final Inventory inventory;

    private @Nullable Consumer<Player> onClose;
    private boolean cancelUnhandledClicks = true;

    private FluentInventory(int size, String title) {
        Preconditions.checkArgument(size % 9 == 0 && size >= 9 && size <= 54, "Inventory size must be multiple of 9 within [9,54]");
        this.size = size;
        this.title = ChatColor.translateAlternateColorCodes('&', Preconditions.checkNotBlank(title, "title"));
        this.inventory = Bukkit.createInventory(new UIHolder(this), size, this.title);
    }

    /**
     * Create a new FluentInventory with the given number of rows (1–6).
     *
     * @param rows  number of rows (must be within 1..6)
     * @param title inventory title; color codes using '&' are supported
     * @return a new FluentInventory instance
     */
    public static @NotNull FluentInventory ofRows(int rows, @NotNull String title) {
        Preconditions.checkArgument(rows >= 1 && rows <= 6, "rows must be in [1,6]");
        return new FluentInventory(rows * 9, title);
    }

    /**
     * Create a new FluentInventory with an explicit size in slots.
     * Size must be a multiple of 9 and within the standard chest bounds [9, 54].
     *
     * @param size  total number of slots (multiple of 9)
     * @param title inventory title; color codes using '&' are supported
     * @return a new FluentInventory instance
     */
    public static @NotNull FluentInventory ofSize(int size, @NotNull String title) {
        return new FluentInventory(size, title);
    }

    /**
     * Set a new title for this inventory. Color codes using '&' are supported.
     * Note: Bukkit does not support changing the title of an already open inventory;
     * consumers should close and reopen to apply a new title.
     *
     * @param title the new title (non-blank)
     * @return this instance for chaining
     */
    public @NotNull FluentInventory title(@NotNull String title) {
        this.title = ChatColor.translateAlternateColorCodes('&', Preconditions.checkNotBlank(title, "title"));
        // Note: Bukkit does not support retitling an open inventory; requires reopen.
        return this;
    }

    /**
     * Control whether clicks that do not target a registered ItemButton should be cancelled.
     * Defaults to true to protect UI state from accidental item movement.
     *
     * @param cancel true to cancel unhandled clicks; false to allow them
     * @return this instance for chaining
     */
    public @NotNull FluentInventory cancelUnhandledClicks(boolean cancel) {
        this.cancelUnhandledClicks = cancel;
        return this;
    }

    /**
     * Access the underlying Bukkit Inventory instance managed by this wrapper.
     */
    public @NotNull Inventory getInventory() { return inventory; }
    /**
     * Total number of slots in this inventory.
     */
    public int getSize() { return size; }
    /**
     * The configured title of this inventory. Note that changing the title requires reopening.
     */
    public @NotNull String getTitle() { return title; }

    /**
     * Register a callback to be executed when the player closes this inventory.
     * The callback runs on the server thread.
     *
     * @param onClose a consumer receiving the closing player; null to remove the callback
     * @return this instance for chaining
     */
    public @NotNull FluentInventory onClose(@Nullable Consumer<Player> onClose) {
        this.onClose = onClose;
        return this;
    }

    /**
     * Set or remove a button at the specified slot.
     *
     * @param slot   0-based slot index within this inventory
     * @param button the button to place, or null to clear the slot
     * @return this instance for chaining
     */
    public @NotNull FluentInventory set(int slot, @Nullable ItemButton button) {
        Preconditions.checkArgument(slot >= 0 && slot < size, "slot out of range");
        if (button == null) {
            buttons.remove(slot);
            inventory.setItem(slot, null);
        } else {
            buttons.put(slot, button);
            inventory.setItem(slot, button.getItem());
        }
        return this;
    }

    /**
     * Convenience overload to place an ItemStack with an inline click handler.
     *
     * @param slot   0-based slot index within this inventory
     * @param item   the item to display
     * @param action the click action to execute
     * @return this instance for chaining
     */
    public @NotNull FluentInventory set(int slot, @NotNull ItemStack item, @NotNull de.feelix.leviathan.inventory.click.ClickAction action) {
        return set(slot, ItemButton.of(item, action));
    }

    /**
     * Get the ItemButton currently registered at the given slot.
     *
     * @param slot 0-based slot index
     * @return the button instance or null if none is registered
     */
    public @Nullable ItemButton getButton(int slot) {
        return buttons.get(slot);
    }

    /**
     * Remove all registered buttons and clear the visual inventory contents.
     *
     * @return this instance for chaining
     */
    public @NotNull FluentInventory clear() {
        buttons.clear();
        inventory.clear();
        return this;
    }

    /**
     * Fill the entire inventory with copies of the given item.
     *
     * @param item the item to fill with (must not be null)
     * @return this instance for chaining
     */
    public @NotNull FluentInventory fill(@NotNull ItemStack item) {
        Fillers.fill(inventory, item);
        return this;
    }

    /**
     * Draw a border around the edges of the inventory using copies of the given item.
     *
     * @param item the item to use for the border (must not be null)
     * @return this instance for chaining
     */
    public @NotNull FluentInventory border(@NotNull ItemStack item) {
        Fillers.border(inventory, item);
        return this;
    }

    /**
     * Open this UI for the given player and start event tracking for it.
     *
     * @param player the player to show the inventory to (must not be null)
     */
    public void open(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        InventoryManager.get().trackOpen(player, this);
        player.openInventory(inventory);
    }

    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true); // General protection; specific components may allow moving in the future
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= size) {
            // clicked in bottom inventory
            if (cancelUnhandledClicks) {
                event.setCancelled(true);
            }
            return;
        }

        ItemButton button = buttons.get(raw);
        ClickContext ctx = new ClickContext(player, event, raw, event.getCurrentItem());

        if (button != null && button.isVisible(ctx)) {
            event.setCancelled(button.cancelClick());
            try {
                button.getAction().handle(ctx);
            } catch (Throwable t) {
                // swallow to protect gameplay; developer can log
                t.printStackTrace();
            }
        } else {
            event.setCancelled(cancelUnhandledClicks);
        }

        // Ensure visual is up to date (in case handlers modified items)
        ItemButton current = buttons.get(raw);
        if (current != null) {
            inventory.setItem(raw, current.getItem());
        }
    }

    /**
     * Internal close handler invoked by InventoryManager when the UI is closed.
     * Executes the registered onClose callback safely.
     */
    void handleClose(@NotNull Player player) {
        if (onClose != null) {
            try {
                onClose.accept(player);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    // Utility mapping helpers
    /**
     * Convert a human-friendly row/column pair to a 0-based slot index.
     * Rows are 1..6 and columns are 1..9.
     */
    public static int slot(int row1Based, int col1Based) {
        Preconditions.checkArgument(row1Based >= 1 && row1Based <= 6, "row must be in [1,6]");
        Preconditions.checkArgument(col1Based >= 1 && col1Based <= 9, "col must be in [1,9]");
        return (row1Based - 1) * 9 + (col1Based - 1);
    }

    /**
     * Get the 1-based row of a 0-based slot index.
     */
    public static int row(int slot) { return (slot / 9) + 1; }
    /**
     * Get the 1-based column of a 0-based slot index.
     */
    public static int col(int slot) { return (slot % 9) + 1; }

    @Override
    /**
     * Two FluentInventories are considered equal when they have the same size and refer to the same backing inventory.
     */
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluentInventory that)) return false;
        return size == that.size && Objects.equals(inventory, that.inventory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, inventory);
    }
}
