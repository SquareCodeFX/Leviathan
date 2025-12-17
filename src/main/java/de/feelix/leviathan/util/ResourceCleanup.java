package de.feelix.leviathan.util;

import de.feelix.leviathan.command.cooldown.CooldownManager;
import de.feelix.leviathan.command.core.SlashCommand;
import de.feelix.leviathan.command.interactive.InteractivePrompt;

/**
 * Utility class for cleaning up all static resources in the SlashCommand framework.
 * <p>
 * This class provides methods for cleaning up resources that may accumulate over time
 * due to memory leaks or normal operation. Plugin authors should call these methods
 * during plugin disable to ensure proper cleanup.
 * <p>
 * Example usage in plugin disable:
 * <pre>{@code
 * @Override
 * public void onDisable() {
 *     ResourceCleanup.cleanupAll();
 * }
 * }</pre>
 *
 * For player disconnect events, use:
 * <pre>{@code
 * @EventHandler
 * public void onPlayerQuit(PlayerQuitEvent event) {
 *     ResourceCleanup.cleanupForPlayer(event.getPlayer());
 * }
 * }</pre>
 */
public final class ResourceCleanup {

    private ResourceCleanup() {
        throw new AssertionError("Utility class");
    }

    /**
     * Clean up all static resources across the SlashCommand framework.
     * This should be called during plugin disable to prevent memory leaks.
     * <p>
     * This method clears:
     * <ul>
     *   <li>All interactive prompt sessions</li>
     *   <li>All pending confirmations</li>
     *   <li>All cooldown data</li>
     * </ul>
     */
    public static void cleanupAll() {
        InteractivePrompt.clearAllSessions();
        SlashCommand.clearAllConfirmations();
        CooldownManager.clearAllCooldowns();
    }

    /**
     * Clean up resources associated with a specific player.
     * This should be called when a player disconnects to prevent memory leaks.
     *
     * @param player the player to clean up resources for
     */
    public static void cleanupForPlayer(org.bukkit.entity.Player player) {
        Preconditions.checkNotNull(player, "player");
        cleanupForPlayer(player.getName(), player.getUniqueId());
    }

    /**
     * Clean up resources associated with a specific player by name and UUID.
     * This is useful when the Player object is no longer available.
     *
     * @param playerName the name of the player
     * @param playerUuid the UUID of the player
     */
    public static void cleanupForPlayer(String playerName, java.util.UUID playerUuid) {
        Preconditions.checkNotNull(playerName, "playerName");
        Preconditions.checkNotNull(playerUuid, "playerUuid");

        // Clean up interactive sessions
        InteractivePrompt.cleanupPlayer(playerUuid);

        // Clean up pending confirmations
        SlashCommand.clearConfirmationsForSender(playerName);

        // Clean up cooldowns for this user
        CooldownManager.clearAllCooldownsForUser(playerName);
    }

    /**
     * Clean up interactive session for a player by UUID.
     * Delegates to {@link InteractivePrompt#cleanupPlayer(java.util.UUID)}.
     *
     * @param playerUuid the UUID of the player
     */
    public static void cleanupInteractiveSession(java.util.UUID playerUuid) {
        Preconditions.checkNotNull(playerUuid, "playerUuid");
        InteractivePrompt.cleanupPlayer(playerUuid);
    }

    /**
     * Trigger cleanup of expired resources without clearing active ones.
     * This is less aggressive than {@link #cleanupAll()} and can be called
     * periodically to prevent memory buildup without interrupting active sessions.
     *
     * @return a summary of the cleanup operation
     */
    public static CleanupResult cleanupExpired() {
        int sessions = InteractivePrompt.cleanupExpiredSessions();
        int confirmations = SlashCommand.cleanupExpiredConfirmations();
        int cooldowns = CooldownManager.cleanupExpired();
        return new CleanupResult(sessions, confirmations, cooldowns);
    }

    /**
     * Get diagnostic information about current resource usage.
     *
     * @return diagnostic information about active resources
     */
    public static ResourceStats getResourceStats() {
        return new ResourceStats(
            InteractivePrompt.getActiveSessionCount(),
            SlashCommand.getPendingConfirmationCount(),
            CooldownManager.getTotalCooldownCount()
        );
    }

    /**
     * Result of a cleanup operation.
     */
    public static final class CleanupResult {
        private final int sessionsCleared;
        private final int confirmationsCleared;
        private final int cooldownsCleared;

        private CleanupResult(int sessionsCleared, int confirmationsCleared, int cooldownsCleared) {
            this.sessionsCleared = sessionsCleared;
            this.confirmationsCleared = confirmationsCleared;
            this.cooldownsCleared = cooldownsCleared;
        }

        public int getSessionsCleared() { return sessionsCleared; }
        public int getConfirmationsCleared() { return confirmationsCleared; }
        public int getCooldownsCleared() { return cooldownsCleared; }
        public int getTotalCleared() { return sessionsCleared + confirmationsCleared + cooldownsCleared; }

        @Override
        public String toString() {
            return "CleanupResult{sessions=" + sessionsCleared +
                   ", confirmations=" + confirmationsCleared +
                   ", cooldowns=" + cooldownsCleared + "}";
        }
    }

    /**
     * Statistics about current resource usage.
     */
    public static final class ResourceStats {
        private final int activeSessions;
        private final int pendingConfirmations;
        private final int cooldownEntries;

        private ResourceStats(int activeSessions, int pendingConfirmations, int cooldownEntries) {
            this.activeSessions = activeSessions;
            this.pendingConfirmations = pendingConfirmations;
            this.cooldownEntries = cooldownEntries;
        }

        public int getActiveSessions() { return activeSessions; }
        public int getPendingConfirmations() { return pendingConfirmations; }
        public int getCooldownEntries() { return cooldownEntries; }
        public int getTotalEntries() { return activeSessions + pendingConfirmations + cooldownEntries; }

        @Override
        public String toString() {
            return "ResourceStats{sessions=" + activeSessions +
                   ", confirmations=" + pendingConfirmations +
                   ", cooldowns=" + cooldownEntries + "}";
        }
    }
}
