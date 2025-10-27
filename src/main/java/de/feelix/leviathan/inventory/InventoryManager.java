package de.feelix.leviathan.inventory;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for fluent inventories. Must be initialized once with your Plugin instance.
 * Usage: InventoryManager.init(plugin);
 */
public final class InventoryManager implements Listener {

    private static volatile InventoryManager INSTANCE;

    private final Plugin plugin;
    private final Map<UUID, FluentInventory> openByPlayer = new ConcurrentHashMap<>();

    private InventoryManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the inventory manager and register its event listeners.
     * <p>
     * This method must be called once during plugin startup (e.g., in onEnable).
     * Subsequent calls are ignored. All UI features rely on the manager being initialized.
     * </p>
     *
     * @param plugin your plugin instance (must not be null)
     */
    public static synchronized void init(@NotNull Plugin plugin) {
        Preconditions.checkNotNull(plugin, "plugin");
        if (INSTANCE == null) {
            INSTANCE = new InventoryManager(plugin);
            Bukkit.getPluginManager().registerEvents(INSTANCE, plugin);
        }
    }

    /**
     * Get the initialized InventoryManager singleton.
     *
     * @return the manager instance
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if {@link #init(Plugin)} was not called yet
     */
    public static @NotNull InventoryManager get() {
        Preconditions.checkState(INSTANCE != null, "InventoryManager.init(plugin) must be called before use");
        return INSTANCE;
    }

    /**
         * The plugin that owns and registered this manager.
         */
        public Plugin plugin() { return plugin; }

    /**
     * Associate a player with a currently open FluentInventory.
     * Internal API used by FluentInventory when opening a UI.
     */
    void trackOpen(@NotNull Player player, @NotNull FluentInventory inv) {
        openByPlayer.put(player.getUniqueId(), inv);
    }

    /**
     * Remove the association between a player and an open UI.
     * Internal API used when a player closes their UI.
     */
    void trackClose(@NotNull Player player) {
        openByPlayer.remove(player.getUniqueId());
    }

    /**
     * Get the FluentInventory currently open for the given player, if any.
     *
     * @param player the player to look up (must not be null)
     * @return the open UI instance, or null if none is tracked
     */
    @Nullable FluentInventory getOpen(@NotNull Player player) {
        return openByPlayer.get(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof UIHolder)) return;

        FluentInventory inv = getOpen(player);
        if (inv == null || inv.getInventory() != top) return;

        inv.handleClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof UIHolder)) return;

        FluentInventory inv = getOpen(player);
        if (inv == null || inv.getInventory() != top) return;

        // Prevent dragging by default; components can override via onDrag if needed later.
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof UIHolder)) return;

        FluentInventory inv = getOpen(player);
        if (inv != null && inv.getInventory() == top) {
            try {
                inv.handleClose(player);
            } finally {
                trackClose(player);
            }
        }
    }
}
