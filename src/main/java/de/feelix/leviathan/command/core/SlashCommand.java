package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.argument.ArgumentGroup;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.command.suggestion.SuggestionEngine;
import de.feelix.leviathan.command.interactive.InteractivePrompt;
import de.feelix.leviathan.command.parsing.CommandParseError;
import de.feelix.leviathan.command.parsing.CommandParseResult;
import de.feelix.leviathan.command.parsing.ParseOptions;
import de.feelix.leviathan.command.parsing.PartialParseOptions;
import de.feelix.leviathan.command.parsing.PartialParseResult;
import de.feelix.leviathan.command.parsing.QuotedStringTokenizer;
import de.feelix.leviathan.command.permission.PermissionCascadeMode;
import de.feelix.leviathan.command.permission.PermissionCascade;
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
import java.util.regex.Pattern;
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

    // Lazy cleanup for confirmations
    private static final java.util.concurrent.atomic.AtomicLong confirmationOpCount = new java.util.concurrent.atomic.AtomicLong(0);
    private static final int CONFIRMATION_CLEANUP_INTERVAL = 20; // Clean every N operations

    // Cached regex pattern for whitespace normalization (avoids recompilation on every call)
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Clean up expired confirmation entries.
     * Called automatically via lazy cleanup, but can also be called manually.
     *
     * @return the number of expired entries removed
     */
    public static int cleanupExpiredConfirmations() {
        final long currentTime = System.currentTimeMillis();
        int[] removed = {0};
        pendingConfirmations.entrySet().removeIf(entry -> {
            if (entry.getValue() < currentTime) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    /**
     * Get the number of pending confirmations.
     * Useful for monitoring and diagnostics.
     *
     * @return the count of pending confirmations
     */
    public static int getPendingConfirmationCount() {
        return pendingConfirmations.size();
    }

    /**
     * Clear all pending confirmations.
     * Use with caution - primarily for plugin shutdown.
     */
    public static void clearAllConfirmations() {
        pendingConfirmations.clear();
    }

    /**
     * Clear pending confirmation for a specific sender.
     * Useful when a player disconnects.
     *
     * @param senderName the name of the sender to clear confirmations for
     * @return the number of confirmations cleared
     */
    public static int clearConfirmationsForSender(String senderName) {
        int[] removed = {0};
        pendingConfirmations.entrySet().removeIf(entry -> {
            if (entry.getKey().endsWith(":" + senderName)) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    /**
     * Perform lazy cleanup of expired confirmations.
     */
    private static void lazyConfirmationCleanup() {
        long ops = confirmationOpCount.incrementAndGet();
        if (ops % CONFIRMATION_CLEANUP_INTERVAL == 0) {
            cleanupExpiredConfirmations();
        }
    }

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
    private final Map<String, String> cachedAliasMap;
    private final MessageProvider messages;
    private final boolean sanitizeInputs;
    private final boolean fuzzySubcommandMatching;
    private final double fuzzyMatchThreshold;
    private final boolean debugMode;
    final List<Flag> flags;
    final List<KeyValue<?>> keyValues;
    // Cached parser instance to avoid rebuilding HashMap caches on every execute()
    private final FlagAndKeyValueParser cachedFlagKvParser;
    private final boolean awaitConfirmation;
    private final List<ExecutionHook.Before> beforeHooks;
    private final List<ExecutionHook.After> afterHooks;
    private final List<ArgumentGroup> argumentGroups;
    private final boolean enableQuotedStrings;
    private final PermissionCascadeMode permissionCascadeMode;
    private final String permissionPrefix;
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
     * Get the effective permission for this command, including inherited permissions from parent commands.
     * <p>
     * When permission cascade is enabled, this returns a combined permission that requires
     * both the parent's permission and this command's permission.
     *
     * @return the effective permission string, or null if no permissions are required
     */
    public @Nullable String effectivePermission() {
        if (parent == null) {
            return permission;
        }
        String parentPerm = parent.effectivePermission();
        if (parentPerm == null) {
            return permission;
        }
        if (permission == null) {
            return parentPerm;
        }
        // Both have permissions - return this command's permission
        // (the check will also verify parent permission)
        return permission;
    }

    /**
     * Check if a sender has the effective permission for this command.
     * <p>
     * This checks permissions based on the configured {@link PermissionCascadeMode}:
     * <ul>
     *   <li>{@code NONE}: Only checks this command's permission</li>
     *   <li>{@code INHERIT}: Checks all parent permissions first, then this command's</li>
     *   <li>{@code WILDCARD}: Also checks wildcard permissions (e.g., "admin.*")</li>
     *   <li>{@code INHERIT_FALLBACK}: Uses parent permission if no own permission set</li>
     *   <li>{@code AUTO_PREFIX}: Same as INHERIT (auto-generation happens at build time)</li>
     * </ul>
     *
     * @param sender the command sender to check
     * @return true if the sender has permission
     */
    public boolean hasEffectivePermission(@NotNull org.bukkit.command.CommandSender sender) {
        Preconditions.checkNotNull(sender, "sender");

        PermissionCascade.ParentPermissionChecker parentChecker = null;
        if (parent != null) {
            parentChecker = parent::hasEffectivePermission;
        }

        return PermissionCascade.hasPermission(sender, permission, parentChecker, permissionCascadeMode);
    }

    /**
     * @return the permission cascade mode for this command
     */
    public @NotNull PermissionCascadeMode permissionCascadeMode() {
        return permissionCascadeMode;
    }

    /**
     * @return the permission prefix for auto-generated permissions, or null
     */
    public @Nullable String permissionPrefix() {
        return permissionPrefix;
    }

    /**
     * Get all permissions required to execute this command (including parent permissions).
     *
     * @return list of all required permissions, from root to this command
     */
    public @NotNull List<String> allRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        collectPermissions(perms);
        return Collections.unmodifiableList(perms);
    }

    private void collectPermissions(List<String> perms) {
        if (parent != null) {
            parent.collectPermissions(perms);
        }
        if (permission != null) {
            perms.add(permission);
        }
    }

    /**
     * Get the parent command, if this is a subcommand.
     *
     * @return the parent command, or null if this is a root command
     */
    public @Nullable SlashCommand parent() {
        return parent;
    }

    /**
     * Check if this command is a subcommand.
     *
     * @return true if this command has a parent
     */
    public boolean isSubcommand() {
        return parent != null;
    }

    /**
     * Get the argument groups defined for this command.
     *
     * @return an immutable list of argument groups
     */
    public @NotNull List<ArgumentGroup> argumentGroups() {
        return argumentGroups;
    }

    /**
     * Get the argument group with the specified name.
     *
     * @param name the group name
     * @return the argument group, or null if not found
     */
    public @Nullable ArgumentGroup getArgumentGroup(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        for (ArgumentGroup group : argumentGroups) {
            if (group.name().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Get all arguments belonging to a specific group.
     *
     * @param groupName the group name
     * @return list of arguments in the group
     */
    public @NotNull List<Arg<?>> getArgumentsInGroup(@NotNull String groupName) {
        Preconditions.checkNotNull(groupName, "groupName");
        List<Arg<?>> result = new ArrayList<>();
        for (Arg<?> arg : args) {
            if (groupName.equals(arg.context().group())) {
                result.add(arg);
            }
        }
        return result;
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
     * @return true if quoted string parsing is enabled for this command
     */
    public boolean enableQuotedStrings() {
        return enableQuotedStrings;
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

    /**
     * Build a map of argument aliases to their primary names.
     * <p>
     * This map is used by CommandContext to resolve aliases when retrieving values.
     *
     * @return map of alias -> primary argument name
     */
    private @NotNull Map<String, String> buildAliasMap() {
        Map<String, String> aliasMap = new HashMap<>();
        for (Arg<?> arg : args) {
            for (String alias : arg.aliases()) {
                aliasMap.put(alias, arg.name());
            }
        }
        return aliasMap;
    }

    /**
     * Find an argument by name or alias.
     *
     * @param nameOrAlias the name or alias to search for
     * @return the matching argument, or null if not found
     */
    private @Nullable Arg<?> findArgByNameOrAlias(@NotNull String nameOrAlias) {
        for (Arg<?> arg : args) {
            if (arg.matchesNameOrAlias(nameOrAlias)) {
                return arg;
            }
        }
        return null;
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
                 List<ExecutionHook.Before> beforeHooks, List<ExecutionHook.After> afterHooks,
                 List<ArgumentGroup> argumentGroups, boolean enableQuotedStrings,
                 PermissionCascadeMode permissionCascadeMode, @Nullable String permissionPrefix) {
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
        // Cache the parser to avoid rebuilding internal HashMap caches on every execute()
        this.cachedFlagKvParser = (!this.flags.isEmpty() || !this.keyValues.isEmpty())
            ? new FlagAndKeyValueParser(this.flags, this.keyValues)
            : null;
        this.awaitConfirmation = awaitConfirmation;
        this.beforeHooks = List.copyOf(beforeHooks == null ? List.of() : beforeHooks);
        this.afterHooks = List.copyOf(afterHooks == null ? List.of() : afterHooks);
        this.argumentGroups = List.copyOf(argumentGroups == null ? List.of() : argumentGroups);
        this.enableQuotedStrings = enableQuotedStrings;
        this.permissionCascadeMode = permissionCascadeMode != null ? permissionCascadeMode : PermissionCascadeMode.INHERIT;
        this.permissionPrefix = permissionPrefix;
        // Pre-compute usage string for performance
        this.cachedUsage = computeUsageString();
        // Pre-compute alias map for argument alias support
        this.cachedAliasMap = Collections.unmodifiableMap(buildAliasMap());
    }

    /**
     * Sanitizes a string input by removing or escaping potentially dangerous characters.
     * This helps prevent injection attacks (SQL, command, XSS) when processing user input.
     * <p>
     * Optimized to perform sanitization, trimming, and whitespace collapsing in a single pass.
     *
     * @param input the raw input string to sanitize
     * @return the sanitized string
     */
    private @NotNull String sanitizeString(@NotNull String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }

        StringBuilder result = new StringBuilder(input.length());
        boolean lastWasSpace = true; // Start true to skip leading spaces (trim)

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
                    lastWasSpace = false;
                    break;
                case '>':
                    result.append("&gt;");
                    lastWasSpace = false;
                    break;
                case '&':
                    result.append("&amp;");
                    lastWasSpace = false;
                    break;
                case '"':
                    result.append("&quot;");
                    lastWasSpace = false;
                    break;
                case '\'':
                    result.append("&#39;");
                    lastWasSpace = false;
                    break;
                // Escape backslash
                case '\\':
                    result.append("\\\\");
                    lastWasSpace = false;
                    break;
                // Normalize whitespace: tabs, newlines, carriage returns, and spaces
                case '\t':
                case '\n':
                case '\r':
                case ' ':
                    // Collapse consecutive whitespace into a single space
                    if (!lastWasSpace) {
                        result.append(' ');
                        lastWasSpace = true;
                    }
                    break;
                default:
                    result.append(c);
                    lastWasSpace = false;
            }
        }

        // Remove trailing space (trim)
        int len = result.length();
        if (len > 0 && result.charAt(len - 1) == ' ') {
            result.setLength(len - 1);
        }

        return result.toString();
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
            // Lazy cleanup of expired confirmations to prevent memory leaks
            lazyConfirmationCleanup();
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

        // Handle quoted string parsing if enabled
        String[] effectiveArgs = providedArgs;
        if (enableQuotedStrings && providedArgs.length > 0) {
            QuotedStringTokenizer.TokenizeResult tokenResult = QuotedStringTokenizer.tokenize(providedArgs);
            if (!tokenResult.isSuccess()) {
                // Report parsing error for unclosed quotes
                sendErrorMessage(sender, ErrorType.PARSING, messages.quotedStringError(tokenResult.error()), null);
                return true;
            }
            effectiveArgs = tokenResult.tokens().toArray(new String[0]);
        }

        // Use effectiveArgs from here on instead of providedArgs for argument parsing
        // (providedArgs is still kept for raw context access)
        final String[] processedArgs = effectiveArgs;

        // Permission cascade: check all permissions from parent commands down to this one
        if (!hasEffectivePermission(sender)) {
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
        if (enableHelp && processedArgs.length == 0) {
            // Show help if command has subcommands or required arguments
            int required = 0;
            for (Arg<?> arg : args) {
                if (!arg.optional()) required++;
            }
            if (!subcommands.isEmpty() || required > 0) {
                generateHelpMessage(label, 1, sender);
                return true;
            }
        }

        // Automatic subcommand routing: if the first token matches a registered subcommand, delegate to it
        if (!subcommands.isEmpty() && processedArgs.length >= 1) {
            String first = processedArgs[0].toLowerCase(Locale.ROOT);
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
                // For subcommand execution, we pass the raw remaining args (not processed)
                // since the subcommand will do its own quote processing if enabled
                String[] remaining = processedArgs.length > 1
                    ? Arrays.copyOfRange(processedArgs, 1, processedArgs.length)
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

        // Parse flags and key-value pairs from processed arguments FIRST
        Map<String, Boolean> flagValues = Collections.emptyMap();
        Map<String, Object> keyValuePairs = Collections.emptyMap();
        Map<String, List<Object>> multiValuePairs = Collections.emptyMap();
        String[] positionalArgs = processedArgs;

        if (cachedFlagKvParser != null) {
            // Use cached parser to avoid rebuilding internal HashMap caches
            FlagAndKeyValueParser.ParsedResult flagKvResult = cachedFlagKvParser.parse(processedArgs, sender);

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
                CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
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
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
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

                // Did-You-Mean suggestions using SuggestionEngine (only shown if main error was sent)
                if (sendErrors) {
                    ArgContext argCtx = arg.context();
                    if (argCtx.didYouMean() && !argCtx.completionsPredefined().isEmpty()) {
                        try {
                            SuggestionEngine.Suggestion suggestion = SuggestionEngine.suggestArgument(token, argCtx.completionsPredefined());
                            if (suggestion.hasSuggestions()) {
                                sender.sendMessage(messages.didYouMean(String.join(", ", suggestion.suggestions())));
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

            // Apply transformation if transformer is present (legacy Function-based)
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

            // Apply transformers from ArgContext (new Transformer interface)
            ArgContext argCtxForTransform = arg.context();
            if (argCtxForTransform.hasTransformers() && parsedValue != null) {
                try {
                    parsedValue = argCtxForTransform.applyTransformers(parsedValue);
                } catch (Throwable t) {
                    String errorMsg = messages.argumentTransformationError(arg.name());
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger()
                            .severe("ArgContext transformation failed for argument '" + arg.name() + "': " + t.getMessage());
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
        // Also collect missing interactive arguments for potential interactive prompting
        List<Arg<?>> missingInteractiveArgs = new ArrayList<>();
        boolean hasMissingRequired = false;

        for (Arg<?> arg : args) {
            if (!values.containsKey(arg.name())) {
                // Check if this arg was skipped due to condition
                boolean skippedByCondition = false;
                if (arg.condition() != null) {
                    try {
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                        skippedByCondition = !arg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                        // If condition evaluation fails here, we already handled it during parsing
                    }
                }

                // Check if this arg was skipped due to permission
                boolean skippedByPermission = false;
                if (arg.permission() != null && !arg.permission().isEmpty()
                    && !sender.hasPermission(arg.permission())) {
                    skippedByPermission = arg.optional(); // Only skip if optional
                }

                // If not skipped, check if it's missing
                if (!skippedByCondition && !skippedByPermission) {
                    if (!arg.optional()) {
                        hasMissingRequired = true;
                    }
                    // Collect args with interactive mode enabled
                    if (arg.context().interactive()) {
                        missingInteractiveArgs.add(arg);
                    }
                }
            }
        }

        // If there are missing required arguments, try interactive prompting first
        if (hasMissingRequired) {
            // Check if interactive prompting is available (sender is Player, plugin is set, has interactive args)
            if (sender instanceof Player player && plugin != null && !missingInteractiveArgs.isEmpty()) {
                // Filter to only required missing args that support interactive mode
                List<Arg<?>> requiredInteractiveArgs = missingInteractiveArgs.stream()
                    .filter(a -> !a.optional())
                    .collect(Collectors.toList());

                if (!requiredInteractiveArgs.isEmpty()) {
                    // Start interactive prompt session
                    final Map<String, Object> currentValues = new LinkedHashMap<>(values);
                    final Map<String, Boolean> finalFlagValues = flagValues;
                    final Map<String, Object> finalKvPairs = keyValuePairs;
                    final Map<String, List<Object>> finalMultiPairs = multiValuePairs;
                    final String finalLabel = label;

                    InteractivePrompt.startSession(
                        plugin,
                        player,
                        requiredInteractiveArgs,
                        collectedValues -> {
                            // Merge collected values with existing values
                            currentValues.putAll(collectedValues);
                            // Re-execute with complete values
                            CommandContext ctx = new CommandContext(currentValues, finalFlagValues, finalKvPairs, finalMultiPairs, providedArgs, cachedAliasMap);
                            try {
                                executeAction(player, ctx, finalLabel);
                            } catch (Throwable t) {
                                sendErrorMessage(player, ErrorType.INTERNAL_ERROR, messages.internalError(), t);
                            }
                        },
                        () -> {
                            // Session cancelled - do nothing
                        },
                        messages
                    );
                    return true; // Return early - command will continue via interactive session
                }
            }

            // No interactive prompting available - show error
            sendErrorMessage(
                sender, ErrorType.USAGE,
                messages.insufficientArguments(fullCommandPath(label), usage()), null);
            return true;
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
            CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
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

        // Argument group validation
        if (!argumentGroups.isEmpty()) {
            CommandContext groupCtx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
            for (ArgumentGroup group : argumentGroups) {
                // Count how many members of this group are present
                int presentCount = 0;
                List<String> presentNames = new ArrayList<>();
                for (String memberName : group.memberNames()) {
                    if (groupCtx.has(memberName) || groupCtx.getFlag(memberName)) {
                        presentCount++;
                        presentNames.add(memberName);
                    }
                }

                // Check mutually exclusive constraint
                if (group.isMutuallyExclusive() && presentCount > 1) {
                    String members = String.join(", ", group.memberNames());
                    String provided = String.join(", ", presentNames);
                    sendErrorMessage(sender, ErrorType.CROSS_VALIDATION,
                        messages.argumentGroupMutuallyExclusive(group.name(), members, provided), null);
                    return true;
                }

                // Check at-least-one constraint
                if (group.isAtLeastOneRequired() && presentCount == 0) {
                    String members = String.join(", ", group.memberNames());
                    sendErrorMessage(sender, ErrorType.CROSS_VALIDATION,
                        messages.argumentGroupAtLeastOneRequired(group.name(), members), null);
                    return true;
                }

                // Check all-required constraint
                if (group.isAllRequired() && presentCount > 0 && presentCount < group.memberNames().size()) {
                    String members = String.join(", ", group.memberNames());
                    sendErrorMessage(sender, ErrorType.CROSS_VALIDATION,
                        messages.argumentGroupAllRequired(group.name(), members), null);
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

        CommandContext ctx = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);

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
     * Execute the command action directly with a pre-built context.
     * This is used by InteractivePrompt to execute the command after all arguments are collected.
     *
     * @param sender the command sender
     * @param ctx    the command context with all values
     * @param label  the command label
     */
    private void executeAction(@NotNull CommandSender sender, @NotNull CommandContext ctx, @NotNull String label) {
        if (action == null) {
            return;
        }

        // Execute before hooks
        for (ExecutionHook.Before beforeHook : beforeHooks) {
            try {
                ExecutionHook.BeforeResult beforeResult = beforeHook.execute(sender, ctx);
                if (beforeResult != null && beforeResult.shouldAbort()) {
                    if (beforeResult.message() != null) {
                        sender.sendMessage(beforeResult.message());
                    }
                    return;
                }
            } catch (Throwable t) {
                sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, messages.internalError(), t);
                return;
            }
        }

        long startTime = System.currentTimeMillis();
        boolean executionSuccess = false;
        Throwable executionException = null;

        try {
            action.execute(sender, ctx);
            executionSuccess = true;
        } catch (Throwable t) {
            executionException = (t.getCause() != null) ? t.getCause() : t;
            sendErrorMessage(sender, ErrorType.EXECUTION, messages.executionError(), executionException);
        } finally {
            // Execute after hooks
            long executionTime = System.currentTimeMillis() - startTime;
            ExecutionHook.AfterContext afterContext = executionSuccess
                ? ExecutionHook.AfterContext.success(executionTime)
                : ExecutionHook.AfterContext.failure(executionException, executionTime);

            for (ExecutionHook.After afterHook : afterHooks) {
                try {
                    afterHook.execute(sender, ctx, afterContext);
                } catch (Throwable t) {
                    if (plugin != null) {
                        plugin.getLogger()
                            .warning("After hook threw an exception for command '" + name + "': " + t.getMessage());
                    }
                }
            }
        }
    }

    // ==================== Parse-Only Methods ====================

    /**
     * Parse command arguments without executing the command action.
     * <p>
     * This method allows developers to separate parsing from execution, enabling:
     * <ul>
     *   <li>Custom error handling (errors are returned, not sent directly)</li>
     *   <li>Validation before execution</li>
     *   <li>Dry-run/preview functionality</li>
     *   <li>Custom execution flow control</li>
     * </ul>
     * <p>
     * This method performs:
     * <ul>
     *   <li>Permission checks (command and argument level)</li>
     *   <li>Player-only validation</li>
     *   <li>Guard evaluation</li>
     *   <li>Flag and key-value parsing</li>
     *   <li>Positional argument parsing with validation</li>
     *   <li>Cross-argument validation</li>
     * </ul>
     * <p>
     * It does NOT perform:
     * <ul>
     *   <li>Cooldown checks (use {@link #parseStrict} for that)</li>
     *   <li>Confirmation handling</li>
     *   <li>Command execution</li>
     *   <li>Before/after execution hooks</li>
     * </ul>
     * <p>
     * Example usage:
     * <pre>{@code
     * CommandParseResult result = command.parse(sender, label, args);
     *
     * // Handle success case
     * result.ifSuccess(ctx -> {
     *     // Do something with the parsed context
     *     Player target = ctx.get("target", Player.class);
     *     int amount = ctx.getIntOrDefault("amount", 1);
     * });
     *
     * // Handle failure case
     * result.ifFailure(errors -> {
     *     // Handle errors without automatic messaging
     *     errors.forEach(error -> {
     *         logger.warn("Parse error: " + error.message());
     *     });
     * });
     *
     * // Or use fluent chaining
     * command.parse(sender, label, args)
     *     .ifSuccess(ctx -> executeMyLogic(sender, ctx))
     *     .ifFailure(errors -> handleErrors(sender, errors));
     * }</pre>
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the raw argument tokens
     * @return a {@link CommandParseResult} containing either the parsed context or parsing errors
     */
    public @NotNull CommandParseResult parse(@NotNull CommandSender sender,
                                              @NotNull String label,
                                              @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");

        List<CommandParseError> errors = new ArrayList<>();

        // Permission check
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            return CommandParseResult.failure(
                CommandParseError.permission(messages.noPermission()),
                providedArgs
            );
        }

        // Player-only check
        if (playerOnly && !(sender instanceof Player)) {
            return CommandParseResult.failure(
                CommandParseError.playerOnly(messages.playerOnly()),
                providedArgs
            );
        }

        // Guards
        for (Guard g : this.guards) {
            try {
                if (!g.test(sender)) {
                    return CommandParseResult.failure(
                        CommandParseError.guardFailed(g.errorMessage()),
                        providedArgs
                    );
                }
            } catch (Throwable t) {
                return CommandParseResult.failure(
                    CommandParseError.guardFailed(messages.guardFailed()),
                    providedArgs
                );
            }
        }

        // Parse flags and key-value pairs
        Map<String, Boolean> flagValues = Collections.emptyMap();
        Map<String, Object> keyValuePairs = Collections.emptyMap();
        Map<String, List<Object>> multiValuePairs = Collections.emptyMap();
        String[] positionalArgs = providedArgs;

        if (!flags.isEmpty() || !keyValues.isEmpty()) {
            FlagAndKeyValueParser flagKvParser = new FlagAndKeyValueParser(flags, keyValues);
            FlagAndKeyValueParser.ParsedResult flagKvResult = flagKvParser.parse(providedArgs, sender);

            if (!flagKvResult.isSuccess()) {
                // Collect all flag/key-value parsing errors
                for (String errorMsg : flagKvResult.errors()) {
                    errors.add(CommandParseError.of(ErrorType.PARSING, errorMsg));
                }
                return CommandParseResult.failure(errors, providedArgs);
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

            positionalArgs = flagKvResult.remainingArgs().toArray(new String[0]);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();

        // Parse arguments
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < positionalArgs.length) {
            Arg<?> arg = args.get(argIndex);

            // Evaluate conditional argument
            if (arg.condition() != null) {
                CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                try {
                    if (!arg.condition().test(tempCtx)) {
                        argIndex++;
                        continue;
                    }
                } catch (Throwable t) {
                    errors.add(CommandParseError.internal(messages.argumentConditionError(arg.name()))
                        .forArgument(arg.name()));
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }

            // Per-argument permission check
            if (arg.permission() != null && !arg.permission().isEmpty() && !sender.hasPermission(arg.permission())) {
                if (!arg.optional()) {
                    errors.add(CommandParseError.argumentPermission(arg.name(), messages.argumentPermissionDenied(arg.name())));
                    return CommandParseResult.failure(errors, providedArgs);
                }
                argIndex++;
                continue;
            }

            ArgumentParser<?> parser = arg.parser();
            String token;

            // Check if this is the last argument to be parsed
            boolean isLastArgToBeParsed = true;
            for (int checkIdx = argIndex + 1; checkIdx < args.size(); checkIdx++) {
                Arg<?> futureArg = args.get(checkIdx);

                boolean willBeSkippedByCondition = false;
                if (futureArg.condition() != null) {
                    try {
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                        willBeSkippedByCondition = !futureArg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                    }
                }

                boolean willBeSkippedByPermission = futureArg.permission() != null
                    && !futureArg.permission().isEmpty()
                    && !sender.hasPermission(futureArg.permission())
                    && futureArg.optional();

                if (!willBeSkippedByCondition && !willBeSkippedByPermission) {
                    isLastArgToBeParsed = false;
                    break;
                }
            }

            if (isLastArgToBeParsed && arg.greedy()) {
                if (tokenIndex >= positionalArgs.length) {
                    token = "";
                } else {
                    token = String.join(" ", Arrays.asList(positionalArgs).subList(tokenIndex, positionalArgs.length));
                }
                tokenIndex = positionalArgs.length;
            } else {
                token = positionalArgs[tokenIndex++];
            }

            ParseResult<?> res;
            try {
                res = parser.parse(token, sender);
                if (res == null) {
                    throw new ParsingException(
                        "Parser " + parser.getClass().getName() + " returned null ParseResult for argument '"
                        + arg.name() + "'");
                }
            } catch (ParsingException pe) {
                throw pe;
            } catch (Throwable t) {
                errors.add(CommandParseError.internal(messages.argumentParsingError(arg.name()))
                    .forArgument(arg.name())
                    .withInput(token));
                return CommandParseResult.failure(errors, providedArgs);
            }

            if (!res.isSuccess()) {
                String msg = res.error().orElse("invalid value");
                String errorMsg = messages.invalidArgumentValue(arg.name(), parser.getTypeName(), msg);
                errors.add(CommandParseError.parsing(arg.name(), errorMsg).withInput(token));
                return CommandParseResult.failure(errors, providedArgs);
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
                    errors.add(CommandParseError.internal(messages.argumentTransformationError(arg.name()))
                        .forArgument(arg.name()));
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }

            // Apply validations from ArgContext
            ArgContext ctx = arg.context();
            String validationError;
            try {
                validationError = ValidationHelper.validateValue(
                    parsedValue, ctx, arg.name(), parser.getTypeName(), messages);
            } catch (Throwable t) {
                errors.add(CommandParseError.internal(messages.argumentValidationError(arg.name()))
                    .forArgument(arg.name()));
                return CommandParseResult.failure(errors, providedArgs);
            }

            if (validationError != null) {
                errors.add(CommandParseError.validation(arg.name(), messages.validationFailed(arg.name(), validationError)));
                return CommandParseResult.failure(errors, providedArgs);
            }

            values.put(arg.name(), parsedValue);
            argIndex++;
        }

        // Validate that all required arguments were provided
        for (Arg<?> arg : args) {
            if (!arg.optional() && !values.containsKey(arg.name())) {
                boolean skippedByCondition = false;
                if (arg.condition() != null) {
                    try {
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                        skippedByCondition = !arg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                    }
                }

                boolean skippedByPermission = false;
                if (arg.optional() && arg.permission() != null && !arg.permission().isEmpty()
                    && !sender.hasPermission(arg.permission())) {
                    skippedByPermission = true;
                }

                if (!skippedByCondition && !skippedByPermission) {
                    errors.add(CommandParseError.usage(messages.insufficientArguments(fullCommandPath(label), usage())));
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }
        }

        // Apply default values for missing optional arguments
        for (Arg<?> arg : args) {
            if (!values.containsKey(arg.name()) && arg.context().defaultValue() != null) {
                values.put(arg.name(), arg.context().defaultValue());
            }
        }

        // Check for extra arguments after parsing
        if (!lastIsGreedy && tokenIndex < positionalArgs.length) {
            errors.add(CommandParseError.usage(messages.tooManyArguments(fullCommandPath(label), usage())));
            return CommandParseResult.failure(errors, providedArgs);
        }

        // Cross-argument validation
        if (!crossArgumentValidators.isEmpty()) {
            CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
            for (CrossArgumentValidator validator : crossArgumentValidators) {
                String error;
                try {
                    error = validator.validate(tempCtx);
                } catch (Throwable t) {
                    errors.add(CommandParseError.internal(messages.crossValidationInternalError()));
                    return CommandParseResult.failure(errors, providedArgs);
                }
                if (error != null) {
                    errors.add(CommandParseError.crossValidation(messages.crossValidationFailed(error)));
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }
        }

        // Build final context
        CommandContext finalContext = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
        return CommandParseResult.success(finalContext, providedArgs);
    }

    /**
     * Parse command arguments with configurable options.
     * <p>
     * This overload provides fine-grained control over the parsing process through
     * {@link ParseOptions}. Use this when you need to customize parsing behavior.
     * <p>
     * Example usage:
     * <pre>{@code
     * // Collect all errors instead of failing on first
     * ParseOptions options = ParseOptions.builder()
     *     .collectAllErrors(true)
     *     .includeSuggestions(true)
     *     .build();
     *
     * CommandParseResult result = command.parse(sender, label, args, options);
     * if (result.hasErrors()) {
     *     result.errors().forEach(e -> {
     *         logger.warn(e.toUserMessage());
     *     });
     * }
     *
     * // Parse with cooldowns and subcommand routing
     * CommandParseResult result = command.parse(sender, label, args, ParseOptions.STRICT);
     * }</pre>
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the raw argument tokens
     * @param options      the parsing options
     * @return a {@link CommandParseResult} containing either the parsed context or parsing errors
     */
    public @NotNull CommandParseResult parse(@NotNull CommandSender sender,
                                              @NotNull String label,
                                              @NotNull String[] providedArgs,
                                              @NotNull ParseOptions options) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(options, "options");

        List<CommandParseError> errors = new ArrayList<>();

        // Permission check (unless skipped)
        if (!options.skipPermissionChecks()) {
            if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
                return CommandParseResult.failure(
                    CommandParseError.permission(messages.noPermission()),
                    providedArgs
                );
            }
        }

        // Player-only check (never skip this - it's a type check)
        if (playerOnly && !(sender instanceof Player)) {
            return CommandParseResult.failure(
                CommandParseError.playerOnly(messages.playerOnly()),
                providedArgs
            );
        }

        // Guards (unless skipped)
        if (!options.skipGuards()) {
            for (Guard g : this.guards) {
                try {
                    if (!g.test(sender)) {
                        return CommandParseResult.failure(
                            CommandParseError.guardFailed(g.errorMessage()),
                            providedArgs
                        );
                    }
                } catch (Throwable t) {
                    return CommandParseResult.failure(
                        CommandParseError.guardFailed(messages.guardFailed()),
                        providedArgs
                    );
                }
            }
        }

        // Cooldown checks (if enabled in options)
        if (options.checkCooldowns()) {
            CooldownResult serverCooldown = CooldownManager.checkServerCooldown(name, perServerCooldownMillis);
            if (serverCooldown.onCooldown()) {
                String formattedTime = CooldownManager.formatCooldownMessage("%s", serverCooldown.remainingMillis());
                return CommandParseResult.failure(
                    CommandParseError.cooldown(messages.serverCooldown(formattedTime)),
                    providedArgs
                );
            }

            CooldownResult userCooldown = CooldownManager.checkUserCooldown(name, sender.getName(), perUserCooldownMillis);
            if (userCooldown.onCooldown()) {
                String formattedTime = CooldownManager.formatCooldownMessage("%s", userCooldown.remainingMillis());
                return CommandParseResult.failure(
                    CommandParseError.cooldown(messages.userCooldown(formattedTime)),
                    providedArgs
                );
            }
        }

        // Confirmation check (if enabled in options)
        if (options.checkConfirmation() && awaitConfirmation) {
            String confirmationKey = name + ":" + sender.getName();
            final long currentTime = System.currentTimeMillis();
            final long newExpiry = currentTime + CONFIRMATION_TIMEOUT_MILLIS;

            pendingConfirmations.entrySet().removeIf(entry -> entry.getValue() < currentTime);

            final boolean[] needsConfirmation = {false};
            pendingConfirmations.compute(confirmationKey, (key, existingExpiry) -> {
                if (existingExpiry != null && existingExpiry >= currentTime) {
                    return null;
                } else {
                    needsConfirmation[0] = true;
                    return newExpiry;
                }
            });

            if (needsConfirmation[0]) {
                return CommandParseResult.failure(
                    CommandParseError.confirmationRequired(messages.awaitConfirmation()),
                    providedArgs
                );
            }
        }

        // Subcommand routing (if enabled in options)
        if (options.includeSubcommands() && !subcommands.isEmpty() && providedArgs.length >= 1) {
            String first = providedArgs[0].toLowerCase(Locale.ROOT);
            SlashCommand sub = subcommands.get(first);

            // Fuzzy matching
            if (sub == null && fuzzySubcommandMatching) {
                List<String> subcommandNames = new ArrayList<>(subcommands.keySet());
                List<String> similar = StringSimilarity.findSimilar(first, subcommandNames, 1, fuzzyMatchThreshold);
                if (!similar.isEmpty()) {
                    sub = subcommands.get(similar.get(0));
                }
            }

            if (sub != null) {
                String[] remaining = providedArgs.length > 1
                    ? Arrays.copyOfRange(providedArgs, 1, providedArgs.length)
                    : new String[0];
                return sub.parse(sender, sub.name(), remaining, options);
            } else if (!args.isEmpty()) {
                // Has positional args, don't fail on unknown subcommand - let it be parsed as arg
            } else {
                // No positional args expected - this is an unknown subcommand
                List<String> similar = StringSimilarity.findSimilar(first, new ArrayList<>(subcommands.keySet()));
                if (options.includeSuggestions() && !similar.isEmpty()) {
                    return CommandParseResult.failure(
                        CommandParseError.subcommandNotFound(first,
                            messages.unknownSubcommand(first), similar),
                        providedArgs
                    );
                } else {
                    return CommandParseResult.failure(
                        CommandParseError.subcommandNotFound(first, messages.unknownSubcommand(first)),
                        providedArgs
                    );
                }
            }
        }

        // Continue with standard parsing (reuse existing logic)
        // Parse flags and key-value pairs
        Map<String, Boolean> flagValues = Collections.emptyMap();
        Map<String, Object> keyValuePairs = Collections.emptyMap();
        Map<String, List<Object>> multiValuePairs = Collections.emptyMap();
        String[] positionalArgs = providedArgs;

        if (!flags.isEmpty() || !keyValues.isEmpty()) {
            FlagAndKeyValueParser flagKvParser = new FlagAndKeyValueParser(flags, keyValues);
            FlagAndKeyValueParser.ParsedResult flagKvResult = flagKvParser.parse(providedArgs, sender);

            if (!flagKvResult.isSuccess()) {
                for (String errorMsg : flagKvResult.errors()) {
                    errors.add(CommandParseError.of(ErrorType.PARSING, errorMsg));
                }
                if (!options.collectAllErrors()) {
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }

            flagValues = flagKvResult.flagValues();
            keyValuePairs = flagKvResult.keyValuePairs();
            multiValuePairs = flagKvResult.multiValuePairs();

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

            positionalArgs = flagKvResult.remainingArgs().toArray(new String[0]);
        }

        // If we have errors from flag parsing and we're collecting all, don't parse positional args
        if (!errors.isEmpty() && options.collectAllErrors()) {
            // Return errors collected so far
            return CommandParseResult.failure(errors, providedArgs);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();

        // Parse arguments
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < positionalArgs.length) {
            Arg<?> arg = args.get(argIndex);

            // Evaluate conditional argument
            if (arg.condition() != null) {
                CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                try {
                    if (!arg.condition().test(tempCtx)) {
                        argIndex++;
                        continue;
                    }
                } catch (Throwable t) {
                    errors.add(CommandParseError.internal(messages.argumentConditionError(arg.name()))
                        .forArgument(arg.name()));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                }
            }

            // Per-argument permission check
            if (!options.skipPermissionChecks() && arg.permission() != null && !arg.permission().isEmpty()
                && !sender.hasPermission(arg.permission())) {
                if (!arg.optional()) {
                    errors.add(CommandParseError.argumentPermission(arg.name(),
                        messages.argumentPermissionDenied(arg.name())));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                }
                argIndex++;
                continue;
            }

            ArgumentParser<?> parser = arg.parser();
            String token;

            // Check if this is the last argument to be parsed
            boolean isLastArgToBeParsed = true;
            for (int checkIdx = argIndex + 1; checkIdx < args.size(); checkIdx++) {
                Arg<?> futureArg = args.get(checkIdx);

                boolean willBeSkippedByCondition = false;
                if (futureArg.condition() != null) {
                    try {
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                        willBeSkippedByCondition = !futureArg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                    }
                }

                boolean willBeSkippedByPermission = !options.skipPermissionChecks()
                    && futureArg.permission() != null
                    && !futureArg.permission().isEmpty()
                    && !sender.hasPermission(futureArg.permission())
                    && futureArg.optional();

                if (!willBeSkippedByCondition && !willBeSkippedByPermission) {
                    isLastArgToBeParsed = false;
                    break;
                }
            }

            if (isLastArgToBeParsed && arg.greedy()) {
                if (tokenIndex >= positionalArgs.length) {
                    token = "";
                } else {
                    token = String.join(" ", Arrays.asList(positionalArgs).subList(tokenIndex, positionalArgs.length));
                }
                tokenIndex = positionalArgs.length;
            } else {
                token = positionalArgs[tokenIndex++];
            }

            ParseResult<?> res;
            try {
                res = parser.parse(token, sender);
                if (res == null) {
                    throw new ParsingException(
                        "Parser " + parser.getClass().getName() + " returned null ParseResult for argument '"
                        + arg.name() + "'");
                }
            } catch (ParsingException pe) {
                throw pe;
            } catch (Throwable t) {
                errors.add(CommandParseError.internal(messages.argumentParsingError(arg.name()))
                    .forArgument(arg.name())
                    .withInput(token));
                if (!options.collectAllErrors()) {
                    return CommandParseResult.failure(errors, providedArgs);
                }
                argIndex++;
                continue;
            }

            if (!res.isSuccess()) {
                String msg = res.error().orElse("invalid value");
                String errorMsg = messages.invalidArgumentValue(arg.name(), parser.getTypeName(), msg);
                CommandParseError parseError = CommandParseError.parsing(arg.name(), errorMsg).withInput(token);

                // Add did-you-mean suggestions if enabled
                if (options.includeSuggestions()) {
                    ArgContext argCtx = arg.context();
                    if (argCtx.didYouMean() && !argCtx.completionsPredefined().isEmpty()) {
                        try {
                            List<String> suggestions = StringSimilarity.findSimilar(
                                token, argCtx.completionsPredefined());
                            if (!suggestions.isEmpty()) {
                                parseError = parseError.withSuggestions(suggestions);
                            }
                        } catch (Throwable ignored) {
                            // Silently ignore errors in suggestion generation
                        }
                    }
                }

                errors.add(parseError);
                if (!options.collectAllErrors()) {
                    return CommandParseResult.failure(errors, providedArgs);
                }
                argIndex++;
                continue;
            }

            Object parsedValue = res.value().orElse(null);

            if (sanitizeInputs && parsedValue instanceof String) {
                parsedValue = sanitizeString((String) parsedValue);
            }

            if (arg.transformer() != null && parsedValue != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Function<Object, Object> transformer = (Function<Object, Object>) arg.transformer();
                    parsedValue = transformer.apply(parsedValue);
                } catch (Throwable t) {
                    errors.add(CommandParseError.internal(messages.argumentTransformationError(arg.name()))
                        .forArgument(arg.name()));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                }
            }

            ArgContext ctx = arg.context();
            String validationError;
            try {
                validationError = ValidationHelper.validateValue(
                    parsedValue, ctx, arg.name(), parser.getTypeName(), messages);
            } catch (Throwable t) {
                errors.add(CommandParseError.internal(messages.argumentValidationError(arg.name()))
                    .forArgument(arg.name()));
                if (!options.collectAllErrors()) {
                    return CommandParseResult.failure(errors, providedArgs);
                }
                argIndex++;
                continue;
            }

            if (validationError != null) {
                errors.add(CommandParseError.validation(arg.name(),
                    messages.validationFailed(arg.name(), validationError)));
                if (!options.collectAllErrors()) {
                    return CommandParseResult.failure(errors, providedArgs);
                }
            }

            values.put(arg.name(), parsedValue);
            argIndex++;
        }

        // Validate that all required arguments were provided
        for (Arg<?> arg : args) {
            if (!arg.optional() && !values.containsKey(arg.name())) {
                boolean skippedByCondition = false;
                if (arg.condition() != null) {
                    try {
                        CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
                        skippedByCondition = !arg.condition().test(tempCtx);
                    } catch (Throwable ignored) {
                    }
                }

                boolean skippedByPermission = false;
                if (arg.optional() && arg.permission() != null && !arg.permission().isEmpty()
                    && !sender.hasPermission(arg.permission())) {
                    skippedByPermission = true;
                }

                if (!skippedByCondition && !skippedByPermission) {
                    errors.add(CommandParseError.usage(messages.insufficientArguments(fullCommandPath(label), usage())));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                }
            }
        }

        // Apply default values for missing optional arguments
        for (Arg<?> arg : args) {
            if (!values.containsKey(arg.name()) && arg.context().defaultValue() != null) {
                values.put(arg.name(), arg.context().defaultValue());
            }
        }

        // Check for extra arguments after parsing
        if (!lastIsGreedy && tokenIndex < positionalArgs.length) {
            errors.add(CommandParseError.usage(messages.tooManyArguments(fullCommandPath(label), usage())));
            if (!options.collectAllErrors()) {
                return CommandParseResult.failure(errors, providedArgs);
            }
        }

        // Cross-argument validation
        if (!crossArgumentValidators.isEmpty()) {
            CommandContext tempCtx = CommandContext.createInternal(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
            for (CrossArgumentValidator validator : crossArgumentValidators) {
                String error;
                try {
                    error = validator.validate(tempCtx);
                } catch (Throwable t) {
                    errors.add(CommandParseError.internal(messages.crossValidationInternalError()));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                    continue;
                }
                if (error != null) {
                    errors.add(CommandParseError.crossValidation(messages.crossValidationFailed(error)));
                    if (!options.collectAllErrors()) {
                        return CommandParseResult.failure(errors, providedArgs);
                    }
                }
            }
        }

        // Return failure if any errors were collected
        if (!errors.isEmpty()) {
            return CommandParseResult.failure(errors, providedArgs);
        }

        // Build final context
        CommandContext finalContext = new CommandContext(values, flagValues, keyValuePairs, multiValuePairs, providedArgs, cachedAliasMap);
        return CommandParseResult.success(finalContext, providedArgs);
    }

    /**
     * Parse command arguments with full strictness, including cooldown checks.
     * <p>
     * This method performs all the same checks as {@link #parse}, plus:
     * <ul>
     *   <li>Server cooldown checks</li>
     *   <li>User cooldown checks</li>
     * </ul>
     * <p>
     * Use this method when you want to respect cooldowns but still separate
     * parsing from execution for custom handling.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the raw argument tokens
     * @return a {@link CommandParseResult} containing either the parsed context or parsing errors
     */
    public @NotNull CommandParseResult parseStrict(@NotNull CommandSender sender,
                                                    @NotNull String label,
                                                    @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");

        // Check cooldowns first
        CooldownResult serverCooldown = CooldownManager.checkServerCooldown(name, perServerCooldownMillis);
        if (serverCooldown.onCooldown()) {
            String formattedTime = CooldownManager.formatCooldownMessage("%s", serverCooldown.remainingMillis());
            return CommandParseResult.failure(
                CommandParseError.guardFailed(messages.serverCooldown(formattedTime)),
                providedArgs
            );
        }

        CooldownResult userCooldown = CooldownManager.checkUserCooldown(name, sender.getName(), perUserCooldownMillis);
        if (userCooldown.onCooldown()) {
            String formattedTime = CooldownManager.formatCooldownMessage("%s", userCooldown.remainingMillis());
            return CommandParseResult.failure(
                CommandParseError.guardFailed(messages.userCooldown(formattedTime)),
                providedArgs
            );
        }

        // Delegate to regular parse
        return parse(sender, label, providedArgs);
    }

    /**
     * Parse and execute the command if parsing succeeds.
     * <p>
     * This is a convenience method that combines parsing and execution with custom
     * error handling. Unlike {@link #execute}, this method does not automatically
     * send error messages to the sender - instead, it returns the parse result
     * allowing you to handle errors as needed.
     * <p>
     * Example usage:
     * <pre>{@code
     * command.parseAndExecute(sender, label, args, (s, ctx) -> {
     *     Player target = ctx.require("target", Player.class);
     *     target.sendMessage("Hello!");
     * }).ifFailure(errors -> {
     *     // Custom error handling
     *     sender.sendMessage("§cCommand failed: " + errors.get(0).message());
     * });
     * }</pre>
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the raw argument tokens
     * @param action       the action to execute if parsing succeeds
     * @return the parse result for further handling
     */
    public @NotNull CommandParseResult parseAndExecute(@NotNull CommandSender sender,
                                                        @NotNull String label,
                                                        @NotNull String[] providedArgs,
                                                        @NotNull CommandAction action) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(action, "action");

        CommandParseResult result = parseStrict(sender, label, providedArgs);

        if (result.isSuccess()) {
            // Update cooldowns after successful parse
            if (perServerCooldownMillis > 0) {
                try {
                    CooldownManager.updateServerCooldown(name);
                } catch (Throwable t) {
                    if (plugin != null) {
                        plugin.getLogger().warning("Failed to update server cooldown for command '" + name + "': " + t.getMessage());
                    }
                }
            }
            if (perUserCooldownMillis > 0) {
                try {
                    CooldownManager.updateUserCooldown(name, sender.getName());
                } catch (Throwable t) {
                    if (plugin != null) {
                        plugin.getLogger().warning(
                            "Failed to update user cooldown for command '" + name + "' and user '" + sender.getName() + "': " + t.getMessage());
                    }
                }
            }

            // Execute the action
            CommandContext ctx = result.contextOrThrow();
            try {
                action.execute(sender, ctx);
            } catch (Throwable t) {
                // Return failure with execution error
                return CommandParseResult.failure(
                    CommandParseError.internal(messages.executionError()),
                    providedArgs
                );
            }
        }

        return result;
    }

    // ==================== Async Parsing ====================

    /**
     * Parse command arguments asynchronously.
     * <p>
     * This method performs parsing on a separate thread, which is useful for commands
     * that might involve I/O operations during parsing (e.g., database lookups for player names).
     * <p>
     * Example usage:
     * <pre>{@code
     * command.parseAsync(sender, label, args)
     *     .thenAccept(result -> {
     *         if (result.isSuccess()) {
     *             // Handle success on async thread
     *         } else {
     *             // Handle errors
     *         }
     *     });
     * }</pre>
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @return a CompletableFuture that completes with the parse result
     */
    public @NotNull CompletableFuture<CommandParseResult> parseAsync(@NotNull CommandSender sender,
                                                                      @NotNull String label,
                                                                      @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");

        return CompletableFuture.supplyAsync(() -> parse(sender, label, providedArgs));
    }

    /**
     * Parse command arguments asynchronously with custom options.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @param options      the parsing options
     * @return a CompletableFuture that completes with the parse result
     */
    public @NotNull CompletableFuture<CommandParseResult> parseAsync(@NotNull CommandSender sender,
                                                                      @NotNull String label,
                                                                      @NotNull String[] providedArgs,
                                                                      @NotNull ParseOptions options) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(options, "options");

        return CompletableFuture.supplyAsync(() -> parse(sender, label, providedArgs, options));
    }

    /**
     * Parse and execute command asynchronously.
     * <p>
     * Note: The action will be executed on the async thread. If you need to interact
     * with the Bukkit API, you should schedule the action back to the main thread.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @param action       the action to execute if parsing succeeds
     * @return a CompletableFuture that completes with the parse result
     */
    public @NotNull CompletableFuture<CommandParseResult> parseAndExecuteAsync(@NotNull CommandSender sender,
                                                                                 @NotNull String label,
                                                                                 @NotNull String[] providedArgs,
                                                                                 @NotNull CommandAction action) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(action, "action");

        return CompletableFuture.supplyAsync(() -> parseAndExecute(sender, label, providedArgs, action));
    }

    // ==================== Parse Simulation ====================

    /**
     * Parse command as if executed by a different sender.
     * <p>
     * This is useful for testing permissions and sender-specific validations,
     * or for admin commands that need to validate how a command would behave for another user.
     * <p>
     * Example usage:
     * <pre>{@code
     * // Test what errors a player would get
     * CommandParseResult result = command.parseAs(targetPlayer, "give", new String[]{"diamond", "64"});
     * if (result.hasAccessErrors()) {
     *     admin.sendMessage("Player lacks permission for this command");
     * }
     * }</pre>
     *
     * @param simulatedSender the sender to simulate
     * @param label           the command label used
     * @param providedArgs    the command arguments
     * @return the parse result as if executed by the simulated sender
     */
    public @NotNull CommandParseResult parseAs(@NotNull CommandSender simulatedSender,
                                                @NotNull String label,
                                                @NotNull String[] providedArgs) {
        return parse(simulatedSender, label, providedArgs);
    }

    /**
     * Parse command as if executed by a different sender with custom options.
     *
     * @param simulatedSender the sender to simulate
     * @param label           the command label used
     * @param providedArgs    the command arguments
     * @param options         the parsing options
     * @return the parse result as if executed by the simulated sender
     */
    public @NotNull CommandParseResult parseAs(@NotNull CommandSender simulatedSender,
                                                @NotNull String label,
                                                @NotNull String[] providedArgs,
                                                @NotNull ParseOptions options) {
        return parse(simulatedSender, label, providedArgs, options);
    }

    /**
     * Parse command for multiple senders at once.
     * <p>
     * This is useful for batch operations or testing how a command behaves
     * across different permission levels.
     * <p>
     * Example usage:
     * <pre>{@code
     * List<CommandSender> players = getOnlinePlayers();
     * Map<CommandSender, CommandParseResult> results = command.parseForAll(players, "give", args);
     *
     * // Find all players who can execute this command
     * List<CommandSender> eligible = results.entrySet().stream()
     *     .filter(e -> e.getValue().isSuccess())
     *     .map(Map.Entry::getKey)
     *     .collect(Collectors.toList());
     * }</pre>
     *
     * @param senders      the senders to parse for
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @return a map from sender to their respective parse result
     */
    public @NotNull Map<CommandSender, CommandParseResult> parseForAll(@NotNull Collection<? extends CommandSender> senders,
                                                                        @NotNull String label,
                                                                        @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(senders, "senders");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");

        Map<CommandSender, CommandParseResult> results = new LinkedHashMap<>();
        for (CommandSender sender : senders) {
            results.put(sender, parse(sender, label, providedArgs));
        }
        return Collections.unmodifiableMap(results);
    }

    /**
     * Parse command for multiple senders with custom options.
     *
     * @param senders      the senders to parse for
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @param options      the parsing options
     * @return a map from sender to their respective parse result
     */
    public @NotNull Map<CommandSender, CommandParseResult> parseForAll(@NotNull Collection<? extends CommandSender> senders,
                                                                        @NotNull String label,
                                                                        @NotNull String[] providedArgs,
                                                                        @NotNull ParseOptions options) {
        Preconditions.checkNotNull(senders, "senders");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(options, "options");

        Map<CommandSender, CommandParseResult> results = new LinkedHashMap<>();
        for (CommandSender sender : senders) {
            results.put(sender, parse(sender, label, providedArgs, options));
        }
        return Collections.unmodifiableMap(results);
    }

    /**
     * Parse command for all senders in parallel.
     *
     * @param senders      the senders to parse for
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @return a CompletableFuture that completes with a map from sender to their parse result
     */
    public @NotNull CompletableFuture<Map<CommandSender, CommandParseResult>> parseForAllAsync(
            @NotNull Collection<? extends CommandSender> senders,
            @NotNull String label,
            @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(senders, "senders");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");

        return CompletableFuture.supplyAsync(() -> parseForAll(senders, label, providedArgs));
    }

    // ==================== Partial Parsing ====================

    /**
     * Parse command arguments partially according to the given options.
     * <p>
     * Partial parsing allows you to parse only a subset of arguments, which is useful for:
     * <ul>
     *   <li>Tab completion validation - parse only what's been typed so far</li>
     *   <li>Progressive validation - validate as user types</li>
     *   <li>Early error detection - stop at first error</li>
     *   <li>Argument-specific testing - only test certain arguments</li>
     * </ul>
     * <p>
     * Example usage:
     * <pre>{@code
     * // Parse only first 2 arguments for early validation
     * PartialParseResult result = command.parsePartial(sender, "cmd", args,
     *     PartialParseOptions.firstN(2));
     *
     * // Get what was successfully parsed even if there were errors
     * Map<String, Object> parsed = result.parsedArguments();
     *
     * // Check where parsing failed
     * if (result.hasErrors()) {
     *     int errorIndex = result.errorArgumentIndex();
     *     sender.sendMessage("Error at argument " + errorIndex);
     * }
     * }</pre>
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @param options      the partial parsing options
     * @return the partial parse result
     */
    public @NotNull PartialParseResult parsePartial(@NotNull CommandSender sender,
                                                     @NotNull String label,
                                                     @NotNull String[] providedArgs,
                                                     @NotNull PartialParseOptions options) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        Preconditions.checkNotNull(options, "options");

        PartialParseResult.Builder resultBuilder = PartialParseResult.builder()
            .withRawArgs(providedArgs);

        // Permission check (unless skipped)
        if (!options.skipPermissionChecks() && permission != null && !sender.hasPermission(permission)) {
            return resultBuilder
                .withError(CommandParseError.permission(messages.noPermission()))
                .build();
        }

        // Player-only check
        if (requiresPlayer && !(sender instanceof Player)) {
            return resultBuilder
                .withError(CommandParseError.playerOnly(messages.playersOnly()))
                .build();
        }

        // Guard checks (unless skipped)
        if (!options.skipGuards()) {
            for (Guard guard : guards) {
                try {
                    if (!guard.check(sender)) {
                        return resultBuilder
                            .withError(CommandParseError.guardFailed(guard.message()))
                            .build();
                    }
                } catch (Throwable t) {
                    return resultBuilder
                        .withError(CommandParseError.internal("Guard check failed: " + t.getMessage()))
                        .build();
                }
            }
        }

        // Parse arguments according to options
        int argIndex = 0;
        int parsedCount = 0;

        for (int i = 0; i < args.size(); i++) {
            // Check if we should parse this argument
            if (!options.shouldParseArgument(i)) {
                continue;
            }

            Arg<?> arg = args.get(i);

            // Check if we have input for this argument
            if (argIndex >= providedArgs.length) {
                if (!arg.optional()) {
                    if (options.stopOnFirstError()) {
                        return resultBuilder
                            .withErrorAt(CommandParseError.usage("Missing required argument: " + arg.name()), i)
                            .argumentsParsed(parsedCount)
                            .build();
                    }
                }
                break;
            }

            // Parse the argument
            String input = providedArgs[argIndex];
            ArgumentParser<?> parser = arg.parser();

            try {
                ArgContext argContext = new ArgContext(
                    sender, label, providedArgs, argIndex,
                    input, arg, Collections.emptyMap()
                );

                ParseResult<?> parseResult = parser.parse(argContext);

                if (parseResult.isSuccess()) {
                    Object value = parseResult.value();

                    // Run validators if present
                    if (arg.validatorChain() != null) {
                        Optional<String> validationError = ValidationHelper.validate(arg.validatorChain(), value, arg.name());
                        if (validationError.isPresent()) {
                            if (options.stopOnFirstError()) {
                                return resultBuilder
                                    .withErrorAt(CommandParseError.validation(arg.name(), validationError.get()), i)
                                    .argumentsParsed(parsedCount)
                                    .build();
                            }
                            resultBuilder.withErrorAt(CommandParseError.validation(arg.name(), validationError.get()), i);
                        }
                    }

                    resultBuilder.withArgument(arg.name(), value);
                    parsedCount++;
                } else {
                    // Parsing failed
                    CommandParseError error = CommandParseError.parsing(arg.name(), parseResult.errorMessage())
                        .withInput(input);

                    if (options.stopOnFirstError()) {
                        return resultBuilder
                            .withErrorAt(error, i)
                            .argumentsParsed(parsedCount)
                            .build();
                    }
                    resultBuilder.withErrorAt(error, i);
                }
            } catch (Throwable t) {
                CommandParseError error = CommandParseError.parsing(arg.name(), "Parse error: " + t.getMessage())
                    .withInput(input);

                if (options.stopOnFirstError()) {
                    return resultBuilder
                        .withErrorAt(error, i)
                        .argumentsParsed(parsedCount)
                        .build();
                }
                resultBuilder.withErrorAt(error, i);
            }

            argIndex++;
        }

        // Parse flags and key-values
        for (Flag flag : flags) {
            resultBuilder.withFlag(flag.name(), false); // Default value
        }

        // Check if parsing is complete
        boolean complete = parsedCount == args.size() ||
                           (options.maxArguments() >= 0 && parsedCount >= options.maxArguments());

        return resultBuilder
            .argumentsParsed(parsedCount)
            .complete(complete && !resultBuilder.build().hasErrors())
            .build();
    }

    /**
     * Parse command arguments partially with default options.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @return the partial parse result
     */
    public @NotNull PartialParseResult parsePartial(@NotNull CommandSender sender,
                                                     @NotNull String label,
                                                     @NotNull String[] providedArgs) {
        return parsePartial(sender, label, providedArgs, PartialParseOptions.DEFAULT);
    }

    /**
     * Parse only the first N arguments.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @param count        number of arguments to parse
     * @return the partial parse result
     */
    public @NotNull PartialParseResult parseFirstN(@NotNull CommandSender sender,
                                                    @NotNull String label,
                                                    @NotNull String[] providedArgs,
                                                    int count) {
        return parsePartial(sender, label, providedArgs, PartialParseOptions.firstN(count));
    }

    /**
     * Parse arguments and stop at the first error.
     *
     * @param sender       the command sender
     * @param label        the command label used
     * @param providedArgs the command arguments
     * @return the partial parse result
     */
    public @NotNull PartialParseResult parseUntilError(@NotNull CommandSender sender,
                                                        @NotNull String label,
                                                        @NotNull String[] providedArgs) {
        return parsePartial(sender, label, providedArgs, PartialParseOptions.STOP_ON_ERROR);
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
            help.append("\n\n").append(messages.helpSectionArguments());
            for (Arg<?> arg : visibleArgs) {
                help.append("\n  §7");
                if (arg.optional()) {
                    help.append("[").append(arg.name()).append("]");
                } else {
                    help.append("<").append(arg.name()).append(">");
                }
                help.append(messages.helpTypeSeparator()).append(arg.parser().getTypeName());
                if (arg.description() != null && !arg.description().isEmpty()) {
                    help.append(messages.helpArgumentPrefix()).append(arg.description());
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
            help.append("\n\n").append(messages.helpSectionFlags());
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
                    help.append(messages.helpTypeSeparator()).append(flag.description());
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
            help.append("\n\n").append(messages.helpSectionOptions());
            for (KeyValue<?> kv : visibleKeyValues) {
                help.append("\n  §7--").append(kv.key()).append("=<value>");
                if (!kv.required()) {
                    if (kv.defaultValue() != null) {
                        help.append(" ").append(messages.helpDefaultIndicator(String.valueOf(kv.defaultValue())));
                    } else {
                        help.append(" ").append(messages.helpOptionalIndicator());
                    }
                } else {
                    help.append(" ").append(messages.helpRequiredIndicator());
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
