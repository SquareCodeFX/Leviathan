package de.feelix.leviathan.inventory.prompt;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.inventory.InventoryManager;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Sign-like text prompt. Uses chat as a portable fallback across Spigot versions.
 */
public final class SignPrompt {
    private SignPrompt() {}

    /**
     * Open a chat-based sign prompt for the given player.
     * The player is asked to type their input in chat. The prompt times out after the specified ticks.
     *
     * @param player       the target player (must not be null)
     * @param title        optional title/message shown to the player before input
     * @param onComplete   callback invoked with the player's chat message
     * @param onCancel     optional callback invoked if the player quits or the prompt times out
     * @param timeoutTicks timeout in server ticks (20 ticks = 1 second)
     */
    public static void open(@NotNull Player player,
                            @Nullable String title,
                            @NotNull Consumer<String> onComplete,
                            @Nullable Runnable onCancel,
                            long timeoutTicks) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(onComplete, "onComplete");
        Plugin plugin = InventoryManager.get().plugin();

        if (title != null && !title.isBlank()) {
            player.sendMessage(title);
        }
        player.sendMessage("Please type your input in chat. This will time out in " + Math.max(1, timeoutTicks/20) + "s.");

        class State implements Listener {
            BukkitTask timeout;
            void finish() {
                if (timeout != null) timeout.cancel();
                AsyncPlayerChatEvent.getHandlerList().unregister(this);
                PlayerQuitEvent.getHandlerList().unregister(this);
            }

            @EventHandler
            public void onChat(AsyncPlayerChatEvent e) {
                if (!e.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                e.setCancelled(true);
                String msg = e.getMessage();
                finish();
                Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(msg));
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                if (!e.getPlayer().getUniqueId().equals(player.getUniqueId())) return;
                finish();
                if (onCancel != null) Bukkit.getScheduler().runTask(plugin, onCancel);
            }
        }

        State state = new State();
        Bukkit.getPluginManager().registerEvents(state, plugin);
        state.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state.finish();
            if (onCancel != null) onCancel.run();
        }, Math.max(1L, timeoutTicks));
    }
}
