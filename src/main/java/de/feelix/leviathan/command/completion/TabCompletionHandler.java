package de.feelix.leviathan.command.completion;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.command.core.SlashCommand;
import de.feelix.leviathan.command.flag.Flag;
import de.feelix.leviathan.command.flag.KeyValue;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.message.MessageProvider;
import de.feelix.leviathan.command.validation.ValidationHelper;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.util.StringSimilarity;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles tab completion logic for SlashCommand.
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
     * @param sender       the command sender requesting completions
     * @param alias        the command alias used
     * @param providedArgs the arguments typed so far
     * @param command      the SlashCommand instance
     * @param messages     the message provider for error messages
     * @return list of completion suggestions
     */
    public static @NotNull List<String> generateCompletions(
        @NotNull CommandSender sender,
        @NotNull String alias,
        @NotNull String[] providedArgs,
        @NotNull SlashCommand command,
        @NotNull MessageProvider messages) {

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
        
        // Get the current token being typed
        String currentToken = providedArgs[providedArgs.length - 1];
        
        // Check if current token looks like a flag or key-value
        boolean hasFlags = !command.flags().isEmpty();
        boolean hasKeyValues = !command.keyValues().isEmpty();
        
        if (hasFlags || hasKeyValues) {
            // Check if current token is a flag or key-value pattern
            if (currentToken.startsWith("-") || currentToken.startsWith("--") ||
                currentToken.contains("=") || currentToken.contains(":")) {
                List<String> flagKvCompletions = generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
                if (!flagKvCompletions.isEmpty()) {
                    return flagKvCompletions;
                }
            }
            
            // Also suggest flags/key-values when user hasn't typed anything yet for this token
            // or when they're past all positional arguments
            int argCount = command.args().isEmpty() ? 0 : command.args().size();
            if (index >= argCount || currentToken.isEmpty()) {
                List<String> flagKvCompletions = generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
                if (!flagKvCompletions.isEmpty()) {
                    // If we have positional args to complete too, merge them
                    if (index < argCount && !command.args().isEmpty()) {
                        // Continue to get argument completions below
                    } else {
                        return flagKvCompletions;
                    }
                }
            }
        }
        
        if (command.args().isEmpty()) return Collections.emptyList();

        boolean lastIsGreedy = command.args().get(command.args().size() - 1).greedy();
        int argCount = command.args().size();

        // Determine current argument index
        int currentArgIndex = determineCurrentArgIndex(
            index, argCount, lastIsGreedy, command, alias, sender, providedArgs, messages);
        if (currentArgIndex < 0) return Collections.emptyList();

        // Validate previously entered arguments if enabled
        Map<String, Object> parsedSoFar = new LinkedHashMap<>();
        if (command.validateOnTab() && currentArgIndex > 0) {
            if (!validatePreviousArguments(currentArgIndex, providedArgs, sender, command, parsedSoFar, messages)) {
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
        return generateSuggestions(
            current, prefix, sender, alias, providedArgs,
            currentArgIndex, command, parsedSoFar
        );
    }

    /**
     * Handle tab completions for subcommands.
     */
    private static @NotNull List<String> handleSubcommandCompletions(
        @NotNull CommandSender sender,
        @NotNull String alias,
        @NotNull String[] providedArgs,
        @NotNull SlashCommand command) {

        if (providedArgs.length == 0) {
            return Collections.emptyList();
        }

        String first = providedArgs[0];
        String firstLow = first.toLowerCase(Locale.ROOT);
        SlashCommand sub = command.subcommands().get(firstLow);

        if (providedArgs.length == 1) {
            // Suggest subcommand aliases, filtered by permission
            List<String> names = new ArrayList<>();
            List<String> allSubcommandNames = new ArrayList<>();
            for (Map.Entry<String, SlashCommand> e : command.subcommands().entrySet()) {
                String perm = e.getValue().permission();
                if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) continue;
                String key = e.getKey();
                allSubcommandNames.add(key);
                if (key.startsWith(firstLow)) {
                    names.add(key);
                }
            }

            // If fuzzy matching is enabled and no exact prefix matches, suggest similar subcommands
            if (names.isEmpty() && command.fuzzySubcommandMatching() && !firstLow.isEmpty()) {
                List<String> similar = StringSimilarity.findSimilar(firstLow, allSubcommandNames, 3, 0.4);
                names.addAll(similar);
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
        @NotNull SlashCommand command,
        @NotNull String alias,
        @NotNull CommandSender sender,
        @NotNull String[] providedArgs,
        @NotNull MessageProvider messages) {

        if (index >= argCount) {
            if (!lastIsGreedy) {
                // Too many arguments typed
                if (command.sendErrors()) {
                    sender.sendMessage(messages.tooManyArguments(command.fullCommandPath(alias), command.usage()));
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
        @NotNull SlashCommand command,
        @NotNull Map<String, Object> parsedSoFar,
        @NotNull MessageProvider messages) {

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
                    sender.sendMessage(messages.invalidArgumentValue(prev.name(), prev.parser().getTypeName(), msg));
                }
                return false;
            }

            Object parsedValue = res.value().orElse(null);

            // Apply validations from ArgContext (range, length, pattern, custom validators)
            ArgContext ctx = prev.context();
            String validationError = ValidationHelper.validateValue(
                parsedValue, ctx, prev.name(), prev.parser().getTypeName(), messages);
            if (validationError != null) {
                if (command.sendErrors()) {
                    sender.sendMessage(messages.validationFailed(prev.name(), validationError));
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
     * Default timeout for async completion operations in milliseconds.
     */
    private static final long ASYNC_COMPLETION_TIMEOUT_MS = 2000;

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
        @NotNull SlashCommand command,
        @NotNull Map<String, Object> parsedSoFar) {

        List<String> completions = current.context().completionsPredefined();

        // Check for predefined completions first
        if (!completions.isEmpty()) {
            return filterAndSort(completions, prefix);
        }

        // Check for dynamic completion provider
        if (current.context().completionsDynamic() != null) {
            ArgContext.DynamicCompletionProvider provider = current.context().completionsDynamic();
            DynamicCompletionContext dctx = new DynamicCompletionContext(
                sender, alias, providedArgs, currentArgIndex, prefix,
                command.args(), parsedSoFar, command
            );
            List<String> dyn = provider.provide(dctx);
            if (dyn == null) dyn = Collections.emptyList();
            return filterAndSort(dyn, prefix);
        }

        // Check for async predefined completion supplier
        if (current.context().completionsPredefinedAsync() != null) {
            ArgContext.AsyncPredefinedCompletionSupplier supplier = current.context().completionsPredefinedAsync();
            try {
                CompletableFuture<List<String>> future = supplier.supplyAsync();
                List<String> asyncCompletions = future.get(ASYNC_COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (asyncCompletions == null) asyncCompletions = Collections.emptyList();
                return filterAndSort(asyncCompletions, prefix);
            } catch (Exception e) {
                // Log the error and fall through to other completion sources
                if (command.plugin() != null) {
                    command.plugin().getLogger().log(
                        Level.WARNING,
                        "Async predefined completion failed for argument '" + current.name() + "'", e
                    );
                }
            }
        }

        // Check for async dynamic completion provider
        if (current.context().completionsDynamicAsync() != null) {
            ArgContext.AsyncDynamicCompletionProvider provider = current.context().completionsDynamicAsync();
            DynamicCompletionContext dctx = new DynamicCompletionContext(
                sender, alias, providedArgs, currentArgIndex, prefix,
                command.args(), parsedSoFar, command
            );
            try {
                CompletableFuture<List<String>> future = provider.provideAsync(dctx);
                List<String> asyncDyn = future.get(ASYNC_COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (asyncDyn == null) asyncDyn = Collections.emptyList();
                return filterAndSort(asyncDyn, prefix);
            } catch (Exception e) {
                // Log the error and fall through to parser completions
                if (command.plugin() != null) {
                    command.plugin().getLogger().log(
                        Level.WARNING,
                        "Async dynamic completion failed for argument '" + current.name() + "'", e
                    );
                }
            }
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
     * Enhanced to support smart filtering: exact matches first, then prefix matches, then substring matches.
     */
    private static @NotNull List<String> filterAndSort(@NotNull List<String> completions, @NotNull String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            // No prefix - return all completions sorted
            List<String> result = new ArrayList<>();
            for (String s : completions) {
                if (s != null) {
                    result.add(s);
                }
            }
            Collections.sort(result);
            return result;
        }

        String pfxLow = prefix.toLowerCase(Locale.ROOT);
        List<String> exactMatches = new ArrayList<>();
        List<String> prefixMatches = new ArrayList<>();
        List<String> substringMatches = new ArrayList<>();

        for (String s : completions) {
            if (s == null) continue;
            String sLow = s.toLowerCase(Locale.ROOT);

            // Exact match (case-insensitive)
            if (sLow.equals(pfxLow)) {
                exactMatches.add(s);
            }
            // Prefix match
            else if (sLow.startsWith(pfxLow)) {
                prefixMatches.add(s);
            }
            // Substring match (for better discoverability)
            else if (sLow.contains(pfxLow)) {
                substringMatches.add(s);
            }
        }

        // Sort each category
        Collections.sort(exactMatches);
        Collections.sort(prefixMatches);
        Collections.sort(substringMatches);

        // Combine: exact first, then prefix, then substring
        List<String> result = new ArrayList<>();
        result.addAll(exactMatches);
        result.addAll(prefixMatches);
        result.addAll(substringMatches);

        return result;
    }

    /**
     * Generate tab completions for flags and key-value pairs.
     *
     * @param currentToken  the current token being typed
     * @param providedArgs  all arguments provided so far
     * @param command       the SlashCommand instance
     * @param sender        the command sender
     * @return list of flag and key-value completion suggestions
     */
    private static @NotNull List<String> generateFlagAndKeyValueCompletions(
        @NotNull String currentToken,
        @NotNull String[] providedArgs,
        @NotNull SlashCommand command,
        @NotNull CommandSender sender) {

        List<String> completions = new ArrayList<>();
        List<Flag> flags = command.flags();
        List<KeyValue<?>> keyValues = command.keyValues();

        // Track which flags and key-values have already been used
        Set<String> usedFlags = new HashSet<>();
        Set<String> usedKeyValues = new HashSet<>();

        // Parse already provided arguments to find used flags/key-values
        for (int i = 0; i < providedArgs.length - 1; i++) {
            String arg = providedArgs[i];
            
            // Check for long form flags (--xxx or --no-xxx)
            if (arg.startsWith("--")) {
                String content = arg.substring(2);
                int eqIdx = content.indexOf('=');
                if (eqIdx > 0) {
                    // --key=value format - mark key-value as used
                    String key = content.substring(0, eqIdx);
                    usedKeyValues.add(key.toLowerCase(Locale.ROOT));
                } else if (content.startsWith("no-")) {
                    // Negated flag --no-xxx
                    String flagName = content.substring(3);
                    usedFlags.add(flagName.toLowerCase(Locale.ROOT));
                } else {
                    // Could be flag or key-value needing next arg
                    usedFlags.add(content.toLowerCase(Locale.ROOT));
                    usedKeyValues.add(content.toLowerCase(Locale.ROOT));
                }
            }
            // Check for short form flags (-x or -xyz)
            else if (arg.startsWith("-") && arg.length() > 1 && !Character.isDigit(arg.charAt(1))) {
                String content = arg.substring(1);
                int eqIdx = content.indexOf('=');
                if (eqIdx > 0) {
                    String key = content.substring(0, eqIdx);
                    usedKeyValues.add(key.toLowerCase(Locale.ROOT));
                } else {
                    // Mark each character as a used short flag
                    for (char c : content.toCharArray()) {
                        for (Flag flag : flags) {
                            if (flag.shortForm() != null && flag.shortForm() == c) {
                                if (flag.longForm() != null) {
                                    usedFlags.add(flag.longForm().toLowerCase(Locale.ROOT));
                                }
                                usedFlags.add(flag.name().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            }
            // Check for key=value or key:value format
            else {
                int eqIdx = arg.indexOf('=');
                int colonIdx = arg.indexOf(':');
                int separatorIdx = -1;
                if (eqIdx > 0 && (colonIdx < 0 || eqIdx < colonIdx)) {
                    separatorIdx = eqIdx;
                } else if (colonIdx > 0) {
                    separatorIdx = colonIdx;
                }
                if (separatorIdx > 0) {
                    String key = arg.substring(0, separatorIdx);
                    usedKeyValues.add(key.toLowerCase(Locale.ROOT));
                }
            }
        }

        String tokenLower = currentToken.toLowerCase(Locale.ROOT);

        // Generate completions based on current token pattern
        if (currentToken.startsWith("--")) {
            String prefix = currentToken.substring(2).toLowerCase(Locale.ROOT);
            
            // Suggest long form flags
            for (Flag flag : flags) {
                // Check permission
                if (flag.permission() != null && !flag.permission().isEmpty() 
                    && !sender.hasPermission(flag.permission())) {
                    continue;
                }
                
                if (flag.longForm() != null) {
                    String longForm = flag.longForm();
                    String longFormLower = longForm.toLowerCase(Locale.ROOT);
                    
                    // Don't suggest if already used
                    if (usedFlags.contains(longFormLower)) continue;
                    
                    if (longFormLower.startsWith(prefix)) {
                        completions.add("--" + longForm);
                    }
                    
                    // Suggest negation form if supported
                    if (flag.supportsNegation()) {
                        String negated = "no-" + longForm;
                        if (negated.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            completions.add("--" + negated);
                        }
                    }
                }
            }
            
            // Suggest key-value keys with --key= format
            for (KeyValue<?> kv : keyValues) {
                // Check permission
                if (kv.permission() != null && !kv.permission().isEmpty() 
                    && !sender.hasPermission(kv.permission())) {
                    continue;
                }
                
                String key = kv.key();
                String keyLower = key.toLowerCase(Locale.ROOT);
                
                // Don't suggest if already used (unless multipleValues is enabled)
                if (!kv.multipleValues() && usedKeyValues.contains(keyLower)) continue;
                
                if (keyLower.startsWith(prefix)) {
                    completions.add("--" + key + "=");
                }
            }
        }
        else if (currentToken.startsWith("-") && !currentToken.startsWith("--")) {
            String prefix = currentToken.substring(1).toLowerCase(Locale.ROOT);
            
            // Suggest short form flags
            for (Flag flag : flags) {
                // Check permission
                if (flag.permission() != null && !flag.permission().isEmpty() 
                    && !sender.hasPermission(flag.permission())) {
                    continue;
                }
                
                if (flag.shortForm() != null) {
                    String shortForm = String.valueOf(flag.shortForm());
                    
                    // Check if this flag is already used
                    boolean alreadyUsed = false;
                    if (flag.longForm() != null) {
                        alreadyUsed = usedFlags.contains(flag.longForm().toLowerCase(Locale.ROOT));
                    }
                    if (alreadyUsed) continue;
                    
                    if (shortForm.toLowerCase(Locale.ROOT).startsWith(prefix) || prefix.isEmpty()) {
                        completions.add("-" + shortForm);
                    }
                }
            }
        }
        else {
            // Suggest key= or key: formats for key-value pairs
            for (KeyValue<?> kv : keyValues) {
                // Check permission
                if (kv.permission() != null && !kv.permission().isEmpty() 
                    && !sender.hasPermission(kv.permission())) {
                    continue;
                }
                
                String key = kv.key();
                String keyLower = key.toLowerCase(Locale.ROOT);
                
                // Don't suggest if already used (unless multipleValues is enabled)
                if (!kv.multipleValues() && usedKeyValues.contains(keyLower)) continue;
                
                if (keyLower.startsWith(tokenLower)) {
                    completions.add(key + "=");
                }
            }
            
            // Also suggest -- prefix for flags/key-values when typing empty or partial
            if ("-".startsWith(tokenLower)) {
                // Add -- as a completion hint if there are flags or key-values
                if (!flags.isEmpty() || !keyValues.isEmpty()) {
                    completions.add("--");
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
