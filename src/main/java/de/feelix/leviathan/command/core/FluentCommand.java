package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.async.CancellationToken;
import de.feelix.leviathan.command.async.Progress;
import de.feelix.leviathan.command.completion.TabCompletionHandler;
import de.feelix.leviathan.command.cooldown.CooldownManager;
import de.feelix.leviathan.command.cooldown.CooldownResult;
import de.feelix.leviathan.command.error.ErrorType;
import de.feelix.leviathan.command.error.ExceptionHandler;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.validation.CrossArgumentValidator;
import de.feelix.leviathan.command.validation.ValidationHelper;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.parser.ArgumentParser;
import de.feelix.leviathan.parser.ParseResult;
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
 * created and configured via its {@link FluentCommandBuilder}, then registered against a command declared in plugin
 * .yml.
 */
public final class FluentCommand implements CommandExecutor, TabCompleter {


    /**
     * Create a new builder for a command with the given name.
     *
     * @param name Primary command name as declared in plugin.yml.
     * @return a new FluentCommandBuilder instance
     */
    public static @NotNull FluentCommandBuilder builder(@NotNull String name) {
        return new FluentCommandBuilder(name);
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
    final Map<String, FluentCommand> subcommands;
    private final CommandAction action;
    private final AsyncCommandAction asyncActionAdv;
    private final long asyncTimeoutMillis;
    final List<Guard> guards;
    private final List<CrossArgumentValidator> crossArgumentValidators;
    @Nullable ExceptionHandler exceptionHandler;
    private final long perUserCooldownMillis;
    private final long perServerCooldownMillis;
    private final String cachedUsage;
    JavaPlugin plugin;
    private boolean subOnly = false;


    /**
     * Marks this command instance as a subcommand. Intended for internal use by the Builder
     * when registering subcommands so that attempts to register the subcommand directly
     * can be rejected with a helpful error.
     */
    void markAsSubcommand() {
        this.subOnly = true;
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
     * @return an immutable list of argument definitions
     */
    public @NotNull List<Arg<?>> args() {
        return List.copyOf(args);
    }

    /**
     * @return an immutable map of subcommand aliases to subcommand instances
     */
    public @NotNull Map<String, FluentCommand> subcommands() {
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


    FluentCommand(String name, List<String> aliases, String description, String permission, boolean playerOnly,
                  boolean sendErrors,
                  List<Arg<?>> args, CommandAction action, boolean async, boolean validateOnTab,
                  Map<String, FluentCommand> subcommands,
                  @Nullable AsyncCommandAction asyncActionAdv, long asyncTimeoutMillis,
                  List<Guard> guards, List<CrossArgumentValidator> crossArgumentValidators,
                  @Nullable ExceptionHandler exceptionHandler,
                  long perUserCooldownMillis, long perServerCooldownMillis) {
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
        this.guards = java.util.List.copyOf(guards == null ? java.util.List.of() : guards);
        this.crossArgumentValidators = java.util.List.copyOf(
            crossArgumentValidators == null ? java.util.List.of() : crossArgumentValidators);
        this.exceptionHandler = exceptionHandler;
        this.perUserCooldownMillis = perUserCooldownMillis;
        this.perServerCooldownMillis = perServerCooldownMillis;
        // Pre-compute usage string for performance
        this.cachedUsage = computeUsageString();
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
        return execute(sender, label, providedArgs);
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
                sender.sendMessage("§cError in exception handler: " + handlerException.getMessage());
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
     * (e.g., using {@link FluentCommandBuilder#argCommandChoices(String, Map)}).
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
            sendErrorMessage(sender, ErrorType.PERMISSION, "Â§cYou don't have permission to use this command.", null);
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            sendErrorMessage(sender, ErrorType.PLAYER_ONLY, "Â§cThis command can only be used by players.", null);
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
                sendErrorMessage(sender, ErrorType.GUARD_FAILED, "Â§cYou cannot use this command.", t);
                return true;
            }
        }

        // Cooldown checks
        CooldownResult serverCooldown = CooldownManager.checkServerCooldown(
            name, perServerCooldownMillis);
        if (serverCooldown.onCooldown()) {
            String errorMsg = CooldownManager.formatCooldownMessage(
                "Â§cThis command is on cooldown. Please wait %s.", serverCooldown.remainingMillis());
            sendErrorMessage(sender, ErrorType.GUARD_FAILED, errorMsg, null);
            return true;
        }

        CooldownResult userCooldown = CooldownManager.checkUserCooldown(
            name, sender.getName(), perUserCooldownMillis);
        if (userCooldown.onCooldown()) {
            String errorMsg = CooldownManager.formatCooldownMessage(
                "Â§cYou must wait %s before using this command again.", userCooldown.remainingMillis());
            sendErrorMessage(sender, ErrorType.GUARD_FAILED, errorMsg, null);
            return true;
        }

        // Automatic subcommand routing: if the first token matches a registered subcommand, delegate to it
        if (!subcommands.isEmpty() && providedArgs.length >= 1) {
            String first = providedArgs[0].toLowerCase(Locale.ROOT);
            FluentCommand sub = subcommands.get(first);
            if (sub != null) {
                String[] remaining = Arrays.copyOfRange(providedArgs, 1, providedArgs.length);
                return sub.execute(sender, sub.name(), remaining);
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        // Validate required arg count first
        int required = (int) args.stream().filter(a -> !a.optional()).count();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();
        if (providedArgs.length < required) {
            sendErrorMessage(sender, ErrorType.USAGE, "Â§cUsage: /" + label + " " + usage(), null);
            return true;
        }
        if (!lastIsGreedy && providedArgs.length > args.size()) {
            sendErrorMessage(sender, ErrorType.USAGE, "Â§cToo many arguments. Usage: /" + label + " " + usage(), null);
            return true;
        }

        // Parse arguments using arg index and token index (support greedy last argument)
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < providedArgs.length) {
            Arg<?> arg = args.get(argIndex);
            // Per-argument permission check
            if (arg.permission() != null && !arg.permission().isEmpty() && !sender.hasPermission(arg.permission())) {
                sendErrorMessage(
                    sender, ErrorType.ARGUMENT_PERMISSION,
                    "Â§cYou don't have permission to use argument '" + arg.name() + "'.", null
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
            ParseResult<?> res = parser.parse(token, sender);
            if (res == null) {
                throw new ParsingException(
                    "Parser " + parser.getClass().getName() + " returned null ParseResult for argument '" + arg.name()
                    + "'");
            }
            if (!res.isSuccess()) {
                String msg = res.error().orElse("invalid value");
                String errorMsg =
                    "Â§cInvalid value for '" + arg.name() + "' (expected " + parser.getTypeName() + "): " + msg;
                sendErrorMessage(sender, ErrorType.PARSING, errorMsg, null);

                // Did-You-Mean suggestions (only shown if main error was sent)
                if (sendErrors) {
                    ArgContext argCtx = arg.context();
                    if (argCtx.didYouMean() && !argCtx.completionsPredefined().isEmpty()) {
                        List<String> suggestions = StringSimilarity.findSimilar(token, argCtx.completionsPredefined());
                        if (!suggestions.isEmpty()) {
                            sender.sendMessage("Â§eDid you mean: " + String.join(", ", suggestions) + "?");
                        }
                    }
                }
                return true;
            }
            Object parsedValue = res.value().orElse(null);

            // Apply validations from ArgContext
            ArgContext ctx = arg.context();
            String validationError = ValidationHelper.validateValue(parsedValue, ctx, arg.name(), parser.getTypeName());
            if (validationError != null) {
                sendErrorMessage(
                    sender, ErrorType.VALIDATION,
                    "Â§cInvalid value for '" + arg.name() + "': " + validationError, null
                );
                return true;
            }

            values.put(arg.name(), parsedValue);
            argIndex++;
        }

        // Cross-argument validation: validate relationships between multiple arguments
        if (!crossArgumentValidators.isEmpty()) {
            CommandContext tempCtx = new CommandContext(values, providedArgs);
            for (CrossArgumentValidator validator : crossArgumentValidators) {
                String error = validator.validate(tempCtx);
                if (error != null) {
                    sendErrorMessage(sender, ErrorType.CROSS_VALIDATION, "Â§cValidation error: " + error, null);
                    return true;
                }
            }
        }

        // Update cooldown tracking after successful validation
        if (perServerCooldownMillis > 0) {
            CooldownManager.updateServerCooldown(name);
        }
        if (perUserCooldownMillis > 0) {
            CooldownManager.updateUserCooldown(name, sender.getName());
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
                    } catch (Throwable ignored) {
                    }
                };
                java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(
                    () -> {
                        try {
                            asyncActionAdv.execute(sender, ctx, token, progress);
                        } catch (Throwable t) {
                            throw new de.feelix.leviathan.exceptions.CommandExecutionException(
                                "Error executing command '" + name + "' asynchronously", t);
                        }
                    });
                if (asyncTimeoutMillis > 0L) {
                    future = future.orTimeout(asyncTimeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
                future.exceptionally(ex -> {
                    Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                    String msg;
                    ErrorType errorType;
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        msg = "Â§cCommand timed out after " + asyncTimeoutMillis + " ms.";
                        errorType = ErrorType.TIMEOUT;
                        token.cancel();
                    } else {
                        msg = "Â§cAn internal error occurred while executing this command.";
                        errorType = ErrorType.EXECUTION;
                    }
                    boolean suppressDefault = false;
                    if (exceptionHandler != null) {
                        try {
                            suppressDefault = exceptionHandler.handle(sender, errorType, msg, cause);
                        } catch (Throwable handlerException) {
                            // Exception handler itself threw an exception - log it and continue with default behavior
                            String handlerMsg = "§cError in exception handler: " + handlerException.getMessage();
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
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        action.execute(sender, ctx);
                    } catch (Throwable t) {
                        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                        String errorMsg = "Â§cAn internal error occurred while executing this command.";
                        boolean suppressDefault = false;
                        if (exceptionHandler != null) {
                            try {
                                suppressDefault = exceptionHandler.handle(sender, ErrorType.EXECUTION, errorMsg, cause);
                            } catch (Throwable handlerException) {
                                // Exception handler itself threw an exception - log it and continue with default behavior
                                sender.sendMessage("§cError in exception handler: " + handlerException.getMessage());
                                if (plugin != null) {
                                    plugin.getLogger().severe("Exception handler threw an exception while handling EXECUTION: " + handlerException.getMessage());
                                    handlerException.printStackTrace();
                                }
                            }
                        }
                        if (sendErrors && !suppressDefault) {
                            sender.sendMessage(errorMsg);
                        }
                        throw new de.feelix.leviathan.exceptions.CommandExecutionException(
                            "Error executing command '" + name + "' asynchronously", cause);
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
                    "Â§cAn internal error occurred while executing this command.", cause
                );
                throw new de.feelix.leviathan.exceptions.CommandExecutionException(
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
     * Provide tab-completion options for the current argument token.
     * Respects command-level and per-argument permissions, and can optionally
     * validate previously typed tokens when {@link FluentCommandBuilder#validateOnTab(boolean)} is enabled.
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] providedArgs) {
        return TabCompletionHandler.generateCompletions(sender, alias, providedArgs, this);
    }
}
