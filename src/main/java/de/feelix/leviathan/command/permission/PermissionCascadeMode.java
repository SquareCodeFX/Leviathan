package de.feelix.leviathan.command.permission;

/**
 * Defines how permissions are inherited from parent commands to subcommands.
 * <p>
 * Permission cascading allows parent command permissions to be automatically
 * checked when executing subcommands, providing hierarchical permission control.
 * <p>
 * Example hierarchy:
 * <pre>
 * /admin              (permission: "admin.use")
 *   /admin user       (permission: "admin.user")
 *     /admin user ban (permission: "admin.user.ban")
 * </pre>
 * <p>
 * With different cascade modes:
 * <ul>
 *   <li>{@link #NONE}: Only checks "admin.user.ban"</li>
 *   <li>{@link #INHERIT}: Checks "admin.use" AND "admin.user" AND "admin.user.ban"</li>
 *   <li>{@link #AUTO_PREFIX}: Auto-generates permission from command path</li>
 *   <li>{@link #WILDCARD}: Parent permission implies child (e.g., "admin.*" grants all)</li>
 * </ul>
 */
public enum PermissionCascadeMode {

    /**
     * No permission cascading.
     * <p>
     * Only the subcommand's own permission is checked.
     * Parent command permissions are ignored.
     * <p>
     * Use when subcommands should have completely independent permissions.
     */
    NONE,

    /**
     * Full permission inheritance (default).
     * <p>
     * A sender must have permission for all commands in the hierarchy:
     * parent permission AND this command's permission.
     * <p>
     * This is the most restrictive mode and ensures that users cannot
     * bypass parent command permissions by directly accessing subcommands.
     * <p>
     * Example: To use "/admin user ban", player needs:
     * - "admin.use" (parent)
     * - "admin.user" (sub-parent)
     * - "admin.user.ban" (this command)
     */
    INHERIT,

    /**
     * Automatic permission generation based on command path.
     * <p>
     * If no explicit permission is set, generates one automatically
     * from the command hierarchy using a prefix and dot-notation.
     * <p>
     * Example: "/admin user ban" with prefix "myplugin" generates:
     * - "myplugin.admin.user.ban"
     * <p>
     * Combined with inheritance, this provides a consistent permission
     * structure without manually defining each permission.
     */
    AUTO_PREFIX,

    /**
     * Wildcard permission support.
     * <p>
     * In addition to checking explicit permissions, also checks
     * wildcard permissions at each level of the hierarchy.
     * <p>
     * Example: "/admin user ban" checks:
     * - "admin.*" (would grant access to all admin subcommands)
     * - "admin.user.*" (would grant access to all user subcommands)
     * - "admin.user.ban" (explicit permission)
     * <p>
     * This allows administrators to grant broad permissions using wildcards
     * while still supporting fine-grained permissions.
     * <p>
     * Note: Requires the permission plugin to support wildcard permissions.
     */
    WILDCARD,

    /**
     * Permission inheritance with fallback.
     * <p>
     * If the subcommand has no permission set, it inherits the parent's
     * permission instead of being accessible to everyone.
     * <p>
     * This is useful when you want some subcommands to share permissions
     * with their parent while others have explicit permissions.
     * <p>
     * Example:
     * - "/admin" has permission "admin.use"
     * - "/admin help" has no permission -> inherits "admin.use"
     * - "/admin ban" has permission "admin.ban" -> uses "admin.ban"
     */
    INHERIT_FALLBACK;

    /**
     * @return true if this mode checks parent permissions
     */
    public boolean checksParentPermissions() {
        return this == INHERIT || this == AUTO_PREFIX || this == WILDCARD || this == INHERIT_FALLBACK;
    }

    /**
     * @return true if this mode generates automatic permissions
     */
    public boolean generatesAutoPermissions() {
        return this == AUTO_PREFIX;
    }

    /**
     * @return true if this mode supports wildcard permissions
     */
    public boolean supportsWildcards() {
        return this == WILDCARD;
    }
}
