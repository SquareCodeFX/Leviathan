package de.feelix.leviathan.command.permission;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for handling permission cascading in command hierarchies.
 * <p>
 * This class provides methods to check, compute, and generate permissions
 * based on the configured {@link PermissionCascadeMode}.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Check if sender has permission for a command hierarchy
 * boolean allowed = PermissionCascade.hasPermission(
 *     sender,
 *     command,
 *     PermissionCascadeMode.INHERIT
 * );
 *
 * // Generate automatic permission from command path
 * String perm = PermissionCascade.generateAutoPermission(
 *     "myplugin",
 *     List.of("admin", "user", "ban")
 * );
 * // Result: "myplugin.admin.user.ban"
 * }</pre>
 */
public final class PermissionCascade {

    private PermissionCascade() {
        // Utility class
    }

    /**
     * Check if a sender has permission based on the cascade mode.
     *
     * @param sender     the command sender
     * @param permission the command's own permission (may be null)
     * @param parent     the parent permission checker (may be null)
     * @param mode       the cascade mode
     * @return true if the sender has permission
     */
    public static boolean hasPermission(
            @NotNull CommandSender sender,
            @Nullable String permission,
            @Nullable ParentPermissionChecker parent,
            @NotNull PermissionCascadeMode mode) {

        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(mode, "mode");

        switch (mode) {
            case NONE:
                // Only check own permission
                return permission == null || sender.hasPermission(permission);

            case INHERIT:
                // Check parent first, then own
                if (parent != null && !parent.hasPermission(sender)) {
                    return false;
                }
                return permission == null || sender.hasPermission(permission);

            case INHERIT_FALLBACK:
                // If no permission, inherit from parent
                if (permission == null) {
                    return parent == null || parent.hasPermission(sender);
                }
                // Has own permission - check parent chain plus own
                if (parent != null && !parent.hasPermission(sender)) {
                    return false;
                }
                return sender.hasPermission(permission);

            case WILDCARD:
                // Check parent with wildcards, then own
                if (parent != null && !parent.hasPermissionWithWildcard(sender)) {
                    return false;
                }
                if (permission == null) {
                    return true;
                }
                // Check explicit permission or wildcard
                return sender.hasPermission(permission) ||
                       sender.hasPermission(getWildcardPermission(permission));

            case AUTO_PREFIX:
                // Same as INHERIT for checking (generation happens at build time)
                if (parent != null && !parent.hasPermission(sender)) {
                    return false;
                }
                return permission == null || sender.hasPermission(permission);

            default:
                return permission == null || sender.hasPermission(permission);
        }
    }

    /**
     * Generate an automatic permission string from a command path.
     *
     * @param prefix      the permission prefix (e.g., plugin name)
     * @param commandPath the command path segments (e.g., ["admin", "user", "ban"])
     * @return the generated permission (e.g., "prefix.admin.user.ban")
     */
    public static @NotNull String generateAutoPermission(@NotNull String prefix, @NotNull List<String> commandPath) {
        Preconditions.checkNotNull(prefix, "prefix");
        Preconditions.checkNotNull(commandPath, "commandPath");

        if (commandPath.isEmpty()) {
            return prefix;
        }

        StringBuilder sb = new StringBuilder(prefix);
        for (String segment : commandPath) {
            sb.append('.').append(segment.toLowerCase());
        }
        return sb.toString();
    }

    /**
     * Generate an automatic permission string from a parent permission and command name.
     *
     * @param parentPermission the parent's permission (may be null)
     * @param commandName      the command name
     * @return the generated permission
     */
    public static @NotNull String generateChildPermission(@Nullable String parentPermission,
                                                          @NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");

        if (parentPermission == null || parentPermission.isEmpty()) {
            return commandName.toLowerCase();
        }

        // Remove wildcard suffix if present
        String base = parentPermission;
        if (base.endsWith(".*")) {
            base = base.substring(0, base.length() - 2);
        }

        return base + "." + commandName.toLowerCase();
    }

    /**
     * Get the wildcard version of a permission.
     * <p>
     * Example: "admin.user.ban" → "admin.user.*"
     *
     * @param permission the explicit permission
     * @return the wildcard permission
     */
    public static @NotNull String getWildcardPermission(@NotNull String permission) {
        Preconditions.checkNotNull(permission, "permission");

        int lastDot = permission.lastIndexOf('.');
        if (lastDot <= 0) {
            return "*";
        }
        return permission.substring(0, lastDot) + ".*";
    }

    /**
     * Get all wildcard permissions that could grant access to a permission.
     * <p>
     * Example: "admin.user.ban" → ["*", "admin.*", "admin.user.*"]
     *
     * @param permission the explicit permission
     * @return list of wildcard permissions from most general to most specific
     */
    public static @NotNull List<String> getAllWildcardPermissions(@NotNull String permission) {
        Preconditions.checkNotNull(permission, "permission");

        List<String> wildcards = new ArrayList<>();
        wildcards.add("*"); // Global wildcard

        String[] parts = permission.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts[i]);
            wildcards.add(sb + ".*");
        }

        return Collections.unmodifiableList(wildcards);
    }

    /**
     * Check if any wildcard permission would grant access.
     *
     * @param sender     the command sender
     * @param permission the explicit permission
     * @return true if any wildcard grants access
     */
    public static boolean hasWildcardPermission(@NotNull CommandSender sender, @NotNull String permission) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(permission, "permission");

        for (String wildcard : getAllWildcardPermissions(permission)) {
            if (sender.hasPermission(wildcard)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build a permission hierarchy description for help/debug output.
     *
     * @param permissions list of permissions from root to leaf
     * @return formatted hierarchy string
     */
    public static @NotNull String formatPermissionHierarchy(@NotNull List<String> permissions) {
        Preconditions.checkNotNull(permissions, "permissions");

        if (permissions.isEmpty()) {
            return "(no permissions required)";
        }
        if (permissions.size() == 1) {
            return permissions.get(0);
        }
        return String.join(" → ", permissions);
    }

    /**
     * Functional interface for checking parent permissions.
     */
    @FunctionalInterface
    public interface ParentPermissionChecker {
        /**
         * Check if the sender has the parent's permission.
         *
         * @param sender the command sender
         * @return true if allowed
         */
        boolean hasPermission(@NotNull CommandSender sender);

        /**
         * Check if the sender has the parent's permission including wildcards.
         * Default implementation delegates to {@link #hasPermission(CommandSender)}.
         *
         * @param sender the command sender
         * @return true if allowed
         */
        default boolean hasPermissionWithWildcard(@NotNull CommandSender sender) {
            return hasPermission(sender);
        }
    }
}
