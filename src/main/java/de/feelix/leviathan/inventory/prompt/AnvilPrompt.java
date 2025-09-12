package de.feelix.leviathan.inventory.prompt;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.inventory.InventoryManager;
import de.feelix.leviathan.itemstack.ItemStackBuilder;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Simple anvil rename prompt that captures the player's input string when they take the result item.
 */
public final class AnvilPrompt {

    private AnvilPrompt() {}

    /**
     * Open an anvil-based text prompt for the given player.
     * If the server cannot create an anvil inventory, this method falls back to {@link SignPrompt}.
     *
     * @param player      the target player (must not be null)
     * @param title       optional custom window title; defaults to a generic prompt
     * @param initialText optional initial text placed into the input item
     * @param onComplete  callback invoked with the entered text when the player confirms
     * @param onCancel    optional callback invoked if the prompt closes or times out without input
     */
    public static void open(@NotNull Player player,
                            @Nullable String title,
                            @Nullable String initialText,
                            @NotNull Consumer<String> onComplete,
                            @Nullable Runnable onCancel) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(onComplete, "onComplete");
        Plugin plugin = InventoryManager.get().plugin();

        String windowTitle = title == null ? "Enter text" : title;
        Inventory inv = Bukkit.createInventory(player, InventoryType.ANVIL, windowTitle);
        if (!(inv instanceof AnvilInventory anvil)) {
            // Fallback: cannot create anvil inventory on this server
            player.sendMessage("Anvil prompt not supported. Please type in chat:");
            player.sendMessage(initialText == null ? "" : initialText);
            // The SignPrompt fallback is adequate
            SignPrompt.open(player, title, onComplete, onCancel, 20L * 60);
            return;
        }

        ItemStack paper = ItemStackBuilder.create(Material.PAPER)
            .setName(initialText == null ? "" : initialText)
            .setMeta()
            .build();
        inv.setItem(0, paper);

        player.openInventory(inv);

        Listener listener = new Listener() {
            @EventHandler
            public void onClick(InventoryClickEvent e) {
                if (!(e.getWhoClicked() instanceof Player p)) return;
                if (!p.getUniqueId().equals(player.getUniqueId())) return;
                if (e.getInventory() != inv) return;
                // result slot is 2 in anvil
                if (e.getRawSlot() == 2) {
                    e.setCancelled(true);
                    String text;
                    try {
                        text = anvil.getRenameText();
                    } catch (NoSuchMethodError err) {
                        // Older API; try derive from item display name
                        ItemStack result = e.getCurrentItem();
                        text = result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName()
                                ? result.getItemMeta().getDisplayName() : "";
                    }
                    close();
                    onComplete.accept(text == null ? "" : text);
                } else {
                    // cancel interactions within the anvil inventory
                    if (e.getClickedInventory() == inv) e.setCancelled(true);
                }
            }

            @EventHandler
            public void onClose(InventoryCloseEvent e) {
                if (!(e.getPlayer() instanceof Player p)) return;
                if (!p.getUniqueId().equals(player.getUniqueId())) return;
                if (e.getInventory() != inv) return;
                close();
                if (onCancel != null) onCancel.run();
            }

            private void close() {
                InventoryCloseEvent.getHandlerList().unregister(this);
                InventoryClickEvent.getHandlerList().unregister(this);
            }
        };

        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }
}
