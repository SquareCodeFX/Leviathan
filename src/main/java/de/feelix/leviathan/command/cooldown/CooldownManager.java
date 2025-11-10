package de.feelix.leviathan.command.cooldown;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages cooldowns for commands, supporting both per-user and per-server (global) cooldowns.
 * <p>
 * This class is thread-safe and uses concurrent data structures to handle cooldown tracking
 * across multiple command executions.
 */
public final class CooldownManager {
    // Cooldown tracking: commandName -> (userId -> lastExecutionTime)
    private static final Map<String, Map<String, Long>> perUserCooldowns = new ConcurrentHashMap<>();
    // Cooldown tracking: commandName -> lastExecutionTime
    private static final Map<String, Long> perServerCooldowns = new ConcurrentHashMap<>();

    private CooldownManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a per-server cooldown is active for the given command.
     *
     * @param commandName command name to check
     * @param cooldownMillis cooldown duration in milliseconds
     * @return cooldown check result
     */
    public static @NotNull CooldownResult checkServerCooldown(@NotNull String commandName, long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");
        if (cooldownMillis <= 0) {
            return CooldownResult.notOnCooldown();
        }

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
     * @param commandName command name to check
     * @param userId user identifier (typically player name)
     * @param cooldownMillis cooldown duration in milliseconds
     * @return cooldown check result
     */
    public static @NotNull CooldownResult checkUserCooldown(@NotNull String commandName, @NotNull String userId, long cooldownMillis) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");
        if (cooldownMillis <= 0) {
            return CooldownResult.notOnCooldown();
        }

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
     * Update the user cooldown timestamp for the given command and user.
     *
     * @param commandName command name to update
     * @param userId user identifier (typically player name)
     */
    public static void updateUserCooldown(@NotNull String commandName, @NotNull String userId) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(userId, "userId");
        Map<String, Long> userCooldowns = perUserCooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>());
        userCooldowns.put(userId, System.currentTimeMillis());
    }

    /**
     * Format a cooldown message with a human-readable time remaining.
     *
     * @param template message template with %s placeholder for the time
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
    }

    /**
     * Clear all cooldown data for all commands.
     * Useful for plugin reload or testing.
     */
    public static void clearAllCooldowns() {
        perServerCooldowns.clear();
        perUserCooldowns.clear();
    }
}
