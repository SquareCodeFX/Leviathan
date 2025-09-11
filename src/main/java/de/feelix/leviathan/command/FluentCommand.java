package de.feelix.leviathan.command;

import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.parser.ArgParsers;
import de.feelix.leviathan.parser.ArgumentParser;
import de.feelix.leviathan.parser.ParseResult;
import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
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
 * created and configured via its {@link Builder}, then registered against a command declared in plugin.yml.
 */
public final class FluentCommand implements CommandExecutor, TabCompleter {

    /**
     * Functional interface representing the action to execute when a command invocation
     * has been successfully parsed and validated.
     */
    @FunctionalInterface
    public interface CommandAction {
        /**
         * Execute the command action.
         *
         * @param sender The command sender.
         * @param ctx    Parsed argument context.
         */
        void execute(@NotNull CommandSender sender, @NotNull CommandContext ctx);
    }

    /**
     * Create a new builder for a command with the given name.
     *
     * @param name Primary command name as declared in plugin.yml.
     * @return a new Builder instance
     */
    public static @NotNull Builder builder(@NotNull String name) {
        return new Builder(name);
    }

    /**
     * Builder for {@link FluentCommand}. Provides a fluent API to define arguments,
     * permissions, behavior, and registration.
     */
    public static final class Builder {
        private final String name;
        private String description = "";
        private String permission = null;
        private boolean playerOnly = false;
        private boolean sendErrors = true;
        private boolean async = false;
        private boolean validateOnTab = false;
        private final List<Arg<?>> args = new ArrayList<>();
        private final Map<String, FluentCommand> subcommands = new LinkedHashMap<>();
        private CommandAction action = (s, c) -> {};

        private Builder(String name) {
            this.name = Preconditions.checkNotNull(name, "name");
        }

        /**
         * Set a human-readable description for this command.
         * @param description Description text used for help/usage pages.
         * @return this builder
         */
        public @NotNull Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the command-level permission required to run this command.
         * If {@code null} or blank, no permission is required.
         * @param permission Bukkit permission node
         * @return this builder
         */
        public @NotNull Builder permission(@Nullable String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Restrict the command to players only (not console).
         * @param playerOnly true to allow only players to execute
         * @return this builder
         */
        public @NotNull Builder playerOnly(boolean playerOnly) {
            this.playerOnly = playerOnly;
            return this;
        }

        /**
         * Control whether user-facing error messages should be sent when parsing fails
         * or when the user lacks permissions.
         * @param send true to send error messages to the sender
         * @return this builder
         */
        public @NotNull Builder sendErrors(boolean send) {
            this.sendErrors = send;
            return this;
        }

        /**
         * Add a required int argument.
         * @param name argument name (no whitespace)
         * @return this builder
         */
        public @NotNull Builder argInt(@NotNull String name) {
            return arg(new Arg<>(name, false, ArgParsers.intParser()));
        }

        /**
         * Add a required long argument.
         * @param name argument name (no whitespace)
         * @return this builder
         */
        public @NotNull Builder argLong(@NotNull String name) {
            return arg(new Arg<>(name, false, ArgParsers.longParser()));
        }

        /**
         * Add a required string argument (single token).
         * @param name argument name (no whitespace)
         * @return this builder
         */
        public @NotNull Builder argString(@NotNull String name) {
            return arg(new Arg<>(name, false, ArgParsers.stringParser()));
        }


        /**
         * Add a required UUID argument.
         * @param name argument name (no whitespace)
         * @return this builder
         */
        public @NotNull Builder argUUID(@NotNull String name) {
            return arg(new Arg<>(name, false, ArgParsers.uuidParser()));
        }

        /**
         * Add a greedy trailing string argument. If this argument is last, it captures the
         * remainder of the command line (including spaces). May be marked optional via {@link #optional()}.
         * @param name argument name (no whitespace)
         * @return this builder
         */
        public @NotNull Builder argGreedyString(@NotNull String name) {
            return arg(new Arg<>(name, false, ArgParsers.stringParser(), null, true));
        }

        /**
         * Enable or disable validation of previously entered arguments during tab completion.
         * When enabled, if a prior argument cannot be parsed, tab suggestions will be hidden and,
         * if {@link #sendErrors(boolean)} is true, an error message will be sent to the sender.
         * @param validate true to validate tokens typed before the current one
         * @return this builder
         */
        public @NotNull Builder validateOnTab(boolean validate) {
            this.validateOnTab = validate;
            return this;
        }

        /**
         * Add a required choice argument from a fixed set of aliases mapping to values.
         * @param name argument name
         * @param choices mapping from alias (case-insensitive) to value
         * @param displayType type name shown in error messages (e.g., "gamemode")
         * @return this builder
         */
        public <T> @NotNull Builder argChoices(@NotNull String name, @NotNull Map<String, T> choices, @NotNull String displayType) {
            return arg(new Arg<>(name, false, ArgParsers.choices(choices, displayType)));
        }

        /**
         * Convenience for sub-commands: treat a token as a choice mapping to another {@link FluentCommand}.
         * @param name argument name
         * @param choices mapping from alias (case-insensitive) to a sub-command instance
         * @return this builder
         */
        public @NotNull Builder argCommandChoices(@NotNull String name, @NotNull Map<String, FluentCommand> choices) {
            return arg(new Arg<>(name, false, ArgParsers.choices(choices, "command")));
        }

        /**
         * Add an argument that accepts a value parsed by one of the provided parsers.
         * Error messages will use the supplied {@code displayType} label.
         * @param name argument name
         * @param displayType label for error messages (e.g., "uuid")
         * @param parsers candidate parsers (at least one)
         * @return this builder
         */
        @SafeVarargs
        public final <T> @NotNull Builder argOneOf(@NotNull String name, @NotNull String displayType, @NotNull ArgumentParser<? extends T>... parsers) {
            return arg(new Arg<>(name, false, ArgParsers.oneOf(displayType, parsers)));
        }

        /**
         * Mark the most recently added argument as optional. Optional arguments must
         * come after all required arguments.
         * @return this builder
         * @throws CommandConfigurationException if there is no argument to mark or ordering is invalid
         */
        public @NotNull Builder optional() {
            if (args.isEmpty()) throw new CommandConfigurationException("No argument to mark optional");
            Arg<?> last = args.remove(args.size() - 1);
            args.add(last.optional(true));
            return this;
        }

        /**
         * Assign a permission to the most recently added argument. During execution and
         * tab completion, the argument will be hidden/denied if the sender lacks this permission.
         * @param permission permission node (must not be blank)
         * @return this builder
         */
        public @NotNull Builder argPermission(@NotNull String permission) {
            if (args.isEmpty()) throw new CommandConfigurationException("No argument to set permission for");
            Preconditions.checkNotNull(permission, "permission");
            if (permission.trim().isEmpty()) {
                throw new CommandConfigurationException("Argument permission must not be blank");
            }
            Arg<?> last = args.remove(args.size() - 1);
            args.add(last.withPermission(permission));
            return this;
        }

        /**
         * Add a fully constructed {@link Arg} (advanced use).
         * @param arg the argument descriptor
         * @return this builder
         */
        public @NotNull Builder arg(@NotNull Arg<?> arg) {
            args.add(Preconditions.checkNotNull(arg, "arg"));
            return this;
        }

        /**
         * Define the action that should run when the command is executed.
         * @param action callback invoked with the sender and parsed context
         * @return this builder
         */
        public @NotNull Builder executes(@NotNull CommandAction action) {
            this.action = Preconditions.checkNotNull(action, "action");
            return this;
        }

        /**
         * Register a subcommand under a specific alias. The alias is matched case-insensitively.
         * If you want to use the subcommand's own name as the alias, use {@link #sub(FluentCommand...)}.
         *
         * @param alias Alias used as the first token to route to this subcommand (must be non-blank).
         * @param sub   The subcommand definition (must not be null). It does not need to be registered in plugin.yml.
         * @return this builder
         * @throws CommandConfigurationException if alias is blank, sub is null, or a duplicate alias is added
         */
        public @NotNull Builder sub(@NotNull String alias, @NotNull FluentCommand sub) {
            Preconditions.checkNotNull(alias, "alias");
            Preconditions.checkNotNull(sub, "sub");
            if (alias.trim().isEmpty()) {
                throw new CommandConfigurationException("Subcommand alias must not be blank");
            }
            String key = alias.toLowerCase(Locale.ROOT);
            if (subcommands.containsKey(key)) {
                throw new CommandConfigurationException("Duplicate subcommand alias: '" + alias + "'");
            }
            sub.markAsSubcommand();
            subcommands.put(key, sub);
            return this;
        }

        /**
         * Register one or more subcommands using each subcommand's own {@link FluentCommand#getName()} as the alias.
         * Aliases are matched case-insensitively.
         *
         * @param subs Subcommands to register (must not contain nulls).
         * @return this builder
         * @throws CommandConfigurationException if any sub is null or duplicate aliases are detected
         */
        public @NotNull Builder sub(@Nullable FluentCommand... subs) {
            if (subs == null) return this;
            for (FluentCommand sc : subs) {
                if (sc == null) {
                    throw new CommandConfigurationException("Subcommand must not be null");
                }
                String alias = sc.getName();
                if (alias == null || alias.trim().isEmpty()) {
                    throw new CommandConfigurationException("Subcommand has a blank name");
                }
                String key = alias.toLowerCase(Locale.ROOT);
                if (subcommands.containsKey(key)) {
                    throw new CommandConfigurationException("Duplicate subcommand alias: '" + alias + "'");
                }
                sc.markAsSubcommand();
                subcommands.put(key, sc);
            }
            return this;
        }

        /**
         * Configure whether the action should run asynchronously via {@link CompletableFuture}.
         * @param async true to execute off the main thread
         * @return this builder
         */
        public @NotNull Builder async(boolean async) {
            this.async = async;
            return this;
        }

        /**
         * Shorthand to set the action and enable asynchronous execution in one call.
         * @param action callback invoked with the sender and parsed context
         * @return this builder
         */
        public @NotNull Builder executesAsync(@NotNull CommandAction action) {
            this.action = Preconditions.checkNotNull(action, "action");
            this.async = true;
            return this;
        }

        /**
         * Validate the configuration and build the immutable {@link FluentCommand}.
         * @return the configured command instance
         * @throws CommandConfigurationException if configuration is invalid (e.g., duplicate arg names,
         *                                       required-after-optional, bad greedy placement)
         * @throws ParsingException if a parser violates its contract (e.g., blank type name)
         */
        public @NotNull FluentCommand build() {
            if (name.trim().isEmpty()) {
                throw new CommandConfigurationException("Command name must not be blank");
            }
            // Normalize blank command permission
            if (permission != null && permission.trim().isEmpty()) {
                permission = null;
            }
            // Validate arguments ordering and uniqueness
            boolean seenOptional = false;
            Set<String> seenNames = new HashSet<>();
            for (Arg<?> a : args) {
                String n = a.name().toLowerCase(Locale.ROOT);
                if (!seenNames.add(n)) {
                    throw new CommandConfigurationException("Duplicate argument name: '" + a.name() + "'");
                }
                if (a.optional()) {
                    seenOptional = true;
                } else if (seenOptional) {
                    throw new CommandConfigurationException(
                        "Required argument '" + a.name() + "' cannot appear after an optional argument");
                }
                // Check parser type names are provided for better error messages
                String typeName = a.parser().getTypeName();
                if (typeName == null || typeName.trim().isEmpty()) {
                    throw new ParsingException("Parser " + a.parser().getClass().getName()
                            + " returned a null/blank type name for argument '" + a.name() + "'");
                }
            }
            if (action == null) {
                throw new CommandConfigurationException("Command action must not be null");
            }
            // Validate greedy constraints: if any arg is greedy, it must be the last and be a string parser
            for (int i = 0; i < args.size(); i++) {
                Arg<?> a = args.get(i);
                if (a.greedy()) {
                    if (i != args.size() - 1) {
                        throw new CommandConfigurationException(
                            "Greedy argument '" + a.name() + "' must be the last argument");
                    }
                    if (!"string".equalsIgnoreCase(a.parser().getTypeName())) {
                        throw new CommandConfigurationException(
                            "Greedy argument '" + a.name() + "' must use a string parser");
                    }
                }
            }
            // Validate subcommand aliases (already checked on add), create immutable copy with lower-case keys
            Map<String, FluentCommand> subs = new LinkedHashMap<>();
            for (Map.Entry<String, FluentCommand> e : subcommands.entrySet()) {
                String k = e.getKey();
                if (k == null || k.trim().isEmpty() || e.getValue() == null) {
                    throw new CommandConfigurationException("Invalid subcommand entry");
                }
                String low = k.toLowerCase(Locale.ROOT);
                if (subs.put(low, e.getValue()) != null) {
                    throw new CommandConfigurationException("Duplicate subcommand alias: '" + k + "'");
                }
            }
            return new FluentCommand(
                name, description, permission, playerOnly, sendErrors, args, action, async, validateOnTab, subs);
        }

        /**
         * Build and register this command instance as executor and tab-completer for the
         * command with the same name declared in plugin.yml. Also stores the plugin instance
         * inside the command for potential future use.
         * @param plugin plugin registering the command (must declare the command in plugin.yml)
         * @throws CommandConfigurationException if the command is not declared in plugin.yml
         */
        public void register(@NotNull JavaPlugin plugin) {
            Preconditions.checkNotNull(plugin, "plugin");
            FluentCommand cmd = build();
            cmd.plugin = plugin;
            org.bukkit.command.PluginCommand pc = plugin.getCommand(name);
            if (pc == null) {
                throw new CommandConfigurationException("Command not declared in plugin.yml: " + name);
            }
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
    }

    private final String name;
    private final String description;
    private final String permission;
    private final boolean playerOnly;
    private final boolean sendErrors;
    private final boolean async;
    private final boolean validateOnTab;
    private final List<Arg<?>> args;
    private final Map<String, FluentCommand> subcommands; // lower-case alias -> subcommand
    private final CommandAction action;
    private JavaPlugin plugin; // set during register()
    private boolean subOnly = false; // marked when attached as a subcommand of another command

    // Package-private: used by Builder.sub(...) to mark that this command is intended as a subcommand
    void markAsSubcommand() { this.subOnly = true; }

    /**
     * Register this already-built command instance as executor and tab-completer for the
     * command with the same name declared in plugin.yml. This is intended for root commands only.
     * If this command was added as a subcommand to another command, calling this will throw.
     *
     * @param plugin plugin registering the command (must declare the command in plugin.yml)
     * @throws ApiMisuseException if this command is marked as a subcommand
     * @throws CommandConfigurationException if the command is not declared in plugin.yml
     */
    public void register(@NotNull JavaPlugin plugin) {
        Preconditions.checkNotNull(plugin, "plugin");
        if (subOnly) {
            throw new ApiMisuseException("Subcommand '" + name + "' must not be registered directly. Register only the root command containing it.");
        }
        this.plugin = plugin;
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
    public @NotNull String getDescription() {
        return description;
    }

    /**
     * @return the primary command name
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * @return the command-level permission node, or null if none
     */
    public @Nullable String getPermission() {
        return permission;
    }

    private FluentCommand(String name, String description, String permission, boolean playerOnly, boolean sendErrors,
                         List<Arg<?>> args, CommandAction action, boolean async, boolean validateOnTab,
                         Map<String, FluentCommand> subcommands) {
        this.name = Preconditions.checkNotNull(name, "name");
        this.description = (description == null) ? "" : description;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.sendErrors = sendErrors;
        this.async = async;
        this.validateOnTab = validateOnTab;
        this.args = List.copyOf(Preconditions.checkNotNull(args, "args"));
        this.subcommands = Map.copyOf(Preconditions.checkNotNull(subcommands, "subcommands"));
        this.action = Preconditions.checkNotNull(action, "action");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(command, "command");
        Preconditions.checkNotNull(label, "label");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        return execute(sender, label, providedArgs);
    }

    /**
     * Programmatically execute this command with the given label and arguments.
     * Useful for dispatching to a selected sub-command obtained via a choice argument
     * (e.g., using {@link Builder#argCommandChoices(String, Map)}).
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
            if (sendErrors) sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            if (sendErrors) sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        // Automatic subcommand routing: if first token matches a registered subcommand, delegate to it
        if (!subcommands.isEmpty() && providedArgs.length >= 1) {
            String first = providedArgs[0].toLowerCase(Locale.ROOT);
            FluentCommand sub = subcommands.get(first);
            if (sub != null) {
                String[] remaining = Arrays.copyOfRange(providedArgs, 1, providedArgs.length);
                return sub.execute(sender, sub.getName(), remaining);
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        // Validate required arg count first
        int required = (int) args.stream().filter(a -> !a.optional()).count();
        boolean lastIsGreedy = !args.isEmpty() && args.get(args.size() - 1).greedy();
        if (providedArgs.length < required) {
            if (sendErrors) sender.sendMessage("§cUsage: /" + label + " " + usage());
            return true;
        }
        if (!lastIsGreedy && providedArgs.length > args.size()) {
            if (sendErrors) sender.sendMessage("§cToo many arguments. Usage: /" + label + " " + usage());
            return true;
        }

        // Parse arguments using arg index and token index (support greedy last argument)
        int argIndex = 0;
        int tokenIndex = 0;
        while (argIndex < args.size() && tokenIndex < providedArgs.length) {
            Arg<?> arg = args.get(argIndex);
            // Per-argument permission check
            if (arg.permission() != null && !arg.permission().isEmpty() && !sender.hasPermission(arg.permission())) {
                if (sendErrors) sender.sendMessage("§cYou don't have permission to use argument '" + arg.name() + "'.");
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
                if (sendErrors) {
                    String msg = res.error().orElse("invalid value");
                    sender.sendMessage(
                        "§cInvalid value for '" + arg.name() + "' (expected " + parser.getTypeName() + "): " + msg);
                }
                return true;
            }
            values.put(arg.name(), res.value().orElse(null));
            argIndex++;
        }

        // Optional arguments not provided: simply absent from context
        CommandContext ctx = new CommandContext(values, providedArgs);
        if (async) {
            // Execute asynchronously using CompletableFuture (no Bukkit scheduler).
            CompletableFuture.runAsync(() -> {
                try {
                    action.execute(sender, ctx);
                } catch (Throwable t) {
                    if (sendErrors) sender.sendMessage("§cAn internal error occurred while executing this command.");
                    Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                    throw new de.feelix.leviathan.exceptions.CommandExecutionException(
                        "Error executing command '" + name + "' asynchronously", cause);
                }
            });
        } else {
            try {
                action.execute(sender, ctx);
            } catch (Throwable t) {
                if (sendErrors) sender.sendMessage("§cAn internal error occurred while executing this command.");
                Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                throw new de.feelix.leviathan.exceptions.CommandExecutionException(
                    "Error executing command '" + name + "'", cause);
            }
        }
        return true;
    }

    /**
     * Build a usage string based on the configured arguments.
     */
    private @NotNull String usage() {
        if (!subcommands.isEmpty() && args.isEmpty()) {
            return "<subcommand>";
        }
        return args.stream()
            .map(a -> a.optional() ? ("[" + a.name() + "]") : ("<" + a.name() + ">"))
            .collect(Collectors.joining(" "));
    }

    /**
     * Provide tab-completion options for the current argument token.
     * Respects command-level and per-argument permissions, and can optionally
     * validate previously typed tokens when {@link Builder#validateOnTab(boolean)} is enabled.
     */
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] providedArgs) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(command, "command");
        Preconditions.checkNotNull(alias, "alias");
        Preconditions.checkNotNull(providedArgs, "providedArgs");
        // Gate completions by command-level permission and player-only constraint
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        if (playerOnly && !(sender instanceof Player)) {
            return Collections.emptyList();
        }

        // Subcommand-aware completion at the first token
        if (!subcommands.isEmpty()) {
            if (providedArgs.length == 0) {
                return Collections.emptyList();
            }
            String first = providedArgs[0];
            String firstLow = first.toLowerCase(Locale.ROOT);
            FluentCommand sub = subcommands.get(firstLow);
            if (providedArgs.length == 1) {
                // Suggest subcommand aliases, filtered by permission
                List<String> names = new ArrayList<>();
                for (Map.Entry<String, FluentCommand> e : subcommands.entrySet()) {
                    String perm = e.getValue().getPermission();
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
                return sub.onTabComplete(sender, command, sub.getName(), remaining);
            }
            // If first token doesn't match a subcommand, fall through to this command's own args
        }

        int index = providedArgs.length - 1; // current token index
        if (index < 0) return Collections.emptyList();
        if (args.isEmpty()) return Collections.emptyList();

        boolean lastIsGreedy = args.get(args.size() - 1).greedy();
        int argCount = args.size();

        // Determine current argument index, accounting for greedy last argument
        int currentArgIndex;
        if (index >= argCount) {
            if (!lastIsGreedy) {
                // Too many arguments typed; optionally inform when validation is enabled
                if (validateOnTab && sendErrors) {
                    sender.sendMessage("§cToo many arguments. Usage: /" + alias + " " + usage());
                }
                return Collections.emptyList();
            }
            currentArgIndex = argCount - 1; // all extra tokens belong to the greedy last arg
        } else {
            currentArgIndex = index;
        }

        // Validate previously entered arguments (type-check) if enabled
        if (validateOnTab && currentArgIndex > 0) {
            for (int i = 0; i < currentArgIndex; i++) {
                Arg<?> prev = args.get(i);
                // If a previous argument requires a permission and the sender lacks it, stop suggesting
                if (prev.permission() != null && !prev.permission().isEmpty() && !sender.hasPermission(prev.permission())) {
                    return Collections.emptyList();
                }
                String token = providedArgs[i];
                ParseResult<?> res = prev.parser().parse(token, sender);
                if (res == null) {
                    throw new ParsingException(
                        "Parser " + prev.parser().getClass().getName() + " returned null ParseResult for argument '"
                        + prev.name() + "'");
                }
                if (!res.isSuccess()) {
                    if (sendErrors) {
                        String msg = res.error().orElse("invalid value");
                        sender.sendMessage(
                            "§cInvalid value for '" + prev.name() + "' (expected " + prev.parser().getTypeName() + "): "
                            + msg);
                    }
                    return Collections.emptyList();
                }
            }
        }

        Arg<?> current = args.get(currentArgIndex);
        // If the current argument requires a permission and the sender lacks it, hide completions
        if (current.permission() != null && !current.permission().isEmpty() && !sender.hasPermission(current.permission())) {
            return Collections.emptyList();
        }

        String prefix;
        if (lastIsGreedy && currentArgIndex == argCount - 1) {
            // Join all tokens that belong to the greedy argument as the current prefix
            int greedyStart = argCount - 1;
            if (index < greedyStart) {
                prefix = ""; // safety
            } else {
                prefix = String.join(" ", Arrays.asList(providedArgs).subList(greedyStart, index + 1));
            }
        } else {
            prefix = providedArgs[index];
        }

        List<String> suggestions = current.parser().complete(prefix, sender);
        if (suggestions == null) {
            throw new ParsingException(
                "Parser " + current.parser().getClass().getName() + " returned null suggestions for argument '"
                + current.name() + "'");
        }
        return suggestions;
    }
}
