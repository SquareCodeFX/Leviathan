package de.feelix.leviathan.command.completion;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.core.FluentCommand;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.validation.ValidationHelper;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles tab completion logic for FluentCommand.
 * Encapsulates the complex logic for generating context-aware completions
 * including permission checks, validation, and dynamic completion providers.
 */
public final class TabCompletionHandler {
    
    private TabCompletionHandler() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate tab completions for the given command context.
     *
     * @param sender the command sender requesting completions
     * @param alias the command alias used
     * @param providedArgs the arguments typed so far
     * @param command the FluentCommand instance
     * @return list of completion suggestions
     */
    public static @NotNull List<String> generateCompletions(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] providedArgs,
            @NotNull FluentCommand command) {
        
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(alias, "alias");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(command, "command");

        // Gate completions by command-level permission
        if (command.permission() != null && !command.permission().isEmpty() 
                && !sender.hasPermission(command.permission())) {
            return Collections.emptyList();
        }

        // Player-only constraint check
        if (command.playerOnly() && !(sender instanceof Player)) {
            return Collections.emptyList();
        }

        // Guards gate tab completions as well
        for (Guard g : command.guards()) {
            try {
                if (!g.test(sender)) {
                    return Collections.emptyList();
                }
            } catch (Throwable ignored) {
                return Collections.emptyList();
            }
        }

        // Handle subcommand completions
        if (!command.subcommands().isEmpty()) {
            return handleSubcommandCompletions(sender, alias, providedArgs, command);
        }

        int index = providedArgs.length - 1; // current token index
        if (index < 0) return Collections.emptyList();
        if (command.args().isEmpty()) return Collections.emptyList();

        boolean lastIsGreedy = command.args().get(command.args().size() - 1).greedy();
        int argCount = command.args().size();

        // Determine current argument index
        int currentArgIndex = determineCurrentArgIndex(index, argCount, lastIsGreedy, command, alias, sender, providedArgs);
        if (currentArgIndex < 0) return Collections.emptyList();

        // Validate previously entered arguments if enabled
        Map<String, Object> parsedSoFar = new LinkedHashMap<>();
        if (command.validateOnTab() && currentArgIndex > 0) {
            if (!validatePreviousArguments(currentArgIndex, providedArgs, sender, command, parsedSoFar)) {
                return Collections.emptyList();
            }
        }

        Arg<?> current = command.args().get(currentArgIndex);
        
        // Check per-argument permission
        if (current.permission() != null && !current.permission().isEmpty() 
                && !sender.hasPermission(current.permission())) {
            return Collections.emptyList();
        }

        // Determine prefix for greedy arguments
        String prefix = determinePrefix(providedArgs, index, currentArgIndex, argCount, lastIsGreedy);

        // Generate suggestions
        return generateSuggestions(current, prefix, sender, alias, providedArgs, 
                currentArgIndex, command, parsedSoFar);
    }

    /**
     * Handle tab completions for subcommands.
     */
    private static @NotNull List<String> handleSubcommandCompletions(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] providedArgs,
            @NotNull FluentCommand command) {
        
        if (providedArgs.length == 0) {
            return Collections.emptyList();
        }

        String first = providedArgs[0];
        String firstLow = first.toLowerCase(Locale.ROOT);
        FluentCommand sub = command.subcommands().get(firstLow);

        if (providedArgs.length == 1) {
            // Suggest subcommand aliases, filtered by permission
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, FluentCommand> e : command.subcommands().entrySet()) {
                String perm = e.getValue().permission();
                if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) continue;
                String key = e.getKey();
                if (key.startsWith(firstLow)) {
                    names.add(key);
                }
            }
            Collections.sort(names);
            return names;
        }

        if (sub != null) {
            // Delegate to the subcommand for the remaining tokens
            String[] remaining = Arrays.copyOfRange(providedArgs, 1, providedArgs.length);
            return sub.onTabComplete(sender, null, sub.name(), remaining);
        }

        // If first token doesn't match a subcommand, fall through to command's own args
        return Collections.emptyList();
    }

    /**
     * Determine the current argument index accounting for greedy arguments.
     */
    private static int determineCurrentArgIndex(
            int index,
            int argCount,
            boolean lastIsGreedy,
            @NotNull FluentCommand command,
            @NotNull String alias,
            @NotNull CommandSender sender,
            @NotNull String[] providedArgs) {
        
        if (index >= argCount) {
            if (!lastIsGreedy) {
                // Too many arguments typed
                if (command.validateOnTab() && command.sendErrors()) {
                    sender.sendMessage("§cToo many arguments. Usage: /" + command.fullCommandPath(alias) + " " + command.usage());
                }
                return -1;
            }
            return argCount - 1; // all extra tokens belong to greedy last arg
        }
        return index;
    }

    /**
     * Validate previously entered arguments during tab completion.
     *
     * @return true if all previous arguments are valid, false otherwise
     */
    private static boolean validatePreviousArguments(
            int currentArgIndex,
            @NotNull String[] providedArgs,
            @NotNull CommandSender sender,
            @NotNull FluentCommand command,
            @NotNull Map<String, Object> parsedSoFar) {
        
        for (int i = 0; i < currentArgIndex; i++) {
            Arg<?> prev = command.args().get(i);
            
            // Check per-argument permission
            if (prev.permission() != null && !prev.permission().isEmpty() 
                    && !sender.hasPermission(prev.permission())) {
                return false;
            }

            String token = providedArgs[i];
            ParseResult<?> res = prev.parser().parse(token, sender);
            
            if (res == null) {
                throw new ParsingException(
                    "Parser " + prev.parser().getClass().getName() 
                    + " returned null ParseResult for argument '" + prev.name() + "'");
            }

            if (!res.isSuccess()) {
                if (command.sendErrors()) {
                    String msg = res.error().orElse("invalid value");
                    sender.sendMessage(
                        "§cInvalid value for '" + prev.name() + "' (expected " 
                        + prev.parser().getTypeName() + "): " + msg);
                }
                return false;
            }
            
            Object parsedValue = res.value().orElse(null);
            
            // Apply validations from ArgContext (range, length, pattern, custom validators)
            ArgContext ctx = prev.context();
            String validationError = ValidationHelper.validateValue(parsedValue, ctx, prev.name(), prev.parser().getTypeName());
            if (validationError != null) {
                if (command.sendErrors()) {
                    sender.sendMessage("§cInvalid value for '" + prev.name() + "': " + validationError);
                }
                return false;
            }
            
            parsedSoFar.put(prev.name(), parsedValue);
        }
        return true;
    }

    /**
     * Determine the prefix string for completion matching, accounting for greedy arguments.
     */
    private static @NotNull String determinePrefix(
            @NotNull String[] providedArgs,
            int index,
            int currentArgIndex,
            int argCount,
            boolean lastIsGreedy) {
        
        if (lastIsGreedy && currentArgIndex == argCount - 1) {
            // Join all tokens that belong to the greedy argument
            int greedyStart = argCount - 1;
            if (index < greedyStart) {
                return ""; // safety
            }
            return String.join(" ", Arrays.asList(providedArgs).subList(greedyStart, index + 1));
        }
        return providedArgs[index];
    }

    /**
     * Generate completion suggestions for the current argument.
     */
    private static @NotNull List<String> generateSuggestions(
            @NotNull Arg<?> current,
            @NotNull String prefix,
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] providedArgs,
            int currentArgIndex,
            @NotNull FluentCommand command,
            @NotNull Map<String, Object> parsedSoFar) {
        
        List<String> completions = current.context().completionsPredefined();
        
        // Check for predefined completions first
        if (completions != null && !completions.isEmpty()) {
            return filterAndSort(completions, prefix);
        }
        
        // Check for dynamic completion provider
        if (current.context().completionsDynamic() != null) {
            ArgContext.DynamicCompletionProvider provider = current.context().completionsDynamic();
            DynamicCompletionContext dctx = new DynamicCompletionContext(
                sender, alias, providedArgs, currentArgIndex, prefix, 
                command.args(), parsedSoFar, command);
            List<String> dyn = provider.provide(dctx);
            if (dyn == null) dyn = Collections.emptyList();
            return filterAndSort(dyn, prefix);
        }
        
        // Fall back to parser completions
        List<String> suggestions = current.parser().complete(prefix, sender);
        if (suggestions == null) {
            throw new ParsingException(
                "Parser " + current.parser().getClass().getName() 
                + " returned null suggestions for argument '" + current.name() + "'");
        }
        Collections.sort(suggestions);
        return suggestions;
    }

    /**
     * Filter completions by prefix and sort them.
     */
    private static @NotNull List<String> filterAndSort(@NotNull List<String> completions, @NotNull String prefix) {
        String pfxLow = prefix.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String s : completions) {
            if (s == null) continue;
            if (pfxLow.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(pfxLow)) {
                filtered.add(s);
            }
        }
        Collections.sort(filtered);
        return filtered;
    }
}
