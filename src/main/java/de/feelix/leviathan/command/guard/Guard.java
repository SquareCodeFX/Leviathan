package de.feelix.leviathan.command.guard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Declarative guard that must pass before command execution and tab completion.
 * <p>
 * Guards provide a flexible way to restrict command access beyond simple permission checks,
 * such as requiring the sender to be in a specific world, have certain game mode, etc.
 */
public interface Guard {
    /**
     * Evaluates whether the given sender is allowed to proceed (for execution and tab-complete).
     *
     * @param sender the command sender
     * @return true if permitted; false to block
     */
    boolean test(@NotNull CommandSender sender);
    
    /**
     * Optional human-readable error message sent to the sender when {@link #test(CommandSender)} returns false.
     *
     * @return non-null message string
     */
    default @NotNull String errorMessage() { 
        return "§cYou cannot use this command."; 
    }
    
    /**
     * Creates a permission-based guard that checks if the sender has the specified permission.
     *
     * @param perm the permission node to check
     * @return a guard that tests for the permission
     */
    static @NotNull Guard permission(@NotNull String perm) {
        Preconditions.checkNotNull(perm, "perm");
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) { 
                return sender.hasPermission(perm); 
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou lack permission: " + perm; 
            }
        };
    }
    
    /**
     * Creates a world-based guard that checks if the sender (must be a player) is in the specified world.
     *
     * @param worldName the name of the world to check
     * @return a guard that tests for the world
     */
    static @NotNull Guard inWorld(@NotNull String worldName) {
        Preconditions.checkNotNull(worldName, "worldName");
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.getWorld().getName().equalsIgnoreCase(worldName);
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou must be in world '" + worldName + "'."; 
            }
        };
    }
}
