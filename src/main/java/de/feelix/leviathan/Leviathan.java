package de.feelix.leviathan;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.core.SlashCommand;
import de.feelix.leviathan.command.core.SlashCommandBuilder;

/**
 * Main API facade for the Leviathan command framework.
 * <p>
 * This class provides convenient static entry points for creating commands and builders,
 * offering a cleaner and more discoverable API surface.
 * <p>
 * <b>Example usage:</b>
 * <pre>{@code
 * Leviathan.command("example")
 *     .description("An example command")
 *     .argString("name")
 *     .executes((sender, ctx) -> {
 *         String name = ctx.get("name", String.class);
 *         sender.sendMessage("Hello, " + name + "!");
 *     })
 *     .register(plugin);
 * }</pre>
 *
 * @since 1.2.0
 */
public final class Leviathan {
    
    private Leviathan() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Create a new command builder with the given name.
     * <p>
     * This is the primary entry point for building commands in the Leviathan framework.
     * Equivalent to {@link SlashCommand#builder(String)}.
     *
     * @param name the command name as declared in plugin.yml
     * @return a new SlashCommandBuilder instance
     */
    public static @NotNull SlashCommandBuilder command(@NotNull String name) {
        return SlashCommand.builder(name);
    }
    
    /**
     * Create a new argument context builder for configuring argument behavior.
     * <p>
     * Use this to define validation rules, completions, permissions, and other
     * argument-specific options.
     * <p>
     * Equivalent to {@link ArgContext#builder()}.
     *
     * @return a new ArgContext.Builder instance
     */
    public static @NotNull ArgContext.Builder argContext() {
        return ArgContext.builder();
    }
    
    /**
     * Get the default argument context (no special configuration).
     * <p>
     * Equivalent to {@link ArgContext#defaultContext()}.
     *
     * @return the default ArgContext instance
     */
    public static @NotNull ArgContext defaultArgContext() {
        return ArgContext.defaultContext();
    }
}
