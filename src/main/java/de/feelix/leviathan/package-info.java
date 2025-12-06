/**
 * Root package for the Leviathan command framework.
 * <p>
 * Leviathan is a modern, fluent command framework for Spigot/Bukkit plugins that provides:
 * <ul>
 *   <li>Type-safe argument parsing with built-in validation</li>
 *   <li>Fluent builder API for command construction</li>
 *   <li>Advanced tab completion with dynamic providers</li>
 *   <li>Guard predicates and permission checks</li>
 *   <li>Cooldown management (per-user and per-server)</li>
 *   <li>Async command execution with timeout support</li>
 *   <li>Customizable error messages and localization</li>
 *   <li>Subcommand routing and hierarchical commands</li>
 * </ul>
 * 
 * <h3>Quick Start</h3>
 * <pre>{@code
 * import de.feelix.leviathan.Leviathan;
 * 
 * // Simple command
 * Leviathan.command("greet")
 *     .description("Greets a player")
 *     .argPlayer("target")
 *     .executes((sender, ctx) -> {
 *         Player target = ctx.get("target", Player.class);
 *         sender.sendMessage("Hello, " + target.getName() + "!");
 *     })
 *     .register(plugin);
 * 
 * // Advanced command with validation and completions
 * Leviathan.command("give")
 *     .description("Give items to a player")
 *     .argPlayer("target")
 *     .argMaterial("item")
 *     .argIntRange("amount", 1, 64)
 *     .permission("admin.give")
 *     .executes((sender, ctx) -> {
 *         Player target = ctx.get("target", Player.class);
 *         Material item = ctx.get("item", Material.class);
 *         int amount = ctx.get("amount", Integer.class);
 *         target.getInventory().addItem(new ItemStack(item, amount));
 *         sender.sendMessage("Gave " + amount + " " + item + " to " + target.getName());
 *     })
 *     .register(plugin);
 * }</pre>
 * 
 * <h3>Main Entry Points</h3>
 * <ul>
 *   <li>{@link de.feelix.leviathan.Leviathan} - Main API facade with static factory methods</li>
 *   <li>{@link de.feelix.leviathan.command.core.SlashCommand} - Core command class</li>
 *   <li>{@link de.feelix.leviathan.command.core.SlashCommandBuilder} - Command builder</li>
 *   <li>{@link de.feelix.leviathan.command.argument.ArgContext} - Argument configuration</li>
 * </ul>
 * 
 * @see de.feelix.leviathan.Leviathan
 * @see de.feelix.leviathan.command
 */
package de.feelix.leviathan;
