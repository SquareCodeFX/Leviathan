package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime context passed to dynamic completion providers for rich, case-by-case suggestions.
 */
public final class DynamicCompletionContext {
    private final @NotNull CommandSender sender;
    private final @NotNull String alias;
    private final @NotNull String[] providedArgs;
    private final int currentArgIndex;
    private final @NotNull String prefix;
    private final @NotNull List<Arg<?>> allArgs;
    private final @NotNull Map<String, Object> parsedArgsSoFar;
    private final @NotNull FluentCommand command;

    public DynamicCompletionContext(@NotNull CommandSender sender,
                                    @NotNull String alias,
                                    @NotNull String[] providedArgs,
                                    int currentArgIndex,
                                    @NotNull String prefix,
                                    @NotNull List<Arg<?>> allArgs,
                                    @NotNull Map<String, Object> parsedArgsSoFar,
                                    @NotNull FluentCommand command) {
        this.sender = sender;
        this.alias = alias;
        this.providedArgs = providedArgs;
        this.currentArgIndex = currentArgIndex;
        this.prefix = prefix;
        this.allArgs = List.copyOf(allArgs);
        this.parsedArgsSoFar = Collections.unmodifiableMap(parsedArgsSoFar);
        this.command = command;
    }

    public @NotNull CommandSender getSender() { return sender; }
    public @NotNull String getAlias() { return alias; }
    public @NotNull String[] getProvidedArgs() { return providedArgs; }
    public int getCurrentArgIndex() { return currentArgIndex; }
    public @NotNull String getPrefix() { return prefix; }
    public @NotNull List<Arg<?>> getAllArgs() { return allArgs; }
    public @NotNull Map<String, Object> getParsedArgsSoFar() { return parsedArgsSoFar; }
    public @NotNull FluentCommand getCommand() { return command; }
}
