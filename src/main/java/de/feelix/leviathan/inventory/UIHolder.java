package de.feelix.leviathan.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Internal InventoryHolder used to tag inventories created by the Inventory API.
 * <p>
 * Bukkit routes inventory events based on the top inventory's holder. By attaching this
 * holder, InventoryManager can quickly recognize and route events to the owning FluentInventory.
 * This class is not part of the public API surface and may change without notice.
 * </p>
 */
final class UIHolder implements InventoryHolder {

    private final FluentInventory ui;

    /**
     * Create a new holder bound to the given FluentInventory instance.
     * @param ui owning UI instance
     */
    UIHolder(FluentInventory ui) {
        this.ui = ui;
    }

    /**
         * Return the owning FluentInventory instance.
         */
        FluentInventory ui() { return ui; }

    @Override
    /**
     * InventoryHolder contract. Returns the backing Bukkit Inventory of the owning UI.
     * Note: Bukkit does not read this value for event routing; we expose it for completeness.
     */
    public Inventory getInventory() {
        return ui.getInventory();
    }
}
