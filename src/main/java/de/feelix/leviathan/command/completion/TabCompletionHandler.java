package de.feelix.leviathan.command.completion;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
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

        // CRITICAL: Parse flags/key-values FIRST to get actual positional args
        // This ensures tab completion matches execution behavior
        String[] positionalArgs = providedArgs;
        de.feelix.leviathan.command.flag.FlagAndKeyValueParser.ParsedResult parsedFlagsKv = null;

        if (hasFlags || hasKeyValues) {
            try {
                de.feelix.leviathan.command.flag.FlagAndKeyValueParser flagKvParser =
                    new de.feelix.leviathan.command.flag.FlagAndKeyValueParser(command.flags(), command.keyValues());
                parsedFlagsKv = flagKvParser.parse(providedArgs, sender);
                positionalArgs = parsedFlagsKv.remainingArgs().toArray(new String[0]);
            } catch (Throwable t) {
                // If parsing fails, fall back to treating all as positional
                if (command.plugin() != null) {
                    command.plugin().getLogger().log(
                        Level.WARNING,
                        "Failed to parse flags/key-values during tab completion", t
                    );
                }
            }

            // Check if current token is a flag or key-value pattern
            if (currentToken.startsWith("-") || currentToken.startsWith("--") ||
                currentToken.contains("=") || currentToken.contains(":")) {
                List<String> flagKvCompletions = generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
                if (!flagKvCompletions.isEmpty()) {
                    return flagKvCompletions;
                }
            }

            // Check if previous token was a key-value key waiting for a value
            if (providedArgs.length >= 2) {
                String prevArg = providedArgs[providedArgs.length - 2];
                if (prevArg.startsWith("--") && !prevArg.contains("=")) {
                    String prevContent = prevArg.substring(2);
                    for (de.feelix.leviathan.command.flag.KeyValue<?> kv : command.keyValues()) {
                        if (kv.matchesKey(prevContent)) {
                            // Previous token was a key, current is the value
                            List<String> flagKvCompletions = generateFlagAndKeyValueCompletions(
                                currentToken, providedArgs, command, sender);
                            if (!flagKvCompletions.isEmpty()) {
                                return flagKvCompletions;
                            }
                        }
                    }
                }
            }
        }

        // Use positional args count for determining current position
        int positionalIndex = positionalArgs.length - 1;
        if (positionalIndex < 0 && !command.args().isEmpty()) {
            // No positional args yet, but current token might be start of one
            positionalIndex = 0;
            positionalArgs = new String[]{currentToken};
        }

        if (command.args().isEmpty()) {
            // No positional args defined, suggest flags/key-values
            if (hasFlags || hasKeyValues) {
                return generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
            }
            return Collections.emptyList();
        }

        boolean lastIsGreedy = command.args().get(command.args().size() - 1).greedy();
        int argCount = command.args().size();

        // Determine the current argument index based on positional args only
        int currentArgIndex = determineCurrentArgIndex(
            positionalIndex, argCount, lastIsGreedy, command, alias, sender, positionalArgs, messages, hasFlags, hasKeyValues);
        if (currentArgIndex < 0) {
            // Past all positional args, suggest flags/key-values
            if (hasFlags || hasKeyValues) {
                return generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
            }
            return Collections.emptyList();
        }

        // Validate previously entered arguments if enabled
        Map<String, Object> parsedSoFar = new LinkedHashMap<>();
        if (command.validateOnTab() && currentArgIndex > 0) {
            if (!validatePreviousArguments(currentArgIndex, positionalArgs, sender, command, parsedSoFar, messages)) {
                return Collections.emptyList();
            }
        }

        Arg<?> current = command.args().get(currentArgIndex);

        // Check per-argument permission
        if (current.permission() != null && !current.permission().isEmpty()
            && !sender.hasPermission(current.permission())) {
            return Collections.emptyList();
        }

        // Determine prefix for greedy arguments using positional args
        String prefix = determinePrefix(positionalArgs, positionalIndex, currentArgIndex, argCount, lastIsGreedy);

        // Generate suggestions for the current positional argument
        List<String> argCompletions = generateSuggestions(
            current, prefix, sender, alias, positionalArgs,
            currentArgIndex, command, parsedSoFar
        );

        // Only merge flag/key-value completions if the current token suggests flag/kv input
        // (i.e., starts with - or is empty and we're not in the middle of required positional args)
        if (hasFlags || hasKeyValues) {
            boolean shouldMergeFlags = false;

            // Merge flags if current token explicitly starts flag syntax
            if (currentToken.startsWith("-")) {
                shouldMergeFlags = true;
            }
            // Or if token is empty AND current argument is optional
            else if (currentToken.isEmpty() && current.optional()) {
                shouldMergeFlags = true;
            }

            if (shouldMergeFlags) {
                List<String> flagKvCompletions = generateFlagAndKeyValueCompletions(
                    currentToken, providedArgs, command, sender);
                if (!flagKvCompletions.isEmpty()) {
                    // Merge both lists
                    Set<String> combined = new LinkedHashSet<>(argCompletions);
                    combined.addAll(flagKvCompletions);
                    List<String> result = new ArrayList<>(combined);
                    Collections.sort(result);
                    return result;
                }
            }
        }

        return argCompletions;
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
            String[] remaining = providedArgs.length > 1
                ? Arrays.copyOfRange(providedArgs, 1, providedArgs.length)
                : new String[0];
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
        @NotNull MessageProvider messages,
        boolean hasFlags,
        boolean hasKeyValues) {

        if (index >= argCount) {
            if (!lastIsGreedy) {
                // Past all positional args
                // Don't show error if flags/key-values exist (user might be typing flags)
                if (!hasFlags && !hasKeyValues && command.sendErrors()) {
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
            if (index < greedyStart || index >= providedArgs.length) {
                return ""; // safety check for invalid indices
            }
            int endIndex = Math.min(index + 1, providedArgs.length);
            return String.join(" ", Arrays.asList(providedArgs).subList(greedyStart, endIndex));
        }
        if (index < 0 || index >= providedArgs.length) {
            return ""; // safety check
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
        String pendingKeyValueKey = null; // Track if previous arg was --key needing a value

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
                    // Check if it's a key-value waiting for next arg (--key value format)
                    KeyValue<?> kv = findKeyValueByKey(keyValues, content);
                    if (kv != null) {
                        // This is a key-value in --key value format, mark as used
                        usedKeyValues.add(content.toLowerCase(Locale.ROOT));
                    } else {
                        // It's a flag
                        usedFlags.add(content.toLowerCase(Locale.ROOT));
                    }
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
                    // Only mark as used if it's actually a valid key-value key
                    if (findKeyValueByKey(keyValues, key) != null) {
                        usedKeyValues.add(key.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        // Check if the previous argument was a key-value key waiting for a value (--key value format)
        if (providedArgs.length >= 2) {
            String prevArg = providedArgs[providedArgs.length - 2];
            if (prevArg.startsWith("--")) {
                String prevContent = prevArg.substring(2);
                if (!prevContent.contains("=")) {
                    KeyValue<?> kv = findKeyValueByKey(keyValues, prevContent);
                    if (kv != null) {
                        pendingKeyValueKey = prevContent;
                    }
                }
            }
        }

        String tokenLower = currentToken.toLowerCase(Locale.ROOT);

        // If completing a value for --key value format
        if (pendingKeyValueKey != null) {
            KeyValue<?> kv = findKeyValueByKey(keyValues, pendingKeyValueKey);
            if (kv != null) {
                return generateKeyValueCompletions(kv, currentToken, sender, messages);
            }
        }

        // Check if current token contains = or : for value completion
        int separatorIdx = -1;
        char separator;
        int eqIdx = currentToken.indexOf('=');
        int colonIdx = currentToken.indexOf(':');

        if (eqIdx > 0 && (colonIdx < 0 || eqIdx < colonIdx)) {
            separatorIdx = eqIdx;
            separator = '=';
        } else if (colonIdx > 0) {
            separatorIdx = colonIdx;
            separator = ':';
        } else {
            separator = '=';
        }

        if (separatorIdx > 0) {
            // Extract key and partial value
            String keyPart = currentToken.substring(0, separatorIdx);
            String valuePart = currentToken.substring(separatorIdx + 1);

            // Remove -- or - prefix if present
            String keyOnly = keyPart;
            if (keyOnly.startsWith("--")) {
                keyOnly = keyOnly.substring(2);
            } else if (keyOnly.startsWith("-")) {
                keyOnly = keyOnly.substring(1);
            }

            // Find matching key-value
            KeyValue<?> kv = findKeyValueByKey(keyValues, keyOnly);
            if (kv != null) {
                List<String> valueCompletions = generateKeyValueCompletions(kv, valuePart, sender, messages);
                // Prepend the key and separator to each value completion
                return valueCompletions.stream()
                    .map(v -> keyPart + separator + v)
                    .collect(java.util.stream.Collectors.toList());
            }
        }

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
                        String suggestion = "--" + longForm;
                        // Add description as hint if available
                        if (flag.description() != null && !flag.description().isEmpty()) {
                            suggestion += messages.tabCompletionHint(flag.description());
                        }
                        completions.add(suggestion);
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
                    String suggestion = "--" + key + "=";

                    // Build info hint
                    List<String> hints = new ArrayList<>();
                    hints.add(kv.parser().getTypeName());
                    if (kv.required()) {
                        hints.add("required");
                    }
                    if (kv.defaultValue() != null) {
                        hints.add("default=" + kv.defaultValue());
                    }
                    if (kv.multipleValues()) {
                        hints.add("multi");
                    }

                    suggestion += messages.tabCompletionHint(String.join(", ", hints));
                    completions.add(suggestion);
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

                    if (shortForm.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        String suggestion = "-" + shortForm;
                        // Add description as hint if available
                        if (flag.description() != null && !flag.description().isEmpty()) {
                            suggestion += messages.tabCompletionHint(flag.description());
                        }
                        completions.add(suggestion);
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
                    String suggestion = key + "=";

                    // Build info hint
                    List<String> hints = new ArrayList<>();
                    hints.add(kv.parser().getTypeName());
                    if (kv.required()) {
                        hints.add("required");
                    }
                    if (kv.defaultValue() != null) {
                        hints.add("default=" + kv.defaultValue());
                    }
                    if (kv.multipleValues()) {
                        hints.add("multi");
                    }

                    suggestion += messages.tabCompletionHint(String.join(", ", hints));
                    completions.add(suggestion);
                }
            }

            // Also suggest -- prefix for flags/key-values when typing empty or partial
            if (currentToken.isEmpty() || "-".startsWith(tokenLower)) {
                // Add -- as a completion hint if there are unused flags or key-values
                boolean hasAvailableFlags = flags.stream().anyMatch(flag -> {
                    if (flag.permission() != null && !flag.permission().isEmpty()
                        && !sender.hasPermission(flag.permission())) {
                        return false;
                    }
                    if (flag.longForm() != null) {
                        return !usedFlags.contains(flag.longForm().toLowerCase(Locale.ROOT));
                    }
                    return !usedFlags.contains(flag.name().toLowerCase(Locale.ROOT));
                });

                boolean hasAvailableKeyValues = keyValues.stream().anyMatch(kv -> {
                    if (kv.permission() != null && !kv.permission().isEmpty()
                        && !sender.hasPermission(kv.permission())) {
                        return false;
                    }
                    if (!kv.multipleValues() && usedKeyValues.contains(kv.key().toLowerCase(Locale.ROOT))) {
                        return false;
                    }
                    return true;
                });

                if (hasAvailableFlags || hasAvailableKeyValues) {
                    completions.add("--");
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }

    /**
     * Generate value completions for a specific key-value pair.
     *
     * @param kv           the key-value definition
     * @param partialValue the partial value typed so far
     * @param sender       the command sender
     * @param messages     the message provider
     * @return list of value completions
     */
    private static @NotNull List<String> generateKeyValueCompletions(
        @NotNull KeyValue<?> kv,
        @NotNull String partialValue,
        @NotNull CommandSender sender,
        @NotNull MessageProvider messages) {

        List<String> completions = new ArrayList<>();

        // Get completions from parser
        List<String> parserCompletions = kv.parser().complete(partialValue, sender);
        if (parserCompletions != null && !parserCompletions.isEmpty()) {
            completions.addAll(parserCompletions);
        }

        // Add default value if it matches and is not already typed
        if (kv.defaultValue() != null) {
            String defaultStr = kv.defaultValue().toString();
            if (defaultStr.toLowerCase(Locale.ROOT).startsWith(partialValue.toLowerCase(Locale.ROOT))) {
                if (!completions.contains(defaultStr)) {
                    completions.add(defaultStr + messages.tabCompletionDefaultHint());
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }

    /**
     * Find a key-value by its key string.
     *
     * @param keyValues the list of key-value definitions
     * @param key       the key to find
     * @return the matching KeyValue or null
     */
    private static @Nullable KeyValue<?> findKeyValueByKey(@NotNull List<KeyValue<?>> keyValues, @NotNull String key) {
        for (KeyValue<?> kv : keyValues) {
            if (kv.matchesKey(key)) {
                return kv;
            }
        }
        return null;
    }
}
