package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.command.async.CancellationToken;
import de.feelix.leviathan.command.async.Progress;
import de.feelix.leviathan.command.completion.TabCompletionHandler;
import de.feelix.leviathan.command.cooldown.CooldownManager;
import de.feelix.leviathan.command.cooldown.CooldownResult;
import de.feelix.leviathan.command.pagination.PaginationHelper;
import de.feelix.leviathan.command.pagination.config.PaginationConfig;
import de.feelix.leviathan.command.error.ErrorType;
import de.feelix.leviathan.command.error.ExceptionHandler;
import de.feelix.leviathan.command.flag.Flag;
import de.feelix.leviathan.command.flag.FlagAndKeyValueParser;
import de.feelix.leviathan.command.flag.KeyValue;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.message.DefaultMessageProvider;
import de.feelix.leviathan.command.message.MessageProvider;
import de.feelix.leviathan.command.validation.CrossArgumentValidator;
import de.feelix.leviathan.command.validation.ValidationHelper;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.exceptions.CommandExecutionException;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.util.StringSimilarity;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A fluent, type-safe command specification for Bukkit/Spigot/Paper.
 * <p>
 * Key features:
 * <ul>
 *   <li>Strongly typed arguments with pluggable parsers</li>
 *   <li>Optional arguments and per-argument permissions</li>
 *   <li>Greedy trailing string support (captures the remainder of the line)</li>
 *   <li>Optional validation during tab completion to guide users</li>
 *   <li>Configurable asynchronous execution via {@link CompletableFuture}</li>
 * </ul>
 * This class implements both {@link CommandExecutor} and {@link TabCompleter} and is typically
 * created and configured via its {@link SlashCommandBuilder}, then registered against a command declared in plugin
 * .yml.
 */
public final class SlashCommand implements CommandExecutor, TabCompleter {

    // Confirmation tracking: maps "commandName:senderName" to expiration time (System.currentTimeMillis())
    private static final Map<String, Long> pendingConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MILLIS = 10000L; // 10 seconds

    /**
     * Create a new builder for a command with the given name.
     *
     * @param name Primary command name as declared in plugin.yml.
     * @return a new SlashCommandBuilder instance
     */
    public static @NotNull SlashCommandBuilder builder(@NotNull String name) {
        return new SlashCommandBuilder(name);
    }

    /**
     * Fluent alias for {@link #builder(String)}.
     * Create a new command builder with the given name.
     * <p>
     * This provides a more concise entry point: {@code SlashCommand.create("mycommand")}
     *
     * @param name Primary command name as declared in plugin.yml.
     * @return a new SlashCommandBuilder instance
     */
    public static @NotNull SlashCommandBuilder create(@NotNull String name) {
        return builder(name);
    }

    private final String name;
    private final List<String> aliases;
    private final String description;
    final String permission;
    final boolean playerOnly;
    final boolean sendErrors;
    private final boolean async;
    final boolean validateOnTab;
    final List<Arg<?>> args;
    final Map<String, SlashCommand> subcommands;
    private final CommandAction action;
    private final AsyncCommandAction asyncActionAdv;
    private final long asyncTimeoutMillis;
    final List<Guard> guards;
    private final List<CrossArgumentValidator> crossArgumentValidators;
    @Nullable
    ExceptionHandler exceptionHandler;
    private final long perUserCooldownMillis;
    private final long perServerCooldownMillis;
    private final boolean enableHelp;
    private final int helpPageSize;
    private final String cachedUsage;
    private final MessageProvider messages;
    private final boolean sanitizeInputs;
    private final boolean fuzzySubcommandMatching;
    private final double fuzzyMatchThreshold;
    private final boolean debugMode;
    final List<Flag> flags;
    final List<KeyValue<?>> keyValues;
    private final boolean awaitConfirmation;
    private final List<ExecutionHook.Before> beforeHooks;
    private final List<ExecutionHook.After> afterHooks;
    JavaPlugin plugin;
    private boolean subOnly = false;
    @Nullable
    private SlashCommand parent = null;


    /**
     * Marks this command instance as a subcommand. Intended for internal use by the Builder
     * when registering subcommands so that attempts to register the subcommand directly
     * can be rejected with a helpful error.
     */
    void markAsSubcommand() {
        this.subOnly = true;
    }

    /**
     * Sets the parent command for this subcommand. Intended for internal use by the Builder
     * when registering subcommands to maintain the command hierarchy.
     *
     * @param parent the parent command
     */
    void setParent(@NotNull SlashCommand parent) {
        Preconditions.checkNotNull(parent, "parent");
        this.parent = parent;
    }

    /**
     * Builds the full command path by traversing up the parent chain.
     * For example, if this is a "history" subcommand under "venias", this returns "venias history".
     * For root commands, this simply returns the command name.
     *
     * @param alias the alias used to invoke this specific command
     * @return the full command path as a string
     */
    @NotNull
    public String fullCommandPath(@NotNull String alias) {
        Preconditions.checkNotNull(alias, "alias");
        if (parent == null) {
            return alias;
        }
        // Recursively build the path from root to this command
        return parent.fullCommandPath(parent.name()) + " " + alias;
    }

    /**
     * Register this already-built command instance as executor and tab-completer for the
     * command with the same name declared in plugin.yml.
     * This is intended for root commands only.
     * If this command was added as a subcommand to another command, calling this will throw.
     *
     * @param plugin plugin registering the command (must declare the command in plugin.yml)
     * @throws ApiMisuseException            if this command is marked as a subcommand
     * @throws CommandConfigurationException if the command is not declared in plugin.yml
     */
    public void register(@NotNull JavaPlugin plugin) {
        Preconditions.checkNotNull(plugin, "plugin");
        if (subOnly) {
            throw new ApiMisuseException("Subcommand '" + name
                                         + "' must not be registered directly. Register only the root command "
                                         + "containing it.");
        }
        this.plugin = plugin;

        // Register primary command
        org.bukkit.command.PluginCommand pc = plugin.getCommand(name);
        if (pc == null) {
            throw new CommandConfigurationException("Command not declared in plugin.yml: " + name);
        }
        pc.setExecutor(this);
        pc.setTabCompleter(this);
    }

    /**
     * Retrieves the description associated with the command.
     *
     * @return a non-null string representing the command's description
     */
    public @NotNull String description() {
        return description;
    }

    /**
     * @return the primary command name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return an immutable list of additional command aliases
     */
    public @NotNull List<String> aliases() {
        return aliases;
    }

    /**
     * @return the command-level permission node, or null if none
     */
    public @Nullable String permission() {
        return permission;
    }

    /**
     * @return true if this command can only be executed by players (not console)
     */
    public boolean playerOnly() {
        return playerOnly;
    }

    /**
     * @return true if error messages should be sent to the sender
     */
    public boolean sendErrors() {
        return sendErrors;
    }

    /**
     * @return true if arguments should be validated during tab completion
     */
    public boolean validateOnTab() {
        return validateOnTab;
    }

    /**
     * @return true if input sanitization is enabled for string arguments
     */
    public boolean sanitizeInputs() {
        return sanitizeInputs;
    }

    /**
     * @return true if fuzzy matching is enabled for subcommands
     */
    public boolean fuzzySubcommandMatching() {
        return fuzzySubcommandMatching;
    }

    /**
     * @return an immutable list of argument definitions
     */
    public @NotNull List<Arg<?>> args() {
        return List.copyOf(args);
    }

    /**
     * @return an immutable map of subcommand aliases to subcommand instances
     */
    public @NotNull Map<String, SlashCommand> subcommands() {
        return Map.copyOf(subcommands);
    }

    /**
     * @return an immutable list of guards
     */
    public @NotNull List<Guard> guards() {
        return List.copyOf(guards);
    }

    /**
     * @return an immutable list of flag definitions
     */
    public @NotNull List<Flag> flags() {
        return List.copyOf(flags);
    }

    /**
     * @return an immutable list of key-value definitions
     */
    public @NotNull List<KeyValue<?>> keyValues() {
        return List.copyOf(keyValues);
    }

    /**
     * @return the plugin instance this command is registered with, or null if not yet registered
     */
    public @Nullable JavaPlugin plugin() {
        return plugin;
    }

    SlashCommand(String name, List<String> aliases, String description, String permission, boolean playerOnly,
                 boolean sendErrors,
                 List<Arg<?>> args, CommandAction action, boolean async, boolean validateOnTab,
                 Map<String, SlashCommand> subcommands,
                 @Nullable AsyncCommandAction asyncActionAdv, long asyncTimeoutMillis,
                 List<Guard> guards, List<CrossArgumentValidator> crossArgumentValidators,
                 @Nullable ExceptionHandler exceptionHandler,
                 long perUserCooldownMillis, long perServerCooldownMillis, boolean enableHelp,
                 int helpPageSize, @Nullable MessageProvider messages, boolean sanitizeInputs,
                 boolean fuzzySubcommandMatching, double fuzzyMatchThreshold, boolean debugMode,
                 List<Flag> flags, List<KeyValue<?>> keyValues, boolean awaitConfirmation,
                 List<ExecutionHook.Before> beforeHooks, List<ExecutionHook.After> afterHooks) {
        this.name = Preconditions.checkNotNull(name, "name");
        this.aliases = List.copyOf(aliases == null ? List.of() : aliases);
        this.description = (description == null) ? "" : description;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.sendErrors = sendErrors;
        this.async = async;
        this.validateOnTab = validateOnTab;
        this.args = List.copyOf(Preconditions.checkNotNull(args, "args"));
        this.subcommands = Map.copyOf(Preconditions.checkNotNull(subcommands, "subcommands"));
        this.action = Preconditions.checkNotNull(action, "action");
        this.asyncActionAdv = asyncActionAdv;
        this.asyncTimeoutMillis = asyncTimeoutMillis;
        this.guards = List.copyOf(guards == null ? List.of() : guards);
        this.crossArgumentValidators = List.copyOf(
            crossArgumentValidators == null ? List.of() : crossArgumentValidators);
        this.exceptionHandler = exceptionHandler;
        this.perUserCooldownMillis = perUserCooldownMillis;
        this.perServerCooldownMillis = perServerCooldownMillis;
        this.enableHelp = enableHelp;
        this.helpPageSize = helpPageSize > 0 ? helpPageSize : 10; // default to 10 items per page
        this.messages = (messages != null) ? messages : new DefaultMessageProvider();
        this.sanitizeInputs = sanitizeInputs;
        this.fuzzySubcommandMatching = fuzzySubcommandMatching;
        this.fuzzyMatchThreshold = Math.max(0.0, Math.min(1.0, fuzzyMatchThreshold));
        this.debugMode = debugMode;
        this.flags = List.copyOf(flags == null ? List.of() : flags);
        this.keyValues = List.copyOf(keyValues == null ? List.of() : keyValues);
        this.awaitConfirmation = awaitConfirmation;
        this.beforeHooks = List.copyOf(beforeHooks == null ? List.of() : beforeHooks);
        this.afterHooks = List.copyOf(afterHooks == null ? List.of() : afterHooks);
        // Pre-compute usage string for performance
        this.cachedUsage = computeUsageString();
    }

    /**
     * Sanitizes a string input by removing or escaping potentially dangerous characters.
     * This helps prevent injection attacks (SQL, command, XSS) when processing user input.
     *
     * @param input the raw input string to sanitize
     * @return the sanitized string
     */
    private @NotNull String sanitizeString(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }

        StringBuilder result = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                // Remove control characters and null bytes
                case '\0':
                case '\u0001':
                case '\u0002':
                case '\u0003':
                case '\u0004':
                case '\u0005':
                case '\u0006':
                case '\u0007':
                case '\u0008':
                case '\u000B':
                case '\u000C':
                case '\u000E':
                case '\u000F':
                case '\u0010':
                case '\u0011':
                case '\u0012':
                case '\u0013':
                case '\u0014':
                case '\u0015':
                case '\u0016':
                case '\u0017':
                case '\u0018':
                case '\u0019':
                case '\u001A':
                case '\u001B':
                case '\u001C':
                case '\u001D':
                case '\u001E':
                case '\u001F':
                    // Skip control characters
                    break;
                // Escape HTML/XML special characters
                case '<':
                    result.append("&lt;");
                    break;
                case '>':
                    result.append("&gt;");
                    break;
                case '&':
                    result.append("&amp;");
                    break;
                case '"':
                    result.append("&quot;");
                    break;
                case '\'':
                    result.append("&#39;");
                    break;
                // Escape backslash
                case '\\':
                    result.append("\\\\");
                    break;
                // Allow tabs and newlines but normalize multiple spaces
                case '\t':
                case '\n':
                case '\r':
                    result.append(' ');
                    break;
                default:
                    result.append(c);
            }
        }

        // Trim and collapse multiple consecutive spaces
        return result.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Compute the usage string based on the configured arguments and subcommands.
     * Called once during construction to cache the result.
     */
    private @NotNull String computeUsageString() {
        if (!subcommands.isEmpty() && args.isEmpty()) {
            return "<subcommand>";
        }
        return args.stream()
            .map(a -> a.optional() ? ("[" + a.name() + "]") : ("<" + a.name() + ">"))
            .collect(Collectors.joining(" "));
    }

    /**
     * Bukkit command entry point. Delegates to {@link #execute(CommandSender, String, String[])} after
     * validating non-null parameters. This method should not be called directly; use
     * {@link #execute(CommandSender, String, String[])} for programmatic dispatch.
     *
     * @param sender       the command sender
     * @param command      the Bukkit command instance
     * @param label        the used label or alias
     * @param providedArgs raw arguments as provided by Bukkit
     * @return always true to indicate the command was handled
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(command, "command");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        try {
            return execute(sender, label, providedArgs);
        } catch (Throwable t) {
            // Top-level catch to ensure no exception escapes from command execution
            sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, messages.internalError(), t);
            if (plugin != null) {
                plugin.getLogger().severe("Unhandled exception in command '" + name + "': " + t.getMessage());
                logException(t);
            }
            return true;
        }
    }

    /**
     * Sends an error message to the sender, optionally invoking the exception handler.
     * This helper method encapsulates the repetitive pattern of checking the exception handler
     * and conditionally sending error messages.
     *
     * @param sender    the command sender to send the message to
     * @param errorType the type of error that occurred
     * @param message   the error message to send
     * @param exception optional exception that caused the error (can be null)
     */
    private void sendErrorMessage(@NotNull CommandSender sender, @NotNull ErrorType errorType,
                                  @NotNull String message, @Nullable Throwable exception) {
        boolean suppressDefault = false;
        if (exceptionHandler != null) {
            try {
                suppressDefault = exceptionHandler.handle(sender, errorType, message, exception);
            } catch (Throwable handlerException) {
                // Exception handler itself threw an exception - log it and continue with default behavior
                sender.sendMessage(messages.exceptionHandlerError(handlerException.getMessage()));
                if (plugin != null) {
                    plugin.getLogger()
                        .severe("Exception handler threw an exception while handling " + errorType + ": "
                                + handlerException.getMessage());
                    logException(handlerException);
                }
            }
        }
        if (sendErrors && !suppressDefault) {
            sender.sendMessage(message);
        }
    }

    /**
     * Unwrap an exception to find the root cause, properly traversing the exception chain.
     * Handles nested ExecutionException, CompletionException, and InvocationTargetException wrappers.
     *
     * @param throwable the exception to unwrap
     * @return the root cause exception
     */
    private static @NotNull Throwable unwrapException(@NotNull Throwable throwable) {
        Throwable current = throwable;
        java.util.Set<Throwable> seen = new java.util.HashSet<>(); // Prevent infinite loops
        while (current != null && seen.add(current)) {
            if (current instanceof java.util.concurrent.ExecutionException
                || current instanceof java.util.concurrent.CompletionException
                || current instanceof java.lang.reflect.InvocationTargetException) {
                Throwable cause = current.getCause();
                if (cause != null && cause != current) {
                    current = cause;
                    continue;
                }
            }
            break;
        }
        return current != null ? current : throwable;
    }

    /**
     * Logs an exception with its full stack trace using the plugin's logger.
     * This method provides robust logging by converting the stack trace to a string
     * and logging it through the plugin's logging system instead of printing to stderr.
     *
     * @param throwable the exception to log
     */
    private void logException(@NotNull Throwable throwable) {
        if (plugin == null) {
            return;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        // Split by newlines (handles \n, \r\n, and \r)
        for (String line : sw.toString().split("\\r?\\n|\\r")) {
            if (!line.isEmpty()) {
                plugin.getLogger().log(Level.SEVERE, line);
            }
        }
    }

    /**
     * Programmatically execute this command with the given label and arguments.
     * Useful for dispatching to a selected sub-command obtained via a choice argument
     * (e.g., using {@link SlashCommandBuilder#argCommandChoices(String, Map)}).
     *
     * @param sender       the command sender
     * @param label        the label used to execute the command
     * @param providedArgs the raw argument tokens as typed by the user
     * @return true to indicate the command was handled
     */
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sendErrorMessage(sender, ErrorType.PERMISSION, messages.noPermission(), null);
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            sendErrorMessage(sender, ErrorType.PLAYER_ONLY, messages.playerOnly(), null);
            return true;
        }

        // Guards
        for (Guard g : this.guards) {
            try {
                if (!g.test(sender)) {
                    sendErrorMessage(sender, ErrorType.GUARD_FAILED, g.errorMessage(), null);
                    return true;
                }
            } catch (Throwable t) {
                sendErrorMessage(sender, ErrorType.GUARD_FAILED, messages.guardFailed(), t);
                return true;
            }
        }

        // Cooldown checks
        CooldownResult serverCooldown = CooldownManager.checkServerCooldown(
            name, perServerCooldownMillis);
        if (serverCooldown.onCooldown()) {
            String formattedTime = CooldownManager.formatCooldownMessage("%s", serverCooldown.remainingMillis());
            sendErrorMessage(sender, ErrorType.GUARD_FAILED, messages.serverCooldown(formattedTime), null);
            return true;
        }

        CooldownResult userCooldown = CooldownManager.checkUserCooldown(
            name, sender.getName(), perUserCooldownMillis);
        if (userCooldown.onCooldown()) {
            String formattedTime = CooldownManager.formatCooldownMessage("%s", userCooldown.remainingMillis());
            sendErrorMessage(sender, ErrorType.GUARD_FAILED, messages.userCooldown(formattedTime), null);
            return true;
        }

        // Confirmation check: if awaitConfirmation is enabled, require the user to send the command twice
        // Uses atomic operations to prevent race conditions in concurrent scenarios
        if (awaitConfirmation) {
            String confirmationKey = name + ":" + sender.getName();
            final long currentTime = System.currentTimeMillis();
            final long newExpiry = currentTime + CONFIRMATION_TIMEOUT_MILLIS;

            // Clean up expired confirmations on every check (was too infrequent before)
            pendingConfirmations.entrySet().removeIf(entry -> entry.getValue() < currentTime);

            // Use compute for atomic check-and-update to prevent race conditions
            // Returns null if confirmation was valid and consumed, non-null if new pending was created
            final boolean[] needsConfirmation = {false};
            pendingConfirmations.compute(confirmationKey, (key, existingExpiry) -> {
                if (existingExpiry != null && existingExpiry >= currentTime) {
                    // Valid confirmation exists - consume it (return null to remove)
                    return null;
                } else {
                    // No valid confirmation - create new pending confirmation
                    needsConfirmation[0] = true;
                    return newExpiry;
                }
            });

            if (needsConfirmation[0]) {
                // First execution - ask for confirmation
                sendErrorMessage(sender, ErrorType.GUARD_FAILED, messages.awaitConfirmation(), null);
                return true;
            }
            // Valid confirmation was consumed - proceed with execution
        }

        // Auto help: display help message when enabled and no arguments provided
        if (enableHelp && providedArgs.length == 0) {
            // Show help if command has subcommands or required arguments
            int required = (int) args.stream().filter(a -> !a.optional()).count();
            if (!subcommands.isEmpty() || required > 0) {
                generateHelpMessage(label, 1, sender);
                return true;
            }
        }

        // Automatic subcommand routing: if the first token matches a registered subcommand, delegate to it
        if (!subcommands.isEmpty() && providedArgs.length >= 1) {
            String first = providedArgs[0].toLowerCase(Locale.ROOT);
            SlashCommand sub = subcommands.get(first);

            // Check if first argument is a page number for help pagination
            if (sub == null && enableHelp) {
                try {
                    int pageNum = Integer.parseInt(first);
                    if (pageNum >= 1) {
                        generateHelpMessage(label, pageNum, sender);
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a number, continue with normal processing
                }
            }

            // Fuzzy matching: if no exact match found and fuzzy matching is enabled, try to find a similar subcommand
            if (sub == null && fuzzySubcommandMatching) {
                List<String> subcommandNames = new ArrayList<>(subcommands.keySet());
                List<String> similar = StringSimilarity.findSimilar(first, subcommandNames, 1, fuzzyMatchThreshold);
                if (!similar.isEmpty()) {
                    sub = subcommands.get(similar.get(0));
                    // Log fuzzy match for audit purposes in debug mode
                    if (debugMode && plugin != null) {
                        plugin.getLogger().info("[Fuzzy Match] '" + first + "' matched to '" + similar.get(0) + "'");
                    }
                }
            }

            if (sub != null) {
                // Safety check: ensure we have arguments to pass
                String[] remaining = providedArgs.length > 1
                    ? Arrays.copyOfRange(providedArgs, 1, providedArgs.length)
                    : new String[0];
                try {
                    return sub.execute(sender, sub.name(), remaining);
                } catch (Throwable t) {
                    // Catch any unexpected exception during subcommand execution
                    String errorMsg = messages.subcommandInternalError(first);
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger()
                            .severe("Subcommand '" + first + "' threw unexpected exception: " + t.getMessage());
                        logException(t);
                    }
                    return true;
                }
            }
            // Invalid subcommand provided: show help if enabled
            if (enableHelp) {
                generateHelpMessage(label, 1, sender);
                return true;
            }
        }

        // Parse flags and key-value pairs from provided arguments FIRST
        Map<String, Boolean> flagValues = Collections.emptyMap();
        Map<String, Object> keyValuePairs = Collections.emptyMap();
        Map<String, List<Object>> multiValuePairs = Collections.emptyMap();
        String[] positionalArgs = providedArgs;
        
        if (!flags.isEmpty() || !keyValues.isEmpty()) {
            FlagAndKeyValueParser flagKvParser = new FlagAndKeyValueParser(flags, keyValues);
            FlagAndKeyValueParser.ParsedResult flagKvResult = flagKvParser.parse(providedArgs, sender);

            if (!flagKvResult.isSuccess()) {
                // Report first error from flag/key-value parsing
                String firstError = !flagKvResult.errors().isEmpty()
                    ? flagKvResult.errors().get(0)
                    : "Unknown parsing error";
                sendErrorMessage(sender, ErrorType.PARSING, messages.invalidArgumentValue("flags/options", "flag", firstError), null);
                return true;
            }

            flagValues = flagKvResult.flagValues();
            keyValuePairs = flagKvResult.keyValuePairs();
            multiValuePairs = flagKvResult.multiValuePairs();

            // Apply input sanitization to string values in key-value pairs if enabled
            if (sanitizeInputs) {
                Map<String, Object> sanitizedKvPairs = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : keyValuePairs.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        sanitizedKvPairs.put(entry.getKey(), sanitizeString((String) value));
                    } else {
                        sanitizedKvPairs.put(entry.getKey(), value);
                    }
                }
                keyValuePairs = sanitizedKvPairs;

                // Also sanitize multi-value pairs
                Map<String, List<Object>> sanitizedMultiPairs = new LinkedHashMap<>();
                for (Map.Entry<String, List<Object>> entry : multiValuePairs.entrySet()) {
                    List<Object> sanitizedList = new ArrayList<>();
                    for (Object value : entry.getValue()) {
                        if (value instanceof String) {
                            sanitizedList.add(sanitizeString((String) value));
                        } else {
                            sanitizedList.add(value);
                        }
                    }
                    sanitizedMultiPairs.put(entry.getKey(), sanitizedList);
                }
                multiValuePairs = sanitizedMultiPairs;
            }

            // Use remaining args (after extracting flags/key-values) for positional argument parsing
            positionalArgs = flagKvResult.remainingArgs().toArray(new String[0]);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();

        // NOTE: Cannot validate required arg count upfront because:
        // 1. Conditional arguments might be skipped
        // 2. Permission-gated arguments might not be accessible
        // Instead, we validate after parsing and check for missing required args

        // Parse arguments using arg index and token index (support greedy last argument)
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < positionalArgs.length) {
            Arg<?> arg = args.get(argIndex);

            // Evaluate conditional argument
            if (arg.condition() != null) {
                // Include flags and key-values in the context for condition evaluation
                CommandContext tempCtx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs);
                try {
                    if (!arg.condition().test(tempCtx)) {
                        // Condition is false, skip this argument entirely (don't consume token)
                        argIndex++;
                        continue;
                    }
                } catch (Throwable t) {
                    String errorMsg = messages.argumentConditionError(arg.name());
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger()
                            .severe("Condition evaluation failed for argument '" + arg.name() + "': " + t.getMessage());
                        logException(t);
                    }
                    return true;
                }
            }

            // Per-argument permission check
            if (arg.permission() != null && !arg.permission().isEmpty() && !sender.hasPermission(arg.permission())) {
                // If a required argument is permission-gated and user lacks permission, fail
                if (!arg.optional()) {
                    sendErrorMessage(
                        sender, ErrorType.ARGUMENT_PERMISSION,
                        messages.argumentPermissionDenied(arg.name()), null
                    );
                    return true;
                }
                // Optional arg without permission: skip it
                argIndex++;
                continue;
            }
            ArgumentParser<?> parser = arg.parser();
            String token;

            // Check if this is the last argument to be parsed (accounting for conditionals and permissions after it)
            boolean isLastArgToBeParsed = true;
            for (int checkIdx = argIndex + 1; checkIdx < args.size(); checkIdx++) {
                Arg<?> futureArg = args.get(checkIdx);

                // Check if future arg will be skipped by condition
                boolean willBeSkippedByCondition = false;
                if (futureArg.condition() != null) {
                    try {
                        CommandContext tempCtx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs);
                        willBeSkippedByCondition = !futureArg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                        // If we can't evaluate, assume it won't be skipped
                    }
                }

                // Check if future arg will be skipped by permission
                boolean willBeSkippedByPermission = futureArg.permission() != null
                    && !futureArg.permission().isEmpty()
                    && !sender.hasPermission(futureArg.permission())
                    && futureArg.optional();

                // If this future arg will actually be parsed, current is not the last
                if (!willBeSkippedByCondition && !willBeSkippedByPermission) {
                    isLastArgToBeParsed = false;
                    break;
                }
            }

            if (isLastArgToBeParsed && arg.greedy()) {
                // Safety check for greedy argument token index
                if (tokenIndex >= positionalArgs.length) {
                    token = "";
                } else {
                    token = String.join(" ", Arrays.asList(positionalArgs).subList(tokenIndex, positionalArgs.length));
                }
                tokenIndex = positionalArgs.length; // consume all remaining tokens
            } else {
                token = positionalArgs[tokenIndex++];
            }
            ParseResult<?> res;
            try {
                res = parser.parse(token, sender);
                if (res == null) {
                    throw new ParsingException(
                        "Parser " + parser.getClass().getName() + " returned null ParseResult for argument '"
                        + arg.name()
                        + "'");
                }
            } catch (ParsingException pe) {
                // Re-throw ParsingException as it indicates a developer error
                throw pe;
            } catch (Throwable t) {
                // Catch any unexpected exception from parser and handle gracefully
                String errorMsg = messages.argumentParsingError(arg.name());
                sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                if (plugin != null) {
                    plugin.getLogger()
                        .severe("Parser " + parser.getClass().getName() + " threw unexpected exception for argument '"
                                + arg.name() + "': " + t.getMessage());
                    logException(t);
                }
                return true;
            }
            if (!res.isSuccess()) {
                String msg = res.error().orElse("invalid value");
                String errorMsg = messages.invalidArgumentValue(arg.name(), parser.getTypeName(), msg);
                sendErrorMessage(sender, ErrorType.PARSING, errorMsg, null);

                // Did-You-Mean suggestions (only shown if main error was sent)
                if (sendErrors) {
                    ArgContext argCtx = arg.context();
                    if (argCtx.didYouMean() && !argCtx.completionsPredefined().isEmpty()) {
                        try {
                            List<String> suggestions = StringSimilarity.findSimilar(
                                token, argCtx.completionsPredefined());
                            if (!suggestions.isEmpty()) {
                                sender.sendMessage(messages.didYouMean(String.join(", ", suggestions)));
                            }
                        } catch (Throwable t) {
                            // Silently ignore errors in suggestion generation - it's non-critical
                            if (plugin != null) {
                                plugin.getLogger()
                                    .warning("Failed to generate 'Did you mean' suggestions for argument '" + arg.name()
                                             + "': " + t.getMessage());
                            }
                        }
                    }
                }
                return true;
            }
            Object parsedValue = res.value().orElse(null);

            // Apply input sanitization for string values if enabled
            if (sanitizeInputs && parsedValue instanceof String) {
                parsedValue = sanitizeString((String) parsedValue);
            }

            // Apply transformation if transformer is present
            if (arg.transformer() != null && parsedValue != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Function<Object, Object> transformer = (Function<Object, Object>) arg.transformer();
                    parsedValue = transformer.apply(parsedValue);
                } catch (Throwable t) {
                    String errorMsg = messages.argumentTransformationError(arg.name());
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger()
                            .severe("Transformation failed for argument '" + arg.name() + "': " + t.getMessage());
                        logException(t);
                    }
                    return true;
                }
            }

            // Apply validations from ArgContext
            ArgContext ctx = arg.context();
            String validationError;
            try {
                validationError = ValidationHelper.validateValue(
                    parsedValue, ctx, arg.name(), parser.getTypeName(), messages);
            } catch (Throwable t) {
                // Catch any unexpected exception during validation
                String errorMsg = messages.argumentValidationError(arg.name());
                sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                if (plugin != null) {
                    plugin.getLogger()
                        .severe("Validation failed with unexpected exception for argument '" + arg.name() + "': "
                                + t.getMessage());
                    logException(t);
                }
                return true;
            }
            if (validationError != null) {
                sendErrorMessage(
                    sender, ErrorType.VALIDATION,
                    messages.validationFailed(arg.name(), validationError), null
                );
                return true;
            }

            values.put(arg.name(), parsedValue);
            argIndex++;
        }

        // Validate that all required arguments were provided (accounting for conditions and permissions)
        for (Arg<?> arg : args) {
            if (!arg.optional() && !values.containsKey(arg.name())) {
                // Check if this arg was skipped due to condition
                boolean skippedByCondition = false;
                if (arg.condition() != null) {
                    try {
                        CommandContext tempCtx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs);
                        skippedByCondition = !arg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                        // If condition evaluation fails here, we already handled it during parsing
                    }
                }

                // Check if this arg was skipped due to permission
                // Note: Required args with missing permission should have already failed during parsing (line 699-704)
                // This should never be true for required args, but we check for safety
                boolean skippedByPermission = false;
                if (arg.optional() && arg.permission() != null && !arg.permission().isEmpty()
                    && !sender.hasPermission(arg.permission())) {
                    skippedByPermission = true;
                }

                // If not skipped by condition or permission, it's truly missing
                if (!skippedByCondition && !skippedByPermission) {
                    sendErrorMessage(
                        sender, ErrorType.USAGE,
                        messages.insufficientArguments(fullCommandPath(label), usage()), null);
                    return true;
                }
            }
        }

        // Apply default values for missing optional arguments
        for (Arg<?> arg : args) {
            if (!values.containsKey(arg.name()) && arg.context().defaultValue() != null) {
                values.put(arg.name(), arg.context().defaultValue());
            }
        }

        // Check for extra arguments after parsing (handles optional args case)
        if (!lastIsGreedy && tokenIndex < positionalArgs.length) {
            sendErrorMessage(sender, ErrorType.USAGE, messages.tooManyArguments(fullCommandPath(label), usage()), null);
            return true;
        }

        // Cross-argument validation: validate relationships between multiple arguments
        if (!crossArgumentValidators.isEmpty()) {
            CommandContext tempCtx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs);
            for (CrossArgumentValidator validator : crossArgumentValidators) {
                String error;
                try {
                    error = validator.validate(tempCtx);
                } catch (Throwable t) {
                    // Catch any unexpected exception during cross-argument validation
                    String errorMsg = messages.crossValidationInternalError();
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger()
                            .severe("Cross-argument validator " + validator.getClass().getName()
                                    + " threw unexpected exception: " + t.getMessage());
                        logException(t);
                    }
                    return true;
                }
                if (error != null) {
                    sendErrorMessage(sender, ErrorType.CROSS_VALIDATION, messages.crossValidationFailed(error), null);
                    return true;
                }
            }
        }

        // Update cooldown tracking after successful validation
        if (perServerCooldownMillis > 0) {
            try {
                CooldownManager.updateServerCooldown(name);
            } catch (Throwable t) {
                // Log but don't fail the command if cooldown update fails
                if (plugin != null) {
                    plugin.getLogger()
                        .warning("Failed to update server cooldown for command '" + name + "': " + t.getMessage());
                }
            }
        }
        if (perUserCooldownMillis > 0) {
            try {
                CooldownManager.updateUserCooldown(name, sender.getName());
            } catch (Throwable t) {
                // Log but don't fail the command if cooldown update fails
                if (plugin != null) {
                    plugin.getLogger()
                        .warning(
                            "Failed to update user cooldown for command '" + name + "' and user '" + sender.getName()
                            + "': " + t.getMessage());
                }
            }
        }

        // Optional arguments not provided: simply absent from context

        CommandContext ctx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs);

        // Execute before hooks
        for (ExecutionHook.Before beforeHook : beforeHooks) {
            ExecutionHook.BeforeResult beforeResult;
            try {
                beforeResult = beforeHook.execute(sender, ctx);
            } catch (Throwable t) {
                String errorMsg = messages.internalError();
                sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                if (plugin != null) {
                    plugin.getLogger()
                        .severe("Before hook threw an exception for command '" + name + "': " + t.getMessage());
                    logException(t);
                }
                return true;
            }
            if (!beforeResult.shouldProceed()) {
                // Before hook aborted execution
                if (beforeResult.errorMessage() != null) {
                    sendErrorMessage(sender, ErrorType.GUARD_FAILED, beforeResult.errorMessage(), null);
                }
                return true;
            }
        }

        // Measure execution time for after hooks
        long startTime = System.currentTimeMillis();
        boolean executionSuccess = false;
        Throwable executionException = null;

        if (async) {
            if (plugin == null) {
                // Cannot run async without plugin - fall back to sync execution
                Throwable executionError = null;
                try {
                    action.execute(sender, ctx);
                } catch (Throwable t) {
                    executionError = (t.getCause() != null) ? t.getCause() : t;
                    sendErrorMessage(sender, ErrorType.EXECUTION, messages.executionError(), executionError);
                }
                long executionTime = System.currentTimeMillis() - startTime;
                ExecutionHook.AfterContext afterContext = (executionError == null)
                    ? ExecutionHook.AfterContext.success(executionTime)
                    : ExecutionHook.AfterContext.failure(executionError, executionTime);
                runAfterHooks(sender, ctx, afterContext);
                if (executionError != null) {
                    throw new CommandExecutionException("Error executing command '" + name + "'", executionError);
                }
            } else if (asyncActionAdv != null) {
                // Advanced async with cancellation, progress, and timeout using Bukkit scheduler
                final CancellationToken token = new CancellationToken();
                final Progress progress = (msg) -> {
                    try {
                        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                    } catch (Throwable t) {
                        plugin.getLogger().warning(
                            "Failed to send progress message for command '" + name + "': " + t.getMessage());
                    }
                };

                // Use Bukkit's async scheduler instead of CompletableFuture
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Throwable executionError = null;
                    boolean timedOut = false;

                    try {
                        // Periodically check for timeout if configured
                        if (asyncTimeoutMillis > 0L) {
                            // Execute with timeout awareness - action should check token.isCancelled()
                            asyncActionAdv.execute(sender, ctx, token, progress);

                            // Check if we exceeded timeout after execution
                            if (System.currentTimeMillis() - startTime > asyncTimeoutMillis) {
                                timedOut = true;
                                token.cancel();
                            }
                        } else {
                            asyncActionAdv.execute(sender, ctx, token, progress);
                        }
                    } catch (Throwable t) {
                        executionError = (t.getCause() != null) ? t.getCause() : t;
                    }

                    // Run after-hooks
                    final long executionTime = System.currentTimeMillis() - startTime;
                    final Throwable finalError = executionError;
                    final boolean finalTimedOut = timedOut;

                    ExecutionHook.AfterContext afterContext = (finalError == null && !finalTimedOut)
                        ? ExecutionHook.AfterContext.success(executionTime)
                        : ExecutionHook.AfterContext.failure(
                            finalError != null ? finalError : new TimeoutException("Command timed out"),
                            executionTime);
                    runAfterHooks(sender, ctx, afterContext);

                    // Handle errors - send messages on main thread
                    if (finalTimedOut || finalError != null) {
                        final String errorMsg = finalTimedOut
                            ? messages.commandTimeout(asyncTimeoutMillis)
                            : messages.executionError();
                        final ErrorType errorType = finalTimedOut ? ErrorType.TIMEOUT : ErrorType.EXECUTION;
                        final Throwable errorCause = finalTimedOut
                            ? new TimeoutException("Command timed out")
                            : finalError;

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            boolean suppressDefault = false;
                            if (exceptionHandler != null) {
                                try {
                                    suppressDefault = exceptionHandler.handle(sender, errorType, errorMsg, errorCause);
                                } catch (Throwable handlerException) {
                                    sender.sendMessage(messages.exceptionHandlerError(handlerException.getMessage()));
                                    plugin.getLogger().severe("Exception handler threw an exception while handling "
                                        + errorType + ": " + handlerException.getMessage());
                                    logException(handlerException);
                                }
                            }
                            if (sendErrors && !suppressDefault) {
                                sender.sendMessage(errorMsg);
                            }
                        });

                        if (finalError != null) {
                            plugin.getLogger().severe("Error executing command '" + name + "' asynchronously: "
                                + finalError.getMessage());
                            logException(finalError);
                        }
                    }
                });
            } else {
                // Simple async execution using Bukkit scheduler
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Throwable executionError = null;
                    try {
                        action.execute(sender, ctx);
                    } catch (Throwable t) {
                        executionError = (t.getCause() != null) ? t.getCause() : t;
                        final Throwable finalError = executionError;
                        final String errorMsg = messages.executionError();

                        // Send error message on main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            boolean suppressDefault = false;
                            if (exceptionHandler != null) {
                                try {
                                    suppressDefault = exceptionHandler.handle(sender, ErrorType.EXECUTION, errorMsg,
                                        finalError);
                                } catch (Throwable handlerException) {
                                    sender.sendMessage(messages.exceptionHandlerError(handlerException.getMessage()));
                                    plugin.getLogger().severe(
                                        "Exception handler threw an exception while handling EXECUTION: "
                                            + handlerException.getMessage());
                                    logException(handlerException);
                                }
                            }
                            if (sendErrors && !suppressDefault) {
                                sender.sendMessage(errorMsg);
                            }
                        });

                        plugin.getLogger().severe(
                            "Error executing command '" + name + "' asynchronously: " + finalError.getMessage());
                        logException(finalError);
                    }

                    // Run after-hooks
                    final long executionTime = System.currentTimeMillis() - startTime;
                    final Throwable finalExecutionError = executionError;
                    ExecutionHook.AfterContext afterContext = (finalExecutionError == null)
                        ? ExecutionHook.AfterContext.success(executionTime)
                        : ExecutionHook.AfterContext.failure(finalExecutionError, executionTime);
                    runAfterHooks(sender, ctx, afterContext);
                });
            }
        } else {
            // Synchronous execution with after-hooks
            Throwable executionError = null;
            try {
                action.execute(sender, ctx);
                executionSuccess = true;
            } catch (Throwable t) {
                executionSuccess = false;
                executionException = (t.getCause() != null) ? t.getCause() : t;
                sendErrorMessage(
                    sender, ErrorType.EXECUTION,
                    messages.executionError(), executionException
                );
            } finally {
                // Execute after hooks for synchronous execution
                long executionTime = System.currentTimeMillis() - startTime;
                ExecutionHook.AfterContext afterContext = executionSuccess
                    ? ExecutionHook.AfterContext.success(executionTime)
                    : ExecutionHook.AfterContext.failure(executionException, executionTime);

                for (ExecutionHook.After afterHook : afterHooks) {
                    try {
                        afterHook.execute(sender, ctx, afterContext);
                    } catch (Throwable t) {
                        // Log but don't fail the command if after hook fails
                        if (plugin != null) {
                            plugin.getLogger()
                                .warning("After hook threw an exception for command '" + name + "': " + t.getMessage());
                            logException(t);
                        }
                    }
                }
            }

            // Re-throw if execution failed
            if (!executionSuccess && executionException != null) {
                throw new CommandExecutionException(
                    "Error executing command '" + name + "'", executionException);
            }
        }
        return true;
    }

    /**
     * Returns the cached usage string based on the configured arguments.
     * The usage string is pre-computed during construction for performance.
     * Package-private for TabCompletionHandler access.
     */
    @NotNull
    public String usage() {
        return cachedUsage;
    }

    /**
     * Categorizes a command based on its argument requirements.
     * Returns a sorting key:
     * 0 = no arguments
     * 1 = only optional arguments
     * 2 = both optional and required arguments
     * 3 = only required arguments
     *
     * @param command the command to categorize
     * @return the category key for sorting
     */
    private int categorizeByArguments(@NotNull SlashCommand command) {
        List<Arg<?>> args = command.args();

        if (args.isEmpty()) {
            return 0; // No arguments - first
        }

        boolean hasOptional = false;
        boolean hasRequired = false;

        for (Arg<?> arg : args) {
            if (arg.optional()) {
                hasOptional = true;
            } else {
                hasRequired = true;
            }
        }

        if (hasRequired && hasOptional) {
            return 2; // Both optional and required - between
        } else if (hasOptional) {
            return 1; // Only optional - second
        } else {
            return 3; // Only required - last
        }
    }

    /**
     * Generates a dynamic help message for this command.
     * If the command has subcommands, displays a paginated list of available subcommands with their usage.
     * If the command has no subcommands, displays the usage format for this command.
     *
     * @param label      the command label used to invoke this command
     * @param pageNumber the page number to display (1-based)
     * @param sender     the command sender to send messages to
     */
    private void generateHelpMessage(@NotNull String label, int pageNumber, @NotNull CommandSender sender) {
        String commandPath = fullCommandPath(label);

        if (!subcommands.isEmpty()) {
            // Format command name: first letter uppercase, rest lowercase
            String formattedName = name.isEmpty() ? "" :
                Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);

            // Get unique subcommands (since aliases point to the same command)
            Set<SlashCommand> uniqueSubcommands = new LinkedHashSet<>(subcommands.values());

            // Sort subcommands by argument requirements:
            // 1. No arguments first
            // 2. Optional arguments only
            // 3. Both optional and required
            // 4. Required arguments only last
            List<SlashCommand> sortedSubcommands = new ArrayList<>(uniqueSubcommands);
            sortedSubcommands.sort(Comparator.comparingInt(this::categorizeByArguments));

            // Use pagination for subcommand listing
            PaginationConfig config = PaginationConfig.builder()
                .pageSize(helpPageSize)
                .build();

            PaginationHelper.paginate(sortedSubcommands)
                .page(pageNumber)
                .pageSize(helpPageSize)
                .config(config)
                .messageProvider(messages)
                .header(messages.helpSubCommandsHeader(formattedName, commandPath).trim())
                .formatter(sub -> {
                    StringBuilder line = new StringBuilder();
                    line.append(messages.helpSubCommandPrefix(sub.name()));
                    String subUsage = sub.usage();
                    if (!subUsage.isEmpty() && !subUsage.equals("<subcommand>")) {
                        line.append(messages.helpUsageSeparator()).append(subUsage);
                    }
                    if (!sub.description().isEmpty()) {
                        line.append(messages.helpDescriptionSeparator()).append(sub.description());
                    }
                    return line.toString();
                })
                .showNavigation(true)
                .showPageOverview(true)
                .commandBase("/" + commandPath)
                .send(sender);
        } else {
            // Enhanced help for commands without subcommands
            generateDetailedHelp(commandPath, sender);
        }
    }

    /**
     * Generates detailed help including arguments, flags, and key-values.
     * Permission-aware: only shows options the sender has permission to use.
     *
     * @param commandPath the full command path
     * @param sender      the command sender
     */
    private void generateDetailedHelp(@NotNull String commandPath, @NotNull CommandSender sender) {
        StringBuilder help = new StringBuilder();

        // Build permission-aware usage line
        String usageStr = buildPermissionAwareUsage(sender);
        help.append(messages.helpUsage(commandPath, usageStr));

        // Description
        if (!description.isEmpty()) {
            help.append("\n").append(messages.helpDescriptionSeparator()).append(description);
        }

        // Arguments section - filter by permission
        List<Arg<?>> visibleArgs = new ArrayList<>();
        for (Arg<?> arg : args) {
            if (arg.permission() == null || sender.hasPermission(arg.permission())) {
                visibleArgs.add(arg);
            }
        }

        if (!visibleArgs.isEmpty()) {
            help.append("\n\n§e§lArguments:");
            for (Arg<?> arg : visibleArgs) {
                help.append("\n  §7");
                if (arg.optional()) {
                    help.append("[").append(arg.name()).append("]");
                } else {
                    help.append("<").append(arg.name()).append(">");
                }
                help.append(" §8- §f").append(arg.parser().getTypeName());
                if (arg.description() != null && !arg.description().isEmpty()) {
                    help.append(" §7- ").append(arg.description());
                }
            }
        }

        // Flags section - filter by permission
        List<Flag> visibleFlags = new ArrayList<>();
        for (Flag flag : flags) {
            if (flag.permission() == null || sender.hasPermission(flag.permission())) {
                visibleFlags.add(flag);
            }
        }

        if (!visibleFlags.isEmpty()) {
            help.append("\n\n§e§lFlags:");
            for (Flag flag : visibleFlags) {
                help.append("\n  §7");
                if (flag.shortForm() != null) {
                    help.append("-").append(flag.shortForm());
                    if (flag.longForm() != null) {
                        help.append(", ");
                    }
                }
                if (flag.longForm() != null) {
                    help.append("--").append(flag.longForm());
                }
                if (flag.description() != null && !flag.description().isEmpty()) {
                    help.append(" §8- §f").append(flag.description());
                }
            }
        }

        // Key-Values section - filter by permission
        List<KeyValue<?>> visibleKeyValues = new ArrayList<>();
        for (KeyValue<?> kv : keyValues) {
            if (kv.permission() == null || sender.hasPermission(kv.permission())) {
                visibleKeyValues.add(kv);
            }
        }

        if (!visibleKeyValues.isEmpty()) {
            help.append("\n\n§e§lOptions:");
            for (KeyValue<?> kv : visibleKeyValues) {
                help.append("\n  §7--").append(kv.key()).append("=<value>");
                if (!kv.required()) {
                    if (kv.defaultValue() != null) {
                        help.append(" §8(default: ").append(kv.defaultValue()).append(")");
                    } else {
                        help.append(" §8(optional)");
                    }
                } else {
                    help.append(" §c(required)");
                }
                if (kv.description() != null && !kv.description().isEmpty()) {
                    help.append("\n    §f").append(kv.description());
                }
            }
        }

        sender.sendMessage(help.toString());
    }

    /**
     * Build a permission-aware usage string showing only arguments the sender can use.
     *
     * @param sender the command sender
     * @return usage string filtered by permissions
     */
    private @NotNull String buildPermissionAwareUsage(@NotNull CommandSender sender) {
        if (!subcommands.isEmpty()) {
            return "<subcommand>";
        }

        StringBuilder usage = new StringBuilder();
        for (Arg<?> arg : args) {
            // Skip args the sender doesn't have permission for
            if (arg.permission() != null && !sender.hasPermission(arg.permission())) {
                continue;
            }

            if (usage.length() > 0) {
                usage.append(" ");
            }

            if (arg.optional()) {
                usage.append("[").append(arg.name()).append("]");
            } else {
                usage.append("<").append(arg.name()).append(">");
            }
        }

        // Add visible flags hint
        boolean hasVisibleFlags = false;
        for (Flag flag : flags) {
            if (flag.permission() == null || sender.hasPermission(flag.permission())) {
                hasVisibleFlags = true;
                break;
            }
        }
        if (hasVisibleFlags) {
            if (usage.length() > 0) usage.append(" ");
            usage.append("[flags...]");
        }

        // Add visible key-values hint
        boolean hasVisibleKeyValues = false;
        for (KeyValue<?> kv : keyValues) {
            if (kv.permission() == null || sender.hasPermission(kv.permission())) {
                hasVisibleKeyValues = true;
                break;
            }
        }
        if (hasVisibleKeyValues) {
            if (usage.length() > 0) usage.append(" ");
            usage.append("[options...]");
        }

        return usage.length() > 0 ? usage.toString() : "";
    }

    // ==================== Execution Hook Helpers ====================

    /**
     * Runs all registered before-execution hooks.
     *
     * @param sender  the command sender
     * @param context the parsed command context
     * @return the result from the first hook that aborts, or a "proceed" result if all pass
     */
    private @NotNull ExecutionHook.BeforeResult runBeforeHooks(@NotNull CommandSender sender,
                                                                @NotNull CommandContext context) {
        if (beforeHooks.isEmpty()) {
            return ExecutionHook.BeforeResult.proceed();
        }
        for (ExecutionHook.Before hook : beforeHooks) {
            try {
                ExecutionHook.BeforeResult result = hook.execute(sender, context);
                if (result == null || !result.shouldProceed()) {
                    // Hook aborted - return the result (or an abort with no message if null)
                    return result != null ? result : ExecutionHook.BeforeResult.abort();
                }
            } catch (Throwable t) {
                // Log hook failure but don't abort - hooks shouldn't break commands
                if (plugin != null) {
                    plugin.getLogger().warning("Before-execution hook threw exception: " + t.getMessage());
                    logException(t);
                }
            }
        }
        return ExecutionHook.BeforeResult.proceed();
    }

    /**
     * Runs all registered after-execution hooks.
     *
     * @param sender  the command sender
     * @param context the parsed command context
     * @param result  the execution result context
     */
    private void runAfterHooks(@NotNull CommandSender sender, @NotNull CommandContext context,
                               @NotNull ExecutionHook.AfterContext result) {
        if (afterHooks.isEmpty()) {
            return;
        }
        for (ExecutionHook.After hook : afterHooks) {
            try {
                hook.execute(sender, context, result);
            } catch (Throwable t) {
                // Log hook failure but continue with other hooks
                if (plugin != null) {
                    plugin.getLogger().warning("After-execution hook threw exception: " + t.getMessage());
                    logException(t);
                }
            }
        }
    }

    /**
     * Provide tab-completion options for the current argument token.
     * Respects command-level and per-argument permissions, and can optionally
     * validate previously typed tokens when {@link SlashCommandBuilder#validateOnTab(boolean)} is enabled.
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] providedArgs) {
        try {
            return TabCompletionHandler.generateCompletions(sender, alias, providedArgs, this, messages);
        } catch (Throwable t) {
            // Top-level catch to ensure no exception escapes from tab completion
            if (plugin != null) {
                plugin.getLogger()
                    .severe("Unhandled exception in tab completion for command '" + name + "': " + t.getMessage());
                logException(t);
            }
            return Collections.emptyList();
        }
    }
}
