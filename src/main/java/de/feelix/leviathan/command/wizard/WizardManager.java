package de.feelix.leviathan.command.wizard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages active wizard sessions.
 * <p>
 * Features:
 * <ul>
 *   <li>Session lifecycle management (create, track, cleanup)</li>
 *   <li>Input routing to active sessions</li>
 *   <li>Automatic timeout handling</li>
 *   <li>Player disconnect cleanup</li>
 * </ul>
 */
public final class WizardManager {

    // Active sessions: player UUID -> session
    private static final Map<UUID, WizardSession> activeSessions = new ConcurrentHashMap<>();

    // Lazy cleanup tracking
    private static final AtomicLong operationCount = new AtomicLong(0);
    private static final int CLEANUP_INTERVAL_OPS = 15;

    private WizardManager() {
        throw new AssertionError("Utility class");
    }

    /**
     * Perform lazy cleanup of expired/inactive sessions.
     */
    private static void lazyCleanup() {
        long ops = operationCount.incrementAndGet();
        if (ops % CLEANUP_INTERVAL_OPS == 0) {
            cleanupExpiredSessions();
        }
    }

    /**
     * Clean up all expired or inactive sessions.
     *
     * @return the number of sessions cleaned up
     */
    public static int cleanupExpiredSessions() {
        int cleaned = 0;
        Iterator<Map.Entry<UUID, WizardSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, WizardSession> entry = iterator.next();
            WizardSession session = entry.getValue();
            if (!session.isActive() || session.isTimedOut()) {
                if (session.isTimedOut() && session.isActive()) {
                    session.timeout();
                }
                iterator.remove();
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * Get the number of active sessions.
     *
     * @return the count of active sessions
     */
    public static int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Clear all sessions. Use with caution - primarily for plugin shutdown.
     */
    public static void clearAllSessions() {
        for (WizardSession session : activeSessions.values()) {
            if (session.isActive()) {
                session.cancel();
            }
        }
        activeSessions.clear();
    }

    // ==================== Session Management ====================

    /**
     * Start a new wizard session for a player.
     *
     * @param plugin     the plugin instance
     * @param player     the player
     * @param definition the wizard definition
     * @return the created session
     */
    public static @NotNull WizardSession startSession(
            @NotNull JavaPlugin plugin,
            @NotNull Player player,
            @NotNull WizardDefinition definition) {
        return startSession(plugin, player, definition, null);
    }

    /**
     * Start a new wizard session for a player with command context.
     *
     * @param plugin         the plugin instance
     * @param player         the player
     * @param definition     the wizard definition
     * @param commandContext the original command context
     * @return the created session
     */
    public static @NotNull WizardSession startSession(
            @NotNull JavaPlugin plugin,
            @NotNull Player player,
            @NotNull WizardDefinition definition,
            @Nullable CommandContext commandContext) {

        Preconditions.checkNotNull(plugin, "plugin");
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(definition, "definition");

        // Lazy cleanup
        lazyCleanup();

        // Cancel any existing session
        cancelSession(player);

        // Create and start new session
        WizardSession session = WizardSession.start(plugin, player, definition, commandContext);
        activeSessions.put(player.getUniqueId(), session);

        // Schedule timeout check
        if (definition.timeoutMillis() > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                WizardSession s = activeSessions.get(player.getUniqueId());
                if (s != null && s.sessionId().equals(session.sessionId()) && s.isTimedOut()) {
                    s.timeout();
                    activeSessions.remove(player.getUniqueId());
                }
            }, definition.timeoutMillis() / 50); // Convert to ticks
        }

        return session;
    }

    /**
     * Check if a player has an active wizard session.
     *
     * @param player the player to check
     * @return true if player has an active session
     */
    public static boolean hasActiveSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        lazyCleanup();
        WizardSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Get the active session for a player.
     *
     * @param player the player
     * @return the active session, or null if none
     */
    public static @Nullable WizardSession getSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        lazyCleanup();
        WizardSession session = activeSessions.get(player.getUniqueId());
        if (session != null && !session.isActive()) {
            activeSessions.remove(player.getUniqueId());
            return null;
        }
        return session;
    }

    /**
     * Handle chat input for an active session.
     *
     * @param player  the player who sent the message
     * @param message the chat message
     * @return true if the message was handled by an active session
     */
    public static boolean handleInput(@NotNull Player player, @NotNull String message) {
        Preconditions.checkNotNull(player, "player");
        Preconditions.checkNotNull(message, "message");

        lazyCleanup();

        WizardSession session = activeSessions.get(player.getUniqueId());
        if (session == null || !session.isActive()) {
            if (session != null) {
                activeSessions.remove(player.getUniqueId());
            }
            return false;
        }

        // Check timeout
        if (session.isTimedOut()) {
            session.timeout();
            activeSessions.remove(player.getUniqueId());
            return true;
        }

        boolean handled = session.handleInput(message);

        // Clean up if session is no longer active
        if (!session.isActive()) {
            activeSessions.remove(player.getUniqueId());
        }

        return handled;
    }

    /**
     * Cancel a player's active session.
     *
     * @param player the player
     * @return true if a session was cancelled
     */
    public static boolean cancelSession(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        WizardSession session = activeSessions.remove(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.cancel();
            return true;
        }
        return false;
    }

    /**
     * Clean up all sessions for a disconnected player.
     *
     * @param player the player who disconnected
     */
    public static void cleanupPlayer(@NotNull Player player) {
        Preconditions.checkNotNull(player, "player");
        WizardSession session = activeSessions.remove(player.getUniqueId());
        if (session != null && session.isActive()) {
            // Don't send messages to disconnected player
            // Just clean up the session
        }
    }

    /**
     * Clean up all sessions for a disconnected player by UUID.
     *
     * @param playerUuid the UUID of the player who disconnected
     */
    public static void cleanupPlayer(@NotNull UUID playerUuid) {
        Preconditions.checkNotNull(playerUuid, "playerUuid");
        activeSessions.remove(playerUuid);
    }

    // ==================== Utility Methods ====================

    /**
     * Get all active sessions.
     *
     * @return unmodifiable map of player UUID to session
     */
    public static @NotNull Map<UUID, WizardSession> getAllSessions() {
        return java.util.Collections.unmodifiableMap(activeSessions);
    }

    /**
     * Check if any player has an active session with the given wizard.
     *
     * @param wizardName the wizard name
     * @return true if any player is using this wizard
     */
    public static boolean isWizardInUse(@NotNull String wizardName) {
        Preconditions.checkNotNull(wizardName, "wizardName");
        for (WizardSession session : activeSessions.values()) {
            if (session.isActive() && session.definition().name().equals(wizardName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the number of players using a specific wizard.
     *
     * @param wizardName the wizard name
     * @return the count of active sessions for this wizard
     */
    public static int getWizardUserCount(@NotNull String wizardName) {
        Preconditions.checkNotNull(wizardName, "wizardName");
        int count = 0;
        for (WizardSession session : activeSessions.values()) {
            if (session.isActive() && session.definition().name().equals(wizardName)) {
                count++;
            }
        }
        return count;
    }
}
