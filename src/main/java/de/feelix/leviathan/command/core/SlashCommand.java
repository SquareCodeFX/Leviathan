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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
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
    @Nullable ExceptionHandler exceptionHandler;
    private final long perUserCooldownMillis;
    private final long perServerCooldownMillis;
    private final boolean enableHelp;
    private final int helpPageSize;
    private final String cachedUsage;
    private final MessageProvider messages;
    private final boolean sanitizeInputs;
    private final boolean fuzzySubcommandMatching;
    JavaPlugin plugin;
    private boolean subOnly = false;
    @Nullable private SlashCommand parent = null;


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
                 boolean fuzzySubcommandMatching) {
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
                case '\u0001': case '\u0002': case '\u0003': case '\u0004':
                case '\u0005': case '\u0006': case '\u0007': case '\u0008':
                case '\u000B': case '\u000C': case '\u000E': case '\u000F':
                case '\u0010': case '\u0011': case '\u0012': case '\u0013':
                case '\u0014': case '\u0015': case '\u0016': case '\u0017':
                case '\u0018': case '\u0019': case '\u001A': case '\u001B':
                case '\u001C': case '\u001D': case '\u001E': case '\u001F':
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
                t.printStackTrace();
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
                    plugin.getLogger().severe("Exception handler threw an exception while handling " + errorType + ": " + handlerException.getMessage());
                    handlerException.printStackTrace();
                }
            }
        }
        if (sendErrors && !suppressDefault) {
            sender.sendMessage(message);
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
                List<String> similar = StringSimilarity.findSimilar(first, subcommandNames, 1, 0.6);
                if (!similar.isEmpty()) {
                    sub = subcommands.get(similar.get(0));
                }
            }
            
            if (sub != null) {
                String[] remaining = Arrays.copyOfRange(providedArgs, 1, providedArgs.length);
                try {
                    return sub.execute(sender, sub.name(), remaining);
                } catch (Throwable t) {
                    // Catch any unexpected exception during subcommand execution
                    String errorMsg = messages.subcommandInternalError(first);
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger().severe("Subcommand '" + first + "' threw unexpected exception: " + t.getMessage());
                        t.printStackTrace();
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

        Map<String, Object> values = new LinkedHashMap<>();
        // Validate required arg count first
        int required = (int) args.stream().filter(a -> !a.optional()).count();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();
        if (providedArgs.length < required) {
            sendErrorMessage(
                sender, ErrorType.USAGE, messages.insufficientArguments(fullCommandPath(label), usage()), null);
            return true;
        }

        // Parse arguments using arg index and token index (support greedy last argument)
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < providedArgs.length) {
            Arg<?> arg = args.get(argIndex);
            
            // Evaluate conditional argument
            if (arg.condition() != null) {
                CommandContext tempCtx = new CommandContext(values, providedArgs);
                try {
                    if (!arg.condition().test(tempCtx)) {
                        // Condition is false, skip this argument
                        argIndex++;
                        continue;
                    }
                } catch (Throwable t) {
                    String errorMsg = messages.argumentConditionError(arg.name());
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger().severe("Condition evaluation failed for argument '" + arg.name() + "': " + t.getMessage());
                        t.printStackTrace();
                    }
                    return true;
                }
            }
            
            // Per-argument permission check
            if (arg.permission() != null && !arg.permission().isEmpty() && !sender.hasPermission(arg.permission())) {
                sendErrorMessage(
                    sender, ErrorType.ARGUMENT_PERMISSION,
                    messages.argumentPermissionDenied(arg.name()), null
                );
                return true;
            }
            ArgumentParser<?> parser = arg.parser();
            String token;
            if (argIndex == args.size() - 1 && arg.greedy()) {
                token = String.join(" ", Arrays.asList(providedArgs).subList(tokenIndex, providedArgs.length));
                tokenIndex = providedArgs.length; // consume all remaining tokens
            } else {
                token = providedArgs[tokenIndex++];
            }
            ParseResult<?> res;
            try {
                res = parser.parse(token, sender);
                if (res == null) {
                    throw new ParsingException(
                        "Parser " + parser.getClass().getName() + " returned null ParseResult for argument '" + arg.name()
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
                    plugin.getLogger().severe("Parser " + parser.getClass().getName() + " threw unexpected exception for argument '" + arg.name() + "': " + t.getMessage());
                    t.printStackTrace();
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
                            List<String> suggestions = StringSimilarity.findSimilar(token, argCtx.completionsPredefined());
                            if (!suggestions.isEmpty()) {
                                sender.sendMessage(messages.didYouMean(String.join(", ", suggestions)));
                            }
                        } catch (Throwable t) {
                            // Silently ignore errors in suggestion generation - it's non-critical
                            if (plugin != null) {
                                plugin.getLogger().warning("Failed to generate 'Did you mean' suggestions for argument '" + arg.name() + "': " + t.getMessage());
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
                        plugin.getLogger().severe("Transformation failed for argument '" + arg.name() + "': " + t.getMessage());
                        t.printStackTrace();
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
                    plugin.getLogger().severe("Validation failed with unexpected exception for argument '" + arg.name() + "': " + t.getMessage());
                    t.printStackTrace();
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

        // Apply default values for missing optional arguments
        for (Arg<?> arg : args) {
            if (!values.containsKey(arg.name()) && arg.context().defaultValue() != null) {
                values.put(arg.name(), arg.context().defaultValue());
            }
        }

        // Check for extra arguments after parsing (handles optional args case)
        if (!lastIsGreedy && tokenIndex < providedArgs.length) {
            sendErrorMessage(sender, ErrorType.USAGE, messages.tooManyArguments(fullCommandPath(label), usage()), null);
            return true;
        }

        // Cross-argument validation: validate relationships between multiple arguments
        if (!crossArgumentValidators.isEmpty()) {
            CommandContext tempCtx = new CommandContext(values, providedArgs);
            for (CrossArgumentValidator validator : crossArgumentValidators) {
                String error;
                try {
                    error = validator.validate(tempCtx);
                } catch (Throwable t) {
                    // Catch any unexpected exception during cross-argument validation
                    String errorMsg = messages.crossValidationInternalError();
                    sendErrorMessage(sender, ErrorType.INTERNAL_ERROR, errorMsg, t);
                    if (plugin != null) {
                        plugin.getLogger().severe("Cross-argument validator " + validator.getClass().getName() + " threw unexpected exception: " + t.getMessage());
                        t.printStackTrace();
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
                    plugin.getLogger().warning("Failed to update server cooldown for command '" + name + "': " + t.getMessage());
                }
            }
        }
        if (perUserCooldownMillis > 0) {
            try {
                CooldownManager.updateUserCooldown(name, sender.getName());
            } catch (Throwable t) {
                // Log but don't fail the command if cooldown update fails
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to update user cooldown for command '" + name + "' and user '" + sender.getName() + "': " + t.getMessage());
                }
            }
        }

        // Optional arguments not provided: simply absent from context
        CommandContext ctx = new CommandContext(values, providedArgs);
        if (async) {
            if (asyncActionAdv != null) {
                // Advanced async with cancellation, progress, and timeout
                final CancellationToken token = new CancellationToken();
                final Progress progress = (msg) -> {
                    try {
                        if (plugin != null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                        } else {
                            sender.sendMessage(msg);
                        }
                    } catch (Throwable t) {
                        // Log progress callback errors instead of silently ignoring
                        if (plugin != null) {
                            plugin.getLogger().warning("Failed to send progress message for command '" + name + "': " + t.getMessage());
                        }
                    }
                };
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> {
                        try {
                            asyncActionAdv.execute(sender, ctx, token, progress);
                        } catch (Throwable t) {
                            throw new CommandExecutionException(
                                "Error executing command '" + name + "' asynchronously", t);
                        }
                    });
                if (asyncTimeoutMillis > 0L) {
                    future = future.orTimeout(asyncTimeoutMillis, TimeUnit.MILLISECONDS);
                }
                future.exceptionally(ex -> {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    String msg;
                    ErrorType errorType;
                    if (cause instanceof TimeoutException) {
                        msg = messages.commandTimeout(asyncTimeoutMillis);
                        errorType = ErrorType.TIMEOUT;
                        token.cancel();
                    } else {
                        msg = messages.executionError();
                        errorType = ErrorType.EXECUTION;
                    }
                    boolean suppressDefault = false;
                    if (exceptionHandler != null) {
                        try {
                            suppressDefault = exceptionHandler.handle(sender, errorType, msg, cause);
                        } catch (Throwable handlerException) {
                            // Exception handler itself threw an exception - log it and continue with default behavior
                            String handlerMsg = messages.exceptionHandlerError(handlerException.getMessage());
                            if (plugin != null) {
                                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(handlerMsg));
                                plugin.getLogger().severe("Exception handler threw an exception while handling " + errorType + ": " + handlerException.getMessage());
                                handlerException.printStackTrace();
                            } else {
                                sender.sendMessage(handlerMsg);
                            }
                        }
                    }
                    if (sendErrors && !suppressDefault) {
                        if (plugin != null)
                            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                        else sender.sendMessage(msg);
                    }
                    return null;
                });
            } else {
                // Execute asynchronously using CompletableFuture (no Bukkit scheduler).
                CompletableFuture.runAsync(() -> {
                    try {
                        action.execute(sender, ctx);
                    } catch (Throwable t) {
                        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                        String errorMsg = messages.executionError();
                        boolean suppressDefault = false;
                        if (exceptionHandler != null) {
                            try {
                                suppressDefault = exceptionHandler.handle(sender, ErrorType.EXECUTION, errorMsg, cause);
                            } catch (Throwable handlerException) {
                                // Exception handler itself threw an exception - log it and continue with default behavior
                                sender.sendMessage(messages.exceptionHandlerError(handlerException.getMessage()));
                                if (plugin != null) {
                                    plugin.getLogger().severe("Exception handler threw an exception while handling EXECUTION: " + handlerException.getMessage());
                                    handlerException.printStackTrace();
                                }
                            }
                        }
                        if (sendErrors && !suppressDefault) {
                            sender.sendMessage(errorMsg);
                        }
                        // Log the exception but don't re-throw to prevent unhandled exception in async thread
                        if (plugin != null) {
                            plugin.getLogger().severe("Error executing command '" + name + "' asynchronously: " + cause.getMessage());
                            cause.printStackTrace();
                        }
                    }
                });
            }
        } else {
            try {
                action.execute(sender, ctx);
            } catch (Throwable t) {
                Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                sendErrorMessage(
                    sender, ErrorType.EXECUTION,
                    messages.executionError(), cause
                );
                throw new CommandExecutionException(
                    "Error executing command '" + name + "'", cause);
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
     * @param label the command label used to invoke this command
     * @param pageNumber the page number to display (1-based)
     * @param sender the command sender to send messages to
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
            String usageStr = usage();
            sender.sendMessage(messages.helpUsage(commandPath, usageStr));
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
                plugin.getLogger().severe("Unhandled exception in tab completion for command '" + name + "': " + t.getMessage());
                t.printStackTrace();
            }
            return Collections.emptyList();
        }
    }
}
