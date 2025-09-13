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
     * Functional interface for providing dynamic tab completions per-argument.
     * Implementations may inspect the sender, command alias, full provided args,
     * and current token prefix to compute suggestions.
     */
    @FunctionalInterface
    public interface CompletionProvider {
        /**
         * Compute completion suggestions for the current argument token.
         *
         * @param sender       the command sender requesting completions
         * @param alias        the label or alias used
         * @param providedArgs the full array of tokens typed so far
         * @param prefix       the current token prefix to complete
         * @return non-null list of suggestion strings (may be empty)
         */
        @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String alias,
                                       @NotNull String[] providedArgs, @NotNull String prefix);
    }

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
     * Extended asynchronous command action supporting cancellation and progress reporting.
     * Use via Builder.executesAsync(AsyncCommandAction, timeoutMillis).
     */
    @FunctionalInterface
    public interface AsyncCommandAction {
        void execute(@NotNull CommandSender sender,
                     @NotNull CommandContext ctx,
                     @NotNull CancellationToken token,
                     @NotNull Progress progress);
    }

    /**
     * Cancellation token for cooperative cancellation of async command actions.
     */
    public static final class CancellationToken {
        private volatile boolean cancelled;
        public void cancel() { this.cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    /**
     * Progress reporter for long-running async actions. Implementations should be thread-safe.
     */
    @FunctionalInterface
    public interface Progress {
        void report(@NotNull String message);
    }

    /**
     * Asynchronous completion provider. The returned future should complete quickly; a timeout and
     * debounce are applied by FluentCommand.
     */
    @FunctionalInterface
    public interface AsyncCompletionProvider {
        /**
         * Compute completion suggestions asynchronously.
         * The returned future should complete quickly. A debounce window and timeout
         * are applied by {@link FluentCommand}.
         *
         * @param sender       the command sender requesting completions
         * @param alias        the label or alias used
         * @param providedArgs the full array of tokens typed so far
         * @param prefix       the current token prefix to complete (may be empty)
         * @return a non-null future that completes with a list of suggestions (may be empty)
         */
        @NotNull CompletableFuture<List<String>> completeAsync(@NotNull CommandSender sender,
                                                               @NotNull String alias,
                                                               @NotNull String[] providedArgs,
                                                               @NotNull String prefix);
    }

    /**
     * Declarative guard that must pass before command execution/tab completion.
     */
    public interface Guard {
        /**
         * Evaluates whether the given sender is allowed to proceed (for execution and tab-complete).
         *
         * @param sender the command sender
         * @return true if permitted; false to block
         */
        boolean test(@NotNull CommandSender sender);
        /**
         * Optional human-readable error message sent to the sender when {@link #test(CommandSender)} returns false.
         *
         * @return non-null message string
         */
        default @NotNull String errorMessage() { return "§cYou cannot use this command."; }
    }

    // Helper guards
    public static @NotNull Guard permission(@NotNull String perm) {
        Preconditions.checkNotNull(perm, "perm");
        return new Guard() {
            @Override public boolean test(@NotNull CommandSender sender) { return sender.hasPermission(perm); }
            @Override public @NotNull String errorMessage() { return "§cYou lack permission: " + perm; }
        };
    }
    public static @NotNull Guard inWorld(@NotNull String worldName) {
        Preconditions.checkNotNull(worldName, "worldName");
        return new Guard() {
            @Override public boolean test(@NotNull CommandSender sender) {
                if (!(sender instanceof Player)) return false;
                Player p = (Player) sender;
                return p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(worldName);
            }
            @Override public @NotNull String errorMessage() { return "§cYou must be in world '" + worldName + "'."; }
        };
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
        // Async advanced
        private AsyncCommandAction asyncAction = null;
        private Long asyncTimeoutMillis = null;
        // Help & examples
        private final List<String> examples = new ArrayList<>();
        private int helpPageSize = 8;
        private boolean autoHelp = true;
        // Guards
        private final List<Guard> guards = new ArrayList<>();
        // Per-argument static completions and dynamic providers (by argument index)
        private final Map<Integer, List<String>> completionsByIndex = new HashMap<>();
        private final Map<Integer, CompletionProvider> providersByIndex = new HashMap<>();
        private final Map<Integer, AsyncCompletionProvider> asyncProvidersByIndex = new HashMap<>();
        // Async completion tuning
        private long completionDebounceMillis = 75L;
        private long completionTimeoutMillis = 250L;

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
         * Register static tab completions for the most recently added argument.
         * Replaces any previously configured static completions for that argument index.
         */
        public @NotNull Builder completions(@NotNull String... suggestions) {
            Preconditions.checkNotNull(suggestions, "suggestions");
            return completions(Arrays.asList(suggestions));
        }

        /**
         * Register static tab completions for the most recently added argument.
         * Replaces any previously configured static completions for that argument index.
         */
        public @NotNull Builder completions(@NotNull Collection<String> suggestions) {
            if (args.isEmpty()) throw new CommandConfigurationException("No argument to set completions for");
            Preconditions.checkNotNull(suggestions, "suggestions");
            int idx = args.size() - 1;
            completionsByIndex.put(idx, new ArrayList<>(suggestions));
            return this;
        }

        /**
         * Set a dynamic completion provider for the most recently added argument.
         * Overrides any static completions when present.
         */
        public @NotNull Builder completionProvider(@NotNull CompletionProvider provider) {
            if (args.isEmpty()) throw new CommandConfigurationException("No argument to set completion provider for");
            providersByIndex.put(args.size() - 1, Preconditions.checkNotNull(provider, "provider"));
            return this;
        }

        /**
         * Register static tab completions for a specific argument index (0-based).
         */
        public @NotNull Builder completionsForArg(int index, @NotNull Collection<String> suggestions) {
            if (index < 0) throw new CommandConfigurationException("Argument index must be >= 0");
            Preconditions.checkNotNull(suggestions, "suggestions");
            completionsByIndex.put(index, new ArrayList<>(suggestions));
            return this;
        }

        /**
         * Set a dynamic completion provider for a specific argument index (0-based).
         */
        public @NotNull Builder completionProviderForArg(int index, @NotNull CompletionProvider provider) {
            if (index < 0) throw new CommandConfigurationException("Argument index must be >= 0");
            providersByIndex.put(index, Preconditions.checkNotNull(provider, "provider"));
            return this;
        }

        /**
         * Set an asynchronous completion provider for the most recently added argument.
         */
        public @NotNull Builder completionProviderAsync(@NotNull AsyncCompletionProvider provider) {
            if (args.isEmpty()) throw new CommandConfigurationException("No argument to set completion provider for");
            asyncProvidersByIndex.put(args.size() - 1, Preconditions.checkNotNull(provider, "provider"));
            return this;
        }

        /**
         * Set an asynchronous completion provider for a specific argument index (0-based).
         */
        public @NotNull Builder completionProviderAsyncForArg(int index, @NotNull AsyncCompletionProvider provider) {
            if (index < 0) throw new CommandConfigurationException("Argument index must be >= 0");
            asyncProvidersByIndex.put(index, Preconditions.checkNotNull(provider, "provider"));
            return this;
        }

        /**
         * Configure debounce window in milliseconds for asynchronous completion providers.
         */
        public @NotNull Builder completionDebounceMillis(long millis) {
            if (millis < 0) throw new CommandConfigurationException("Debounce millis must be >= 0");
            this.completionDebounceMillis = millis;
            return this;
        }

        /**
         * Configure timeout in milliseconds for asynchronous completion providers.
         */
        public @NotNull Builder completionTimeoutMillis(long millis) {
            if (millis < 1) throw new CommandConfigurationException("Timeout millis must be >= 1");
            this.completionTimeoutMillis = millis;
            return this;
        }

        /**
         * Define the action that should run when the command is executed.
         * @param action callback invoked with the sender and parsed context
         * @return this builder
         */
        public @NotNull Builder executes(@NotNull CommandAction action) {
            this.action = Preconditions.checkNotNull(action, "action");
            this.asyncAction = null; // prefer sync action
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
         * Add an example usage line shown on help pages.
         */
        public @NotNull Builder example(@NotNull String example) {
            Preconditions.checkNotNull(example, "example");
            this.examples.add(example);
            return this;
        }

        /**
         * Add multiple example usage lines shown on help pages.
         */
        public @NotNull Builder examples(@NotNull Collection<String> examples) {
            Preconditions.checkNotNull(examples, "examples");
            this.examples.addAll(examples);
            return this;
        }

        /**
         * Configure help page size (number of items per page for subcommands/examples).
         */
        public @NotNull Builder helpPageSize(int size) {
            if (size < 1) throw new CommandConfigurationException("helpPageSize must be >= 1");
            this.helpPageSize = size;
            return this;
        }

        /**
         * Enable/disable the automatic built-in help handler ("help" subcommand).
         */
        public @NotNull Builder autoHelp(boolean enable) {
            this.autoHelp = enable;
            return this;
        }

        /**
         * Require a specific sender type (e.g., Player.class).
         */
        public @NotNull Builder require(@NotNull Class<? extends CommandSender> type) {
            Preconditions.checkNotNull(type, "type");
            this.guards.add(new Guard() {
                @Override public boolean test(@NotNull CommandSender sender) { return type.isInstance(sender); }
                @Override public @NotNull String errorMessage() { return "§cThis command requires a " + type.getSimpleName() + "."; }
            });
            return this;
        }

        /**
         * Add one or more custom guard predicates.
         */
        public @NotNull Builder require(@NotNull Guard... guards) {
            Preconditions.checkNotNull(guards, "guards");
            for (Guard g : guards) {
                if (g != null) this.guards.add(g);
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
            this.asyncAction = null;
            this.async = true;
            return this;
        }

        /**
         * Define an asynchronous command action with optional timeout, supporting cancellation tokens
         * and progress reporting.
         * @param action async action implementation
         * @param timeoutMillis timeout in milliseconds (<= 0 for no timeout)
         */
        public @NotNull Builder executesAsync(@NotNull AsyncCommandAction action, long timeoutMillis) {
            this.async = true;
            this.asyncAction = Preconditions.checkNotNull(action, "action");
            this.asyncTimeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Define an asynchronous command action without timeout.
         */
        public @NotNull Builder executesAsync(@NotNull AsyncCommandAction action) {
            return executesAsync(action, 0L);
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
                name, description, permission, playerOnly, sendErrors, args, action, async, validateOnTab, subs,
                completionsByIndex, providersByIndex,
                asyncProvidersByIndex,
                completionDebounceMillis, completionTimeoutMillis,
                asyncAction, (asyncTimeoutMillis == null ? 0L : asyncTimeoutMillis),
                examples, helpPageSize, autoHelp, guards);
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
    private final CommandAction action; // sync action (optional when asyncActionAdv is used)
    // Mutable per-argument completions. Providers take precedence over static lists.
    private final Map<Integer, CompletionProvider> completionProviders;
    private final Map<Integer, List<String>> staticCompletions;
    // Async completion providers and tuning
    private final Map<Integer, AsyncCompletionProvider> asyncCompletionProviders;
    private final long completionDebounceMillis;
    private final long completionTimeoutMillis;
    private final Map<Integer, DebounceCache> completionCache = new java.util.concurrent.ConcurrentHashMap<>();
    // Advanced async action
    private final AsyncCommandAction asyncActionAdv; // nullable
    private final long asyncTimeoutMillis; // 0 = no timeout
    // Help system & guards
    private final java.util.List<String> examples;
    private final int helpPageSize;
    private final boolean autoHelp;
    private final java.util.List<Guard> guards;

    private JavaPlugin plugin; // set during register()
    private boolean subOnly = false; // marked when attached as a subcommand of another command

    private static final class DebounceCache {
        String prefix;
        java.util.List<String> result;
        long timestamp;
        java.util.concurrent.CompletableFuture<java.util.List<String>> inFlight;
    }

    /**
     * Marks this command instance as a subcommand. Intended for internal use by the Builder
     * when registering subcommands so that attempts to register the subcommand directly
     * can be rejected with a helpful error.
     */
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

    // ===== Runtime tab-completion API (mutable after build) =====

    /**
     * Replace the static completion suggestions for a specific argument position (0-based).
     * Any previously registered static suggestions for this index are replaced; dynamic providers
     * (if present) still take precedence when offering completions at runtime.
     *
     * @param argIndex    the argument index (0-based)
     * @param suggestions non-null collection of suggestion strings
     */
    public synchronized void setCompletions(int argIndex, @NotNull Collection<String> suggestions) {
        Preconditions.checkNotNull(suggestions, "suggestions");
        List<String> list = new java.util.concurrent.CopyOnWriteArrayList<>(suggestions);
        staticCompletions.put(argIndex, list);
    }

    /**
     * Add a single static completion suggestion for the specified argument index.
     *
     * @param argIndex   the argument index (0-based)
     * @param suggestion non-null suggestion string to add
     */
    public synchronized void addCompletion(int argIndex, @NotNull String suggestion) {
        Preconditions.checkNotNull(suggestion, "suggestion");
        List<String> list = staticCompletions.computeIfAbsent(argIndex, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(suggestion);
    }

    /**
     * Add multiple static completion suggestions for the specified argument index.
     *
     * @param argIndex    the argument index (0-based)
     * @param suggestions non-null collection of suggestion strings to add
     */
    public synchronized void addCompletions(int argIndex, @NotNull Collection<String> suggestions) {
        Preconditions.checkNotNull(suggestions, "suggestions");
        List<String> list = staticCompletions.computeIfAbsent(argIndex, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.addAll(suggestions);
    }

    /**
     * Remove a specific static completion for the specified argument index.
     *
     * @param argIndex   the argument index (0-based)
     * @param suggestion non-null suggestion string to remove
     * @return true if the suggestion was present and removed
     */
    public synchronized boolean removeCompletion(int argIndex, @NotNull String suggestion) {
        Preconditions.checkNotNull(suggestion, "suggestion");
        List<String> list = staticCompletions.get(argIndex);
        if (list == null) return false;
        return list.remove(suggestion);
    }

    /**
     * Clear all static completion suggestions for the specified argument index.
     * Dynamic providers, if any, remain unaffected.
     *
     * @param argIndex the argument index (0-based)
     */
    public synchronized void clearCompletions(int argIndex) {
        staticCompletions.remove(argIndex);
    }

    /** Get an immutable snapshot of static completions for the specified argument index. */
    public @NotNull List<String> getCompletions(int argIndex) {
        List<String> list = staticCompletions.get(argIndex);
        if (list == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** Set or replace a dynamic completion provider for the specified argument index. */
    public synchronized void setCompletionProvider(int argIndex, @Nullable CompletionProvider provider) {
        if (provider == null) {
            completionProviders.remove(argIndex);
        } else {
            completionProviders.put(argIndex, provider);
        }
    }

    /** Remove any dynamic completion provider for the specified argument index. */
    public synchronized void clearCompletionProvider(int argIndex) {
        completionProviders.remove(argIndex);
    }

    private FluentCommand(String name, String description, String permission, boolean playerOnly, boolean sendErrors,
                         List<Arg<?>> args, CommandAction action, boolean async, boolean validateOnTab,
                         Map<String, FluentCommand> subcommands,
                         Map<Integer, List<String>> initialStaticCompletions,
                         Map<Integer, CompletionProvider> initialProviders,
                         Map<Integer, AsyncCompletionProvider> initialAsyncProviders,
                         long completionDebounceMillis, long completionTimeoutMillis,
                         @Nullable AsyncCommandAction asyncActionAdv, long asyncTimeoutMillis,
                         List<String> examples, int helpPageSize, boolean autoHelp, List<Guard> guards) {
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
        // Initialize mutable completion maps
        this.completionProviders = new java.util.concurrent.ConcurrentHashMap<>();
        if (initialProviders != null) {
            this.completionProviders.putAll(initialProviders);
        }
        this.staticCompletions = new java.util.concurrent.ConcurrentHashMap<>();
        if (initialStaticCompletions != null) {
            for (Map.Entry<Integer, List<String>> e : initialStaticCompletions.entrySet()) {
                this.staticCompletions.put(e.getKey(), new java.util.concurrent.CopyOnWriteArrayList<>(e.getValue()));
            }
        }
        this.asyncCompletionProviders = new java.util.concurrent.ConcurrentHashMap<>();
        if (initialAsyncProviders != null) {
            this.asyncCompletionProviders.putAll(initialAsyncProviders);
        }
        this.completionDebounceMillis = completionDebounceMillis;
        this.completionTimeoutMillis = completionTimeoutMillis;
        this.asyncActionAdv = asyncActionAdv;
        this.asyncTimeoutMillis = asyncTimeoutMillis;
        this.examples = java.util.List.copyOf(examples == null ? java.util.List.of() : examples);
        this.helpPageSize = helpPageSize;
        this.autoHelp = autoHelp;
        this.guards = java.util.List.copyOf(guards == null ? java.util.List.of() : guards);
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

        // Guards
        for (Guard g : this.guards) {
            try {
                if (!g.test(sender)) {
                    if (sendErrors) sender.sendMessage(g.errorMessage());
                    return true;
                }
            } catch (Throwable t) {
                if (sendErrors) sender.sendMessage("§cYou cannot use this command.");
                    return true;
            }
        }

        // Built-in help handler
        if (autoHelp && providedArgs.length >= 1 && providedArgs[0].equalsIgnoreCase("help")) {
            int page = 1;
            if (providedArgs.length >= 2) {
                try { page = Math.max(1, Integer.parseInt(providedArgs[1])); } catch (NumberFormatException ignored) {}
            }
            sendHelp(sender, label, page);
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
                    } catch (Throwable ignored) {}
                };
                java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
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
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        msg = "§cCommand timed out after " + asyncTimeoutMillis + " ms.";
                        token.cancel();
                    } else {
                        msg = "§cAn internal error occurred while executing this command.";
                    }
                    if (sendErrors) {
                        if (plugin != null) plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
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
                        if (sendErrors) sender.sendMessage("§cAn internal error occurred while executing this command.");
                        Throwable cause = (t.getCause() != null) ? t.getCause() : t;
                        throw new de.feelix.leviathan.exceptions.CommandExecutionException(
                            "Error executing command '" + name + "' asynchronously", cause);
                    }
                });
            }
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

    private void sendHelp(@NotNull CommandSender sender, @NotNull String label, int page) {
        // Header
        if (!description.isEmpty()) {
            sender.sendMessage("§6/" + label + " §7- " + description);
        } else {
            sender.sendMessage("§6/" + label);
        }
        sender.sendMessage("§eUsage: §f/" + label + (usage().isEmpty() ? "" : (" " + usage())));
        if (!args.isEmpty()) {
            sender.sendMessage("§eArguments:");
            int i = 1;
            for (Arg<?> a : args) {
                String opt = a.optional() ? " §7(optional)" : "";
                String perm = (a.permission() != null && !a.permission().isEmpty()) ? (" §8perm:" + a.permission()) : "";
                sender.sendMessage("§7  " + (i++) + ". §b" + a.name() + " §7<" + a.parser().getTypeName() + ">" + opt + perm);
            }
        }

        // Build items: subcommands visible + examples
        java.util.List<String> items = new java.util.ArrayList<>();
        if (!subcommands.isEmpty()) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, FluentCommand> e : subcommands.entrySet()) {
                String perm = e.getValue().getPermission();
                if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) continue;
                names.add(e.getKey());
            }
            java.util.Collections.sort(names);
            for (String n : names) {
                FluentCommand sc = subcommands.get(n);
                String desc = sc.getDescription();
                items.add("§a" + n + (desc == null || desc.isEmpty() ? "" : (" §7- §f" + desc)));
            }
        }
        if (!examples.isEmpty()) {
            for (String ex : examples) {
                items.add("§fExample: §7/" + label + " " + ex);
            }
        }

        int total = items.size();
        if (total == 0) return;
        int pages = Math.max(1, (int) Math.ceil(total / (double) helpPageSize));
        int p = Math.max(1, Math.min(page, pages));
        int start = (p - 1) * helpPageSize;
        int end = Math.min(total, start + helpPageSize);
        sender.sendMessage("§eHelp §7(Page " + p + "/" + pages + "):");
        for (int i = start; i < end; i++) {
            int idx = i + 1;
            sender.sendMessage("§7  " + idx + ". " + items.get(i));
        }
        if (p < pages) {
            sender.sendMessage("§7Type §e/" + label + " help " + (p + 1) + " §7for more.");
        }
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

        // Guards gate tab completions as well
        for (Guard g : this.guards) {
            try {
                if (!g.test(sender)) {
                    return java.util.Collections.emptyList();
                }
            } catch (Throwable ignored) { return java.util.Collections.emptyList(); }
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

        List<String> suggestions;
        AsyncCompletionProvider asyncProv = asyncCompletionProviders.get(currentArgIndex);
        if (asyncProv != null) {
            DebounceCache cache = completionCache.computeIfAbsent(currentArgIndex, k -> new DebounceCache());
            long now = System.currentTimeMillis();
            if (cache.prefix != null && cache.prefix.equals(prefix)) {
                // If same prefix within debounce window
                if (cache.inFlight != null && !cache.inFlight.isDone() && (now - cache.timestamp) < completionDebounceMillis) {
                    return java.util.Collections.emptyList();
                }
                if (cache.result != null && (now - cache.timestamp) < completionDebounceMillis) {
                    return cache.result;
                }
            }
            java.util.concurrent.CompletableFuture<java.util.List<String>> fut = asyncProv.completeAsync(sender, alias, providedArgs, prefix);
            if (fut == null) {
                throw new ParsingException("AsyncCompletionProvider returned null for argument index " + currentArgIndex);
            }
            cache.prefix = prefix;
            cache.inFlight = fut;
            cache.timestamp = now;
            try {
                java.util.List<String> res = fut.get(completionTimeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (res == null) res = java.util.Collections.emptyList();
                java.util.List<String> sorted = new java.util.ArrayList<>(res);
                java.util.Collections.sort(sorted);
                cache.result = sorted;
                cache.inFlight = null;
                cache.timestamp = System.currentTimeMillis();
                suggestions = sorted;
            } catch (java.util.concurrent.TimeoutException | java.lang.InterruptedException | java.util.concurrent.ExecutionException e) {
                // Do not propagate; just return empty on timeout/error
                suggestions = java.util.Collections.emptyList();
            }
        } else {
            CompletionProvider provider = completionProviders.get(currentArgIndex);
            if (provider != null) {
                suggestions = provider.complete(sender, alias, providedArgs, prefix);
                if (suggestions == null) {
                    throw new ParsingException("CompletionProvider returned null for argument index " + currentArgIndex);
                }
            } else {
                List<String> base = staticCompletions.get(currentArgIndex);
                if (base != null) {
                    String pfxLow = prefix.toLowerCase(Locale.ROOT);
                    List<String> filtered = new ArrayList<>();
                    for (String s : base) {
                        if (s == null) continue;
                        if (pfxLow.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(pfxLow)) {
                            filtered.add(s);
                        }
                    }
                    Collections.sort(filtered);
                    suggestions = filtered;
                } else {
                    suggestions = current.parser().complete(prefix, sender);
                    if (suggestions == null) {
                        throw new ParsingException(
                            "Parser " + current.parser().getClass().getName() + " returned null suggestions for argument '"
                            + current.name() + "'");
                    }
                }
            }
        }
        return suggestions;
    }
}
