package de.feelix.leviathan.command.guard;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

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
    
    /**
     * Creates a gamemode-based guard that checks if the sender (must be a player) is in the specified game mode.
     *
     * @param gameMode the game mode to check
     * @return a guard that tests for the game mode
     */
    static @NotNull Guard inGameMode(@NotNull GameMode gameMode) {
        Preconditions.checkNotNull(gameMode, "gameMode");
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.getGameMode() == gameMode;
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou must be in " + gameMode.name() + " mode."; 
            }
        };
    }
    
    /**
     * Creates an operator-status guard that checks if the sender is an operator.
     *
     * @return a guard that tests for operator status
     */
    static @NotNull Guard opOnly() {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                return sender.isOp();
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cThis command is only available to operators."; 
            }
        };
    }
    
    /**
     * Creates a level-range guard that checks if the player's experience level is within the specified range.
     *
     * @param minLevel minimum level (inclusive)
     * @param maxLevel maximum level (inclusive)
     * @return a guard that tests for level range
     */
    static @NotNull Guard levelRange(int minLevel, int maxLevel) {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                int level = p.getLevel();
                return level >= minLevel && level <= maxLevel;
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou must be between level " + minLevel + " and " + maxLevel + " to use this command."; 
            }
        };
    }
    
    /**
     * Creates a minimum-level guard that checks if the player's experience level meets the requirement.
     *
     * @param minLevel minimum level required (inclusive)
     * @return a guard that tests for minimum level
     */
    static @NotNull Guard minLevel(int minLevel) {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.getLevel() >= minLevel;
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou must be at least level " + minLevel + " to use this command."; 
            }
        };
    }
    
    /**
     * Creates a health-threshold guard that checks if the player's health is above the specified value.
     *
     * @param minHealth minimum health required (exclusive)
     * @return a guard that tests for health above threshold
     */
    static @NotNull Guard healthAbove(double minHealth) {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.getHealth() > minHealth;
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou need more than " + minHealth + " health to use this command."; 
            }
        };
    }
    
    /**
     * Creates a food-level guard that checks if the player's food level is above the specified value.
     *
     * @param minFoodLevel minimum food level required (exclusive)
     * @return a guard that tests for food level above threshold
     */
    static @NotNull Guard foodLevelAbove(int minFoodLevel) {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.getFoodLevel() > minFoodLevel;
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou need more than " + minFoodLevel + " food level to use this command."; 
            }
        };
    }
    
    /**
     * Creates a flying guard that checks if the player is currently flying.
     *
     * @return a guard that tests if player is flying
     */
    static @NotNull Guard isFlying() {
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player p)) return false;
                return p.isFlying();
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return "§cYou must be flying to use this command."; 
            }
        };
    }
    
    /**
     * Creates a custom guard based on a predicate function.
     * Useful for dynamic or complex conditions.
     *
     * @param predicate the predicate to test
     * @param errorMessage the error message to display when the guard fails
     * @return a guard that tests the predicate
     */
    static @NotNull Guard custom(@NotNull Predicate<CommandSender> predicate, @NotNull String errorMessage) {
        Preconditions.checkNotNull(predicate, "predicate");
        Preconditions.checkNotNull(errorMessage, "errorMessage");
        return new Guard() {
            @Override 
            public boolean test(@NotNull CommandSender sender) {
                return predicate.test(sender);
            }
            
            @Override 
            public @NotNull String errorMessage() { 
                return errorMessage; 
            }
        };
    }
}
