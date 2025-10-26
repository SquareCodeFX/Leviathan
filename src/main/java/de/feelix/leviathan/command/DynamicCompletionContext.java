package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime context passed to dynamic completion providers for rich, case-by-case suggestions.
 */
public record DynamicCompletionContext(@NotNull CommandSender sender, @NotNull String alias,
                                       @NotNull String[] providedArgs, int currentArgIndex, @NotNull String prefix,
                                       @NotNull List<Arg<?>> allArgs, @NotNull Map<String, Object> parsedArgsSoFar,
                                       @NotNull FluentCommand command) {

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
}
