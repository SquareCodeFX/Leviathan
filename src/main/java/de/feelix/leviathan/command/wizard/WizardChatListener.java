package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit event listener for the wizard system.
 * <p>
 * This listener handles:
 * <ul>
 *   <li>Player chat events during active wizard sessions</li>
 *   <li>Player disconnect to clean up wizard sessions</li>
 * </ul>
 * <p>
 * The listener must be registered with Bukkit's plugin manager for the
 * wizard system to function properly. This is typically done automatically
 * when a wizard command is registered, but can also be done manually:
 * <pre>{@code
 * // Manual registration (optional - done automatically)
 * WizardChatListener.register(plugin);
 * }</pre>
 * <p>
 * Chat messages from players with active wizard sessions are intercepted
 * and routed to the wizard for processing. The chat event is cancelled
 * to prevent the message from appearing to other players.
 *
 * @see WizardManager
 * @see WizardSession
 */
public final class WizardChatListener implements Listener {

    private static volatile boolean registered = false;
    private static final Object REGISTRATION_LOCK = new Object();

    private final JavaPlugin plugin;

    private WizardChatListener(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register the wizard chat listener with the plugin.
     * <p>
     * This method is idempotent - calling it multiple times has no effect
     * after the first successful registration.
     *
     * @param plugin the plugin to register with
     */
    public static void register(@NotNull JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        synchronized (REGISTRATION_LOCK) {
            if (!registered) {
                WizardChatListener listener = new WizardChatListener(plugin);
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registered = true;
            }
        }
    }

    /**
     * Check if the listener is registered.
     *
     * @return true if already registered
     */
    public static boolean isRegistered() {
        return registered;
    }

    /**
     * Handle player chat events.
     * <p>
     * If the player has an active wizard session, the message is routed
     * to the wizard and the chat event is cancelled.
     *
     * @param event the chat event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player has an active wizard session
        if (!WizardManager.hasActiveSession(player)) {
            return;
        }

        // Cancel the chat event so the message doesn't appear to others
        event.setCancelled(true);

        String message = event.getMessage();

        // Handle the input on the main thread for Bukkit API safety
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            WizardManager.handleInput(player, message);
        });
    }

    /**
     * Handle player disconnect events.
     * <p>
     * Cleans up any active wizard session for the disconnecting player.
     *
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up wizard session if player disconnects
        if (WizardManager.hasActiveSession(player)) {
            WizardManager.cancelSession(player);
        }
    }

    /**
     * Reset the registration state.
     * <p>
     * This is primarily used for testing or plugin reload scenarios.
     * After calling this, the next call to {@link #register(JavaPlugin)}
     * will register a new listener.
     */
    public static void resetRegistration() {
        synchronized (REGISTRATION_LOCK) {
            registered = false;
        }
    }
}
