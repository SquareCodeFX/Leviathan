package de.feelix.leviathan.inventory;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Utilities to fill and border inventories with a specific ItemStack.
 */
public final class Fillers {
    private Fillers() {}

    /**
     * Fill every slot of the given inventory with a clone of the provided item.
     *
     * @param inv  the target inventory (must not be null)
     * @param item the item to fill with (must not be null); a cloned instance is used for placement
     */
    public static void fill(@NotNull Inventory inv, @NotNull ItemStack item) {
        Preconditions.checkNotNull(inv, "inv");
        Preconditions.checkNotNull(item, "item");
        ItemStack copy = item.clone();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, copy);
        }
    }

    /**
     * Draw a border around the outer edges of the inventory using clones of the provided item.
     * A standard chest inventory has 9 columns; rows are derived from inventory size.
     *
     * @param inv  the target inventory (must not be null)
     * @param item the item to place on the border (must not be null); a cloned instance is used for placement
     */
    public static void border(@NotNull Inventory inv, @NotNull ItemStack item) {
        Preconditions.checkNotNull(inv, "inv");
        Preconditions.checkNotNull(item, "item");
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack copy = item.clone();
        // top row
        for (int c = 0; c < 9; c++) inv.setItem(c, copy);
        // bottom row
        for (int c = 0; c < 9; c++) inv.setItem((rows - 1) * 9 + c, copy);
        // left/right columns
        for (int r = 1; r < rows - 1; r++) {
            inv.setItem(r * 9, copy);
            inv.setItem(r * 9 + 8, copy);
        }
    }
}
