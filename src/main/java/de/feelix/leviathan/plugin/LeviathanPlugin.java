package de.feelix.leviathan.plugin;

import de.feelix.leviathan.command.FluentCommand;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Example plugin demonstrating how to register commands using the FluentCommand API.
 */
public final class LeviathanPlugin extends JavaPlugin {

    /**
     * Called by Bukkit when the plugin is enabled. Registers example commands.
     */
    @Override
    public void onEnable() {
        registerGamemode();
        registerKick();

        getLogger().info("Leviathan command API enabled.");
    }

    /**
     * Register a /gamemode command showcasing choice and oneOf parsers with an optional target.
     */
    private void registerGamemode() {
        Map<String, GameMode> gm = new HashMap<>();
        gm.put("0", GameMode.SURVIVAL);
        gm.put("survival", GameMode.SURVIVAL);
        gm.put("1", GameMode.CREATIVE);
        gm.put("creative", GameMode.CREATIVE);
        gm.put("2", GameMode.ADVENTURE);
        gm.put("adventure", GameMode.ADVENTURE);
        gm.put("3", GameMode.SPECTATOR);
        gm.put("spectator", GameMode.SPECTATOR);

        FluentCommand.builder("gamemode")
            .permission("leviathan.gamemode")
            .description("Change your or another player's game mode")
            .argChoices("mode", gm, "gamemode")
            .argUUID("target").optional()
            .executes((sender, ctx) -> {
                GameMode mode = ctx.get("mode", GameMode.class);

                // Resolve target
                Player targetPlayer = null;
                if (ctx.has("target")) {
                    UUID id = ctx.get("target", UUID.class);
                    if (id != null) {
                        targetPlayer = Bukkit.getPlayer(id);
                    }
                    if (targetPlayer == null) {
                        sender.sendMessage("§cTarget player is not online.");
                        return;
                    }
                } else {
                    if (sender instanceof Player p) {
                        targetPlayer = p;
                    } else {
                        sender.sendMessage("§cYou must specify a player when using this command from console.");
                        return;
                    }
                }

                targetPlayer.setGameMode(mode);
                if (!targetPlayer.equals(sender)) {
                    sender.sendMessage(
                        "§aSet " + targetPlayer.getName() + "'s gamemode to " + mode.name().toLowerCase() + ".");
                }
                targetPlayer.sendMessage("§aYour gamemode has been set to " + mode.name().toLowerCase() + ".");
            })
            .register(this);
    }

    /**
     * Register a /kick command showcasing oneOf and optional trailing arguments.
     */
    private void registerKick() {
        FluentCommand.builder("kick")
            .permission("leviathan.kick")
            .description("Kick a player with an optional reason")
            .argUUID("target")
            .argString("reason").optional()
            .executes((sender, ctx) -> {
                // Resolve target
                Player targetPlayer = null;
                Object target = ctx.get("target", Object.class);
                if (target instanceof OfflinePlayer op) {
                    targetPlayer = op.getPlayer();
                } else if (target instanceof UUID id) {
                    targetPlayer = Bukkit.getPlayer(id);
                }
                if (targetPlayer == null) {
                    sender.sendMessage("§cTarget player is not online.");
                    return;
                }

                String reason = ctx.getOptional("reason", String.class).orElse("Kicked by an operator.");
                targetPlayer.kickPlayer(reason);
                sender.sendMessage("§aKicked " + targetPlayer.getName() + " §7(Reason: " + reason + ")");
            })
            .register(this);
    }
}
