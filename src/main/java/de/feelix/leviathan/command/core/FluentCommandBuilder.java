package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.error.ExceptionHandler;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.validation.CrossArgumentValidator;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.command.argument.ArgParsers;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Builder for {@link FluentCommand}. Provides a fluent API to define arguments,
 * permissions, behavior, and registration.
 */
public final class FluentCommandBuilder {
    private final String name;
    private final List<String> aliases = new ArrayList<>();
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
    // Guards
    private final List<Guard> guards = new ArrayList<>();
    // Cross-argument validators
    private final List<CrossArgumentValidator> crossArgumentValidators = new ArrayList<>();
    // Exception handler
    private @Nullable ExceptionHandler exceptionHandler = null;
    // Cooldowns
    private long perUserCooldownMillis = 0L;
    private long perServerCooldownMillis = 0L;
    // Auto help
    private boolean enableHelp = false;

    FluentCommandBuilder(String name) {
        this.name = Preconditions.checkNotNull(name, "name");
    }

    /**
     * Set a human-readable description for this command.
     *
     * @param description Description text used for help/usage pages.
     * @return this builder
     */
    public @NotNull FluentCommandBuilder description(@Nullable String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the command-level permission required to run this command.
     * If {@code null} or blank, no permission is required.
     *
     * @param permission Bukkit permission node
     * @return this builder
     */
    public @NotNull FluentCommandBuilder permission(@Nullable String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Set additional command aliases for subcommand routing.
     * <p>
     * <b>Note:</b> Aliases are intended for subcommands only. When this command is registered as a subcommand,
     * these aliases allow users to invoke it using alternative names. For main commands, use Bukkit's native
     * alias support in plugin.yml instead.
     *
     * @param aliases additional command names/aliases for subcommand routing
     * @return this builder
     */
    public @NotNull FluentCommandBuilder aliases(@NotNull String... aliases) {
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.trim().isEmpty()) {
                    this.aliases.add(alias.trim());
                }
            }
        }
        return this;
    }

    /**
     * Add a single command alias for subcommand routing.
     * <p>
     * <b>Note:</b> Aliases are intended for subcommands only. When this command is registered as a subcommand,
     * this alias allows users to invoke it using an alternative name. For main commands, use Bukkit's native
     * alias support in plugin.yml instead.
     *
     * @param alias additional command name/alias for subcommand routing
     * @return this builder
     */
    public @NotNull FluentCommandBuilder alias(@NotNull String alias) {
        if (alias != null && !alias.trim().isEmpty()) {
            this.aliases.add(alias.trim());
        }
        return this;
    }

    /**
     * Restrict the command to players only (not console).
     *
     * @param playerOnly true to allow only players to execute
     * @return this builder
     */
    public @NotNull FluentCommandBuilder playerOnly(boolean playerOnly) {
        this.playerOnly = playerOnly;
        return this;
    }

    /**
     * Control whether user-facing error messages should be sent when parsing fails
     * or when the user lacks permissions.
     *
     * @param send true to send error messages to the sender
     * @return this builder
     */
    public @NotNull FluentCommandBuilder sendErrors(boolean send) {
        this.sendErrors = send;
        return this;
    }

    /**
     * Enable or disable automatic help messages for this command.
     * When enabled, executing the command without arguments will display a help message
     * showing either subcommands or usage information.
     *
     * @param enable true to enable automatic help, false to disable (default: false)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder enableHelp(boolean enable) {
        this.enableHelp = enable;
        return this;
    }

    /**
     * Add a required int argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argInt(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.intParser()));
    }

    /**
     * Add a required int argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argInt(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.intParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required long argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argLong(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.longParser()));
    }

    /**
     * Add a required long argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argLong(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.longParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required string argument (single token).
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argString(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.stringParser()));
    }

    /**
     * Add a string argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argString(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(
            name, ArgParsers.stringParser(), Preconditions.checkNotNull(
            argContext,
            "argContext"
        )
        ));
    }

    /**
     * Add a required UUID argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argUUID(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.uuidParser()));
    }

    /**
     * Add a UUID argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argUUID(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.uuidParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required double argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argDouble(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.doubleParser()));
    }

    /**
     * Add a double argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argDouble(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.doubleParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required float argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argFloat(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.floatParser()));
    }

    /**
     * Add a float argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argFloat(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.floatParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required boolean argument.
     * Accepts: true, false, yes, no, on, off, 1, 0 (case-insensitive).
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argBoolean(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.booleanParser()));
    }

    /**
     * Add a boolean argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argBoolean(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.booleanParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required online player argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argPlayer(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.playerParser()));
    }

    /**
     * Add a player argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argPlayer(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.playerParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required offline player argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argOfflinePlayer(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.offlinePlayerParser()));
    }

    /**
     * Add an offline player argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argOfflinePlayer(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.offlinePlayerParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required world argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argWorld(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.worldParser()));
    }

    /**
     * Add a world argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argWorld(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.worldParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required material argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argMaterial(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.materialParser()));
    }

    /**
     * Add a material argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argMaterial(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.materialParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required enum argument.
     *
     * @param name      argument name (no whitespace)
     * @param enumClass the enum class to parse
     * @param <E>       enum type
     * @return this builder
     */
    public <E extends Enum<E>> @NotNull FluentCommandBuilder argEnum(@NotNull String name, @NotNull Class<E> enumClass) {
        return arg(new Arg<>(name, false, ArgParsers.enumParser(enumClass)));
    }

    /**
     * Add an enum argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param enumClass  the enum class to parse
     * @param argContext per-argument configuration
     * @param <E>        enum type
     * @return this builder
     */
    public <E extends Enum<E>> @NotNull FluentCommandBuilder argEnum(@NotNull String name, @NotNull Class<E> enumClass, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.enumParser(enumClass), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required integer argument with range validation.
     * Convenience method that combines argInt with range validation in one call.
     *
     * @param name argument name (no whitespace)
     * @param min  minimum value (inclusive)
     * @param max  maximum value (inclusive)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argIntRange(@NotNull String name, int min, int max) {
        return arg(new Arg<>(name, ArgParsers.intParser(), 
            ArgContext.builder().intRange(min, max).build()));
    }

    /**
     * Add a required long argument with range validation.
     * Convenience method that combines argLong with range validation in one call.
     *
     * @param name argument name (no whitespace)
     * @param min  minimum value (inclusive)
     * @param max  maximum value (inclusive)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argLongRange(@NotNull String name, long min, long max) {
        return arg(new Arg<>(name, ArgParsers.longParser(), 
            ArgContext.builder().longRange(min, max).build()));
    }

    /**
     * Add a required double argument with range validation.
     * Convenience method that combines argDouble with range validation in one call.
     *
     * @param name argument name (no whitespace)
     * @param min  minimum value (inclusive)
     * @param max  maximum value (inclusive)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argDoubleRange(@NotNull String name, double min, double max) {
        return arg(new Arg<>(name, ArgParsers.doubleParser(), 
            ArgContext.builder().doubleRange(min, max).build()));
    }

    /**
     * Add a required float argument with range validation.
     * Convenience method that combines argFloat with range validation in one call.
     *
     * @param name argument name (no whitespace)
     * @param min  minimum value (inclusive)
     * @param max  maximum value (inclusive)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argFloatRange(@NotNull String name, float min, float max) {
        return arg(new Arg<>(name, ArgParsers.floatParser(), 
            ArgContext.builder().floatRange(min, max).build()));
    }

    /**
     * Add a required string argument with length validation.
     * Convenience method that combines argString with length validation in one call.
     *
     * @param name      argument name (no whitespace)
     * @param minLength minimum length (inclusive)
     * @param maxLength maximum length (inclusive)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argStringLength(@NotNull String name, int minLength, int maxLength) {
        return arg(new Arg<>(name, ArgParsers.stringParser(), 
            ArgContext.builder().stringLengthRange(minLength, maxLength).build()));
    }


    /**
     * Enable or disable validation of previously entered arguments during tab completion.
     * When enabled, if a prior argument cannot be parsed, tab suggestions will be hidden and,
     * if {@link #sendErrors(boolean)} is true, an error message will be sent to the sender.
     *
     * @param validate true to validate tokens typed before the current one
     * @return this builder
     */
    public @NotNull FluentCommandBuilder validateOnTab(boolean validate) {
        this.validateOnTab = validate;
        return this;
    }

    /**
     * Add a required choice argument from a fixed set of aliases mapping to values.
     *
     * @param name        argument name
     * @param choices     mapping from alias (case-insensitive) to value
     * @param displayType type name shown in error messages (e.g., "gamemode")
     * @return this builder
     */
    public <T> @NotNull FluentCommandBuilder argChoices(@NotNull String name, @NotNull Map<String, T> choices,
                                                        @NotNull String displayType) {
        return arg(new Arg<>(name, false, ArgParsers.choices(choices, displayType)));
    }

    /**
     * Add a choice argument with explicit {@link ArgContext}.
     *
     * @param name        argument name
     * @param choices     mapping from alias (case-insensitive) to value
     * @param displayType type name shown in error messages (e.g., "gamemode")
     * @param argContext  per-argument configuration
     * @return this builder
     */
    public <T> @NotNull FluentCommandBuilder argChoices(@NotNull String name, @NotNull Map<String, T> choices,
                                                        @NotNull String displayType, @NotNull ArgContext argContext) {
        return arg(new Arg<>(
            name, ArgParsers.choices(choices, displayType),
            Preconditions.checkNotNull(argContext, "argContext")
        ));
    }

    /**
     * Add a choice argument mapping command aliases to subcommand instances.
     * Useful for command routing via a parsed argument. For example:
     * <pre>{@code
     * .argCommandChoices("action", Map.of("list", listCommand, "add", addCommand), "action")
     * .executes((s, ctx) -> {
     *     FluentCommand sub = ctx.get("action", FluentCommand.class);
     *     sub.execute(s, sub.name(), new String[0]);
     * });
     * }</pre>
     *
     * @param name    argument name
     * @param choices mapping from alias (case-insensitive) to FluentCommand subcommand
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argCommandChoices(@NotNull String name,
                                                           @NotNull Map<String, FluentCommand> choices) {
        return arg(new Arg<>(name, false, ArgParsers.choices(choices, "command")));
    }

    /**
     * Add a command-choice argument with explicit {@link ArgContext}.
     *
     * @param name       argument name
     * @param choices    mapping from alias (case-insensitive) to FluentCommand subcommand
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull FluentCommandBuilder argCommandChoices(@NotNull String name,
                                                           @NotNull Map<String, FluentCommand> choices,
                                                           @NotNull ArgContext argContext) {
        return arg(new Arg<>(
            name, ArgParsers.choices(choices, "command"),
            Preconditions.checkNotNull(argContext, "argContext")
        ));
    }

    /**
     * Add a generic argument to the command.
     *
     * @param arg the argument definition
     * @return this builder
     */
    public <T> @NotNull FluentCommandBuilder arg(@NotNull Arg<T> arg) {
        Preconditions.checkNotNull(arg, "arg");
        this.args.add(arg);
        return this;
    }

    /**
     * Add a generic argument using a custom {@link ArgumentParser}.
     *
     * @param name   argument name (no whitespace)
     * @param parser custom parser for the argument
     * @return this builder
     */
    public <T> @NotNull FluentCommandBuilder arg(@NotNull String name, @NotNull ArgumentParser<T> parser) {
        return arg(new Arg<>(name, false, parser));
    }

    /**
     * Add a generic argument using a custom {@link ArgumentParser} and explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param parser     custom parser for the argument
     * @param argContext per-argument configuration
     * @return this builder
     */
    public <T> @NotNull FluentCommandBuilder arg(@NotNull String name, @NotNull ArgumentParser<T> parser,
                                                 @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, parser, Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Define the action to execute when the command is successfully invoked with valid arguments.
     *
     * @param action callback invoked with the sender and parsed context
     * @return this builder
     */
    public @NotNull FluentCommandBuilder executes(@NotNull CommandAction action) {
        this.action = Preconditions.checkNotNull(action, "action");
        this.asyncAction = null;
        return this;
    }

    /**
     * Register one or more subcommands using each subcommand's own {@link FluentCommand#name()} as the alias.
     * Aliases are matched case-insensitively.
     *
     * @param subs Subcommands to register (must not contain nulls).
     * @return this builder
     * @throws CommandConfigurationException if any sub is null or duplicate aliases are detected
     */
    public @NotNull FluentCommandBuilder sub(@Nullable FluentCommand... subs) {
        if (subs == null) return this;
        for (FluentCommand sc : subs) {
            if (sc == null) {
                throw new CommandConfigurationException("Subcommand must not be null");
            }
            String alias = sc.name();
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
     * Require a specific sender type (e.g., Player.class).
     */
    public @NotNull FluentCommandBuilder require(@NotNull Class<? extends CommandSender> type) {
        Preconditions.checkNotNull(type, "type");
        this.guards.add(new Guard() {
            @Override
            public boolean test(@NotNull CommandSender sender) {
                return type.isInstance(sender);
            }

            @Override
            public @NotNull String errorMessage() {
                return "§cThis command requires a " + type.getSimpleName() + ".";
            }
        });
        return this;
    }

    /**
     * Add one or more custom guard predicates.
     */
    public @NotNull FluentCommandBuilder require(@NotNull Guard... guards) {
        Preconditions.checkNotNull(guards, "guards");
        for (Guard g : guards) {
            if (g != null) this.guards.add(g);
        }
        return this;
    }

    /**
     * Add a cross-argument validator to enforce constraints between multiple arguments.
     * These validators are invoked after all arguments have been parsed and individually validated.
     * Multiple validators can be added and will form a validation chain.
     *
     * @param validator the cross-argument validator to add
     * @return this builder
     */
    public @NotNull FluentCommandBuilder addCrossArgumentValidator(@NotNull CrossArgumentValidator validator) {
        Preconditions.checkNotNull(validator, "validator");
        this.crossArgumentValidators.add(validator);
        return this;
    }

    /**
     * Set a custom exception handler to intercept and handle errors during command processing.
     * The handler can provide custom error messages and optionally suppress default messages.
     *
     * @param handler the exception handler
     * @return this builder
     */
    public @NotNull FluentCommandBuilder exceptionHandler(@Nullable ExceptionHandler handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * Configure whether the action should run asynchronously via {@link CompletableFuture}.
     *
     * @param async true to execute off the main thread
     * @return this builder
     */
    public @NotNull FluentCommandBuilder async(boolean async) {
        this.async = async;
        return this;
    }

    /**
     * Shorthand to set the action and enable asynchronous execution in one call.
     *
     * @param action callback invoked with the sender and parsed context
     * @return this builder
     */
    public @NotNull FluentCommandBuilder executesAsync(@NotNull CommandAction action) {
        this.action = Preconditions.checkNotNull(action, "action");
        this.asyncAction = null;
        this.async = true;
        return this;
    }

    /**
     * Define an asynchronous command action with optional timeout, supporting cancellation tokens
     * and progress reporting.
     *
     * @param action        async action implementation
     * @param timeoutMillis timeout in milliseconds ({@code <= 0} for no timeout)
     */
    public @NotNull FluentCommandBuilder executesAsync(@NotNull AsyncCommandAction action, long timeoutMillis) {
        this.async = true;
        this.asyncAction = Preconditions.checkNotNull(action, "action");
        this.asyncTimeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * Define an asynchronous command action without timeout.
     */
    public @NotNull FluentCommandBuilder executesAsync(@NotNull AsyncCommandAction action) {
        return executesAsync(action, 0L);
    }

    /**
     * Set a per-user cooldown for this command.
     * Each user must wait the specified duration before executing the command again.
     *
     * @param cooldownMillis cooldown duration in milliseconds (0 = no cooldown)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder perUserCooldown(long cooldownMillis) {
        this.perUserCooldownMillis = cooldownMillis;
        return this;
    }

    /**
     * Set a per-server cooldown for this command.
     * All users must wait the specified duration after any execution before the command can be executed again.
     *
     * @param cooldownMillis cooldown duration in milliseconds (0 = no cooldown)
     * @return this builder
     */
    public @NotNull FluentCommandBuilder perServerCooldown(long cooldownMillis) {
        this.perServerCooldownMillis = cooldownMillis;
        return this;
    }

    /**
     * Validate the configuration and build the immutable {@link FluentCommand}.
     *
     * @return the configured command instance
     * @throws CommandConfigurationException if configuration is invalid (e.g., duplicate arg names,
     *                                       required-after-optional, bad greedy placement)
     * @throws ParsingException              if a parser violates its contract (e.g., blank type name)
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
        // Validate subcommands and register both primary names and aliases in the routing map
        Map<String, FluentCommand> subs = new LinkedHashMap<>();
        for (Map.Entry<String, FluentCommand> e : subcommands.entrySet()) {
            String k = e.getKey();
            if (k == null || k.trim().isEmpty() || e.getValue() == null) {
                throw new CommandConfigurationException("Invalid subcommand entry");
            }
            FluentCommand subCmd = e.getValue();
            
            // Forward parent's exceptionHandler to subcommand if subcommand doesn't have its own
            if (subCmd.exceptionHandler == null && this.exceptionHandler != null) {
                subCmd.exceptionHandler = this.exceptionHandler;
            }
            
            // Register primary name
            String low = k.toLowerCase(Locale.ROOT);
            if (subs.put(low, subCmd) != null) {
                throw new CommandConfigurationException("Duplicate subcommand alias: '" + k + "'");
            }
            
            // Register all aliases for this subcommand
            for (String alias : subCmd.aliases()) {
                if (alias == null || alias.trim().isEmpty()) {
                    continue;
                }
                String aliasLow = alias.toLowerCase(Locale.ROOT);
                if (subs.containsKey(aliasLow)) {
                    throw new CommandConfigurationException("Duplicate subcommand alias: '" + alias + "'");
                }
                subs.put(aliasLow, subCmd);
            }
        }
        FluentCommand cmd = new FluentCommand(
            name, aliases, description, permission, playerOnly, sendErrors, args, action, async, validateOnTab, subs,
            asyncAction, (asyncTimeoutMillis == null ? 0L : asyncTimeoutMillis),
            guards, crossArgumentValidators, exceptionHandler,
            perUserCooldownMillis, perServerCooldownMillis, enableHelp
        );
        
        // Set parent reference for all subcommands
        for (FluentCommand subCmd : new java.util.HashSet<>(subs.values())) {
            subCmd.setParent(cmd);
        }
        
        return cmd;
    }

    /**
     * Build and register this command instance as executor and tab-completer for the
     * command with the same name declared in plugin.yml.
     * Also stores the plugin instance inside the command for potential future use.
     *
     * @param plugin plugin registering the command (must declare the command in plugin.yml)
     * @throws CommandConfigurationException if the command is not declared in plugin.yml
     */
    public void register(@NotNull JavaPlugin plugin) {
        Preconditions.checkNotNull(plugin, "plugin");
        FluentCommand cmd = build();
        cmd.plugin = plugin;

        // Register primary command
        org.bukkit.command.PluginCommand pc = plugin.getCommand(name);
        if (pc == null) {
            throw new CommandConfigurationException("Command not declared in plugin.yml: " + name);
        }
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
    }
}
