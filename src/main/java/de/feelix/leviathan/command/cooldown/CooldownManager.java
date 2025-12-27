package de.feelix.leviathan.command.cooldown;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.LazyCleanupProvider;
import de.feelix.leviathan.util.Preconditions;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for commands, supporting both per-user and per-server (global) cooldowns.
 * <p>
 * This class is thread-safe and uses concurrent data structures to handle cooldown tracking
 * across multiple command executions.
 * <p>
 * The manager includes automatic cleanup of expired cooldown entries to prevent memory leaks
 * on long-running servers. Cleanup is performed lazily via {@link #lazyCleanup()} or can be
 * triggered manually via {@link #cleanupExpired()}.
 */
public final class CooldownManager {
    // Cooldown tracking: commandName -> (userId -> lastExecutionTime)
    private static final Map<String, Map<String, Long>> perUserCooldowns = new ConcurrentHashMap<>();
    // Cooldown tracking: commandName -> lastExecutionTime
    private static final Map<String, Long> perServerCooldowns = new ConcurrentHashMap<>();
    // Cooldown durations: commandName -> cooldownMillis (for cleanup reference)
    private static final Map<String, Long> userCooldownDurations = new ConcurrentHashMap<>();
    private static final Map<String, Long> serverCooldownDurations = new ConcurrentHashMap<>();

    // Lazy cleanup using shared utility (interval of 25 for more aggressive cleanup)
    private static final LazyCleanupProvider cleanupProvider = LazyCleanupProvider.withInterval(25);

    // Grace period after cooldown expires before cleanup (30 seconds - reduced from 60 to prevent memory buildup)
    private static final long CLEANUP_GRACE_PERIOD_MS = 30 * 1000L;

    private CooldownManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a per-server cooldown is active for the given command.
     *
     * @param commandName    command name to check
     * @param cooldownMillis cooldown duration in milliseconds
     * @return cooldown check result
     */
    public static @NotNull CooldownResult checkServerCooldown(@NotNull String commandName, long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");

        // Lazy cleanup on access
        lazyCleanup();

        if (cooldownMillis <= 0) {
            return CooldownResult.notOnCooldown();
        }

        // Store duration for cleanup reference
        serverCooldownDurations.put(commandName, cooldownMillis);

        Long lastExecution = perServerCooldowns.get(commandName);
        if (lastExecution == null) {
            return CooldownResult.notOnCooldown();
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastExecution;
        if (elapsed < cooldownMillis) {
            long remaining = cooldownMillis - elapsed;
            return CooldownResult.onCooldown(remaining);
        }

        return CooldownResult.notOnCooldown();
    }

    /**
     * Check if a per-user cooldown is active for the given command and user.
     *
     * @param commandName    command name to check
     * @param userId         user identifier (typically player name)
     * @param cooldownMillis cooldown duration in milliseconds
     * @return cooldown check result
     */
    public static @NotNull CooldownResult checkUserCooldown(@NotNull String commandName, @NotNull String userId,
                                                            long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");

        // Lazy cleanup on access
        lazyCleanup();

        if (cooldownMillis <= 0) {
            return CooldownResult.notOnCooldown();
        }

        // Store duration for cleanup reference
        userCooldownDurations.put(commandName, cooldownMillis);

        Map<String, Long> userCooldowns = perUserCooldowns.get(commandName);
        if (userCooldowns == null) {
            return CooldownResult.notOnCooldown();
        }

        Long lastExecution = userCooldowns.get(userId);
        if (lastExecution == null) {
            return CooldownResult.notOnCooldown();
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastExecution;
        if (elapsed < cooldownMillis) {
            long remaining = cooldownMillis - elapsed;
            return CooldownResult.onCooldown(remaining);
        }

        return CooldownResult.notOnCooldown();
    }

    /**
     * Update the server cooldown timestamp for the given command.
     *
     * @param commandName command name to update
     */
    public static void updateServerCooldown(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        perServerCooldowns.put(commandName, System.currentTimeMillis());
    }

    /**
     * Update the server cooldown timestamp for the given command with duration tracking.
     *
     * @param commandName    command name to update
     * @param cooldownMillis cooldown duration in milliseconds (for cleanup reference)
     */
    public static void updateServerCooldown(@NotNull String commandName, long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");
        perServerCooldowns.put(commandName, System.currentTimeMillis());
        if (cooldownMillis > 0) {
            serverCooldownDurations.put(commandName, cooldownMillis);
        }
    }

    /**
     * Update the user cooldown timestamp for the given command and user.
     *
     * @param commandName command name to update
     * @param userId      user identifier (typically player name)
     */
    public static void updateUserCooldown(@NotNull String commandName, @NotNull String userId) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");
        Map<String, Long> userCooldowns = perUserCooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>());
        userCooldowns.put(userId, System.currentTimeMillis());
    }

    /**
     * Update the user cooldown timestamp for the given command and user with duration tracking.
     *
     * @param commandName    command name to update
     * @param userId         user identifier (typically player name)
     * @param cooldownMillis cooldown duration in milliseconds (for cleanup reference)
     */
    public static void updateUserCooldown(@NotNull String commandName, @NotNull String userId, long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");
        Map<String, Long> userCooldowns = perUserCooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>());
        userCooldowns.put(userId, System.currentTimeMillis());
        if (cooldownMillis > 0) {
            userCooldownDurations.put(commandName, cooldownMillis);
        }
    }

    /**
     * Format a cooldown message with a human-readable time remaining.
     *
     * @param template        message template with %s placeholder for the time
     * @param remainingMillis remaining cooldown time in milliseconds
     * @return formatted message
     */
    public static @NotNull String formatCooldownMessage(@NotNull String template, long remainingMillis) {
        Preconditions.checkNotNull(template, "template");
        double seconds = remainingMillis / 1000.0;
        String timeStr;
        if (seconds < 60) {
            timeStr = String.format("%.1f second%s", seconds, seconds == 1.0 ? "" : "s");
        } else if (seconds < 3600) {
            double minutes = seconds / 60.0;
            timeStr = String.format("%.1f minute%s", minutes, minutes == 1.0 ? "" : "s");
        } else {
            double hours = seconds / 3600.0;
            timeStr = String.format("%.1f hour%s", hours, hours == 1.0 ? "" : "s");
        }
        return String.format(template, timeStr);
    }

    /**
     * Clear all cooldown data for a specific command.
     * Useful for testing or when unloading commands.
     *
     * @param commandName command name to clear cooldowns for
     */
    public static void clearCooldowns(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        perServerCooldowns.remove(commandName);
        perUserCooldowns.remove(commandName);
        serverCooldownDurations.remove(commandName);
        userCooldownDurations.remove(commandName);
    }

    /**
     * Clear all cooldown data for all commands.
     * Useful for plugin reload or testing.
     */
    public static void clearAllCooldowns() {
        perServerCooldowns.clear();
        perUserCooldowns.clear();
        serverCooldownDurations.clear();
        userCooldownDurations.clear();
    }

    /**
     * Perform lazy cleanup if the operation count threshold is reached.
     * This is called automatically on cooldown operations to keep the data clean
     * without requiring background threads.
     */
    private static void lazyCleanup() {
        cleanupProvider.maybeCleanup(CooldownManager::cleanupExpired);
    }

    /**
     * Manually trigger cleanup of expired cooldown entries.
     * This method is automatically called by the cleanup task if started.
     *
     * @return number of entries cleaned up
     */
    public static int cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;

        // Cleanup per-user cooldowns
        for (Map.Entry<String, Map<String, Long>> commandEntry : perUserCooldowns.entrySet()) {
            String commandName = commandEntry.getKey();
            Map<String, Long> userCooldowns = commandEntry.getValue();
            Long duration = userCooldownDurations.get(commandName);

            if (duration == null) {
                // No duration info, use conservative default (1 hour)
                duration = 60 * 60 * 1000L;
            }

            // Add grace period to avoid cleaning up entries too early
            long expirationThreshold = currentTime - duration - CLEANUP_GRACE_PERIOD_MS;

            Iterator<Map.Entry<String, Long>> iterator = userCooldowns.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> userEntry = iterator.next();
                if (userEntry.getValue() < expirationThreshold) {
                    iterator.remove();
                    cleanedCount++;
                }
            }

            // Remove empty command maps
            if (userCooldowns.isEmpty()) {
                perUserCooldowns.remove(commandName);
                userCooldownDurations.remove(commandName);
            }
        }

        // Cleanup per-server cooldowns
        Iterator<Map.Entry<String, Long>> serverIterator = perServerCooldowns.entrySet().iterator();
        while (serverIterator.hasNext()) {
            Map.Entry<String, Long> entry = serverIterator.next();
            String commandName = entry.getKey();
            Long duration = serverCooldownDurations.get(commandName);

            if (duration == null) {
                // No duration info, use conservative default (1 hour)
                duration = 60 * 60 * 1000L;
            }

            long expirationThreshold = currentTime - duration - CLEANUP_GRACE_PERIOD_MS;

            if (entry.getValue() < expirationThreshold) {
                serverIterator.remove();
                serverCooldownDurations.remove(commandName);
                cleanedCount++;
            }
        }

        return cleanedCount;
    }

    /**
     * Get the total number of user cooldown entries currently tracked.
     *
     * @return total user cooldown entry count
     */
    public static int getUserCooldownCount() {
        return perUserCooldowns.values().stream()
            .mapToInt(Map::size)
            .sum();
    }

    /**
     * Get the number of server cooldown entries currently tracked.
     *
     * @return server cooldown entry count
     */
    public static int getServerCooldownCount() {
        return perServerCooldowns.size();
    }

    /**
     * Get the total number of cooldown entries (user + server).
     *
     * @return total cooldown entry count
     */
    public static int getTotalCooldownCount() {
        return getUserCooldownCount() + getServerCooldownCount();
    }

    /**
     * Get the number of commands with active user cooldowns.
     *
     * @return number of commands with user cooldowns
     */
    public static int getTrackedCommandCount() {
        return perUserCooldowns.size() + perServerCooldowns.size();
    }

    /**
     * Get the total number of entries cleaned up since startup.
     *
     * @return total cleaned entries count
     */
    public static long getTotalCleanedEntries() {
        return cleanupProvider.getTotalCleanedEntries();
    }

    /**
     * Get the timestamp of the last cleanup operation.
     *
     * @return last cleanup timestamp in milliseconds, or 0 if never cleaned
     */
    public static long getLastCleanupTime() {
        return cleanupProvider.getLastCleanupTime();
    }

    /**
     * Reset cleanup statistics.
     */
    public static void resetStatistics() {
        cleanupProvider.reset();
    }

    /**
     * Clear a specific user's cooldown for a command.
     *
     * @param commandName command name
     * @param userId      user identifier
     */
    public static void clearUserCooldown(@NotNull String commandName, @NotNull String userId) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");
        Map<String, Long> userCooldowns = perUserCooldowns.get(commandName);
        if (userCooldowns != null) {
            userCooldowns.remove(userId);
        }
    }

    /**
     * Clear the server cooldown for a specific command.
     *
     * @param commandName command name
     */
    public static void clearServerCooldown(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        perServerCooldowns.remove(commandName);
        serverCooldownDurations.remove(commandName);
    }

    /**
     * Get remaining cooldown time for a user on a command.
     *
     * @param commandName    command name
     * @param userId         user identifier
     * @param cooldownMillis cooldown duration
     * @return remaining time in milliseconds, or 0 if not on cooldown
     */
    public static long getRemainingUserCooldown(@NotNull String commandName, @NotNull String userId,
                                                long cooldownMillis) {
        CooldownResult result = checkUserCooldown(commandName, userId, cooldownMillis);
        return result.isOnCooldown() ? result.remainingMillis() : 0L;
    }

    /**
     * Get remaining cooldown time for a server cooldown.
     *
     * @param commandName    command name
     * @param cooldownMillis cooldown duration
     * @return remaining time in milliseconds, or 0 if not on cooldown
     */
    public static long getRemainingServerCooldown(@NotNull String commandName, long cooldownMillis) {
        CooldownResult result = checkServerCooldown(commandName, cooldownMillis);
        return result.isOnCooldown() ? result.remainingMillis() : 0L;
    }

    /**
     * Clear all cooldowns for a specific user across all commands.
     * Useful for cleanup when a player disconnects.
     *
     * @param userId user identifier to clear cooldowns for
     * @return the number of cooldown entries removed
     */
    public static int clearAllCooldownsForUser(@NotNull String userId) {
        Preconditions.checkNotNull(userId, "userId");
        int removed = 0;
        for (Map<String, Long> userCooldowns : perUserCooldowns.values()) {
            if (userCooldowns.remove(userId) != null) {
                removed++;
            }
        }
        return removed;
    }
}
