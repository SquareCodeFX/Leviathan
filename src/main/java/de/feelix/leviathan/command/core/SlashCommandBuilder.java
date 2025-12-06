package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.error.DetailedExceptionHandler;
import de.feelix.leviathan.command.error.ExceptionHandler;
import de.feelix.leviathan.command.guard.Guard;
import de.feelix.leviathan.command.message.MessageProvider;
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
import java.util.function.Predicate;

/**
 * Builder for {@link SlashCommand}. Provides a fluent API to define arguments,
 * permissions, behavior, and registration.
 */
public final class SlashCommandBuilder {
    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private String description = "";
    private String permission = null;
    private boolean playerOnly = false;
    private boolean sendErrors = true;
    private boolean async = false;
    private boolean validateOnTab = false;
    private final List<Arg<?>> args = new ArrayList<>();
    private final Map<String, SlashCommand> subcommands = new LinkedHashMap<>();
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
    private int helpPageSize = 10;
    // Message provider
    private @Nullable MessageProvider messages = null;
    // Input sanitization
    private boolean sanitizeInputs = false;
    // Fuzzy subcommand matching
    private boolean fuzzySubcommandMatching = false;

    SlashCommandBuilder(String name) {
        this.name = Preconditions.checkNotNull(name, "name");
    }

    /**
     * Set a human-readable description for this command.
     *
     * @param description Description text used for help/usage pages.
     * @return this builder
     */
    public @NotNull SlashCommandBuilder description(@Nullable String description) {
        this.description = description;
        return this;
    }

    /**
     * Fluent alias for {@link #description(String)}.
     * Makes the API read more naturally: {@code withDescription("Teleport to spawn")}
     *
     * @param description Description text used for help/usage pages.
     * @return this builder
     */
    public @NotNull SlashCommandBuilder withDescription(@Nullable String description) {
        return description(description);
    }

    /**
     * Set the command-level permission required to run this command.
     * If {@code null} or blank, no permission is required.
     *
     * @param permission Bukkit permission node
     * @return this builder
     */
    public @NotNull SlashCommandBuilder permission(@Nullable String permission) {
        this.permission = permission;
        return this;
    }

    /**
     * Fluent alias for {@link #permission(String)}.
     * Makes the API read more naturally: {@code withPermission("admin.use")}
     *
     * @param permission Bukkit permission node
     * @return this builder
     */
    public @NotNull SlashCommandBuilder withPermission(@Nullable String permission) {
        return permission(permission);
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
    public @NotNull SlashCommandBuilder aliases(@NotNull String... aliases) {
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
    public @NotNull SlashCommandBuilder alias(@NotNull String alias) {
        if (alias != null && !alias.trim().isEmpty()) {
            this.aliases.add(alias.trim());
        }
        return this;
    }

    /**
     * Fluent alias for {@link #aliases(String...)}.
     * Makes the API read more naturally: {@code withAliases("alt1", "alt2")}
     *
     * @param aliases additional command names/aliases for subcommand routing
     * @return this builder
     */
    public @NotNull SlashCommandBuilder withAliases(@NotNull String... aliases) {
        return aliases(aliases);
    }

    /**
     * Restrict the command to players only (not console).
     *
     * @param playerOnly true to allow only players to execute
     * @return this builder
     */
    public @NotNull SlashCommandBuilder playerOnly(boolean playerOnly) {
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
    public @NotNull SlashCommandBuilder sendErrors(boolean send) {
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
    public @NotNull SlashCommandBuilder enableHelp(boolean enable) {
        this.enableHelp = enable;
        return this;
    }

    /**
     * Set the number of items to display per page in help messages.
     * When there are many subcommands, the help output will be paginated.
     * Users can navigate pages by providing a page number argument.
     *
     * @param pageSize number of subcommands to display per page (default: 10, minimum: 1)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder helpPageSize(int pageSize) {
        this.helpPageSize = Math.max(1, pageSize);
        return this;
    }

    /**
     * Set a custom message provider for this command.
     * Allows customization of all user-facing messages (errors, validation, help, etc.).
     * If not set, {@link de.feelix.leviathan.command.message.DefaultMessageProvider} will be used.
     *
     * @param provider the message provider to use (can be null to use default)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder messages(@Nullable MessageProvider provider) {
        this.messages = provider;
        return this;
    }

    /**
     * Fluent alias for {@link #messages(MessageProvider)}.
     * Makes the API read more naturally: {@code withMessages(customProvider)}
     *
     * @param provider the message provider to use (can be null to use default)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder withMessages(@Nullable MessageProvider provider) {
        return messages(provider);
    }

    /**
     * Add a required int argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argInt(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.intParser()));
    }

    /**
     * Add a required int argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argInt(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.intParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required long argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argLong(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.longParser()));
    }

    /**
     * Add a required long argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argLong(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.longParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required string argument (single token).
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argString(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.stringParser()));
    }

    /**
     * Add a string argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argString(@NotNull String name, @NotNull ArgContext argContext) {
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
    public @NotNull SlashCommandBuilder argUUID(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.uuidParser()));
    }

    /**
     * Add a UUID argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argUUID(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.uuidParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required double argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argDouble(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.doubleParser()));
    }

    /**
     * Add a double argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argDouble(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.doubleParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required float argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argFloat(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.floatParser()));
    }

    /**
     * Add a float argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argFloat(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.floatParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required boolean argument.
     * Accepts: true, false, yes, no, on, off, 1, 0 (case-insensitive).
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argBoolean(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.booleanParser()));
    }

    /**
     * Add a boolean argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argBoolean(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.booleanParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required online player argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argPlayer(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.playerParser()));
    }

    /**
     * Add a player argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argPlayer(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.playerParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required offline player argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argOfflinePlayer(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.offlinePlayerParser()));
    }

    /**
     * Add an offline player argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argOfflinePlayer(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(
            new Arg<>(name, ArgParsers.offlinePlayerParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required world argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argWorld(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.worldParser()));
    }

    /**
     * Add a world argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argWorld(@NotNull String name, @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, ArgParsers.worldParser(), Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a required material argument.
     *
     * @param name argument name (no whitespace)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argMaterial(@NotNull String name) {
        return arg(new Arg<>(name, false, ArgParsers.materialParser()));
    }

    /**
     * Add a material argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argMaterial(@NotNull String name, @NotNull ArgContext argContext) {
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
    public <E extends Enum<E>> @NotNull SlashCommandBuilder argEnum(@NotNull String name, @NotNull Class<E> enumClass) {
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
    public <E extends Enum<E>> @NotNull SlashCommandBuilder argEnum(@NotNull String name, @NotNull Class<E> enumClass,
                                                                    @NotNull ArgContext argContext) {
        return arg(
            new Arg<>(name, ArgParsers.enumParser(enumClass), Preconditions.checkNotNull(argContext, "argContext")));
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
    public @NotNull SlashCommandBuilder argIntRange(@NotNull String name, int min, int max) {
        Preconditions.checkArgument(min <= max, "min must be <= max for range [" + min + ", " + max + "]");
        return arg(new Arg<>(
            name, ArgParsers.intParser(),
            ArgContext.builder().intRange(min, max).rangeHint(min, max).build()
        ));
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
    public @NotNull SlashCommandBuilder argLongRange(@NotNull String name, long min, long max) {
        Preconditions.checkArgument(min <= max, "min must be <= max for range [" + min + ", " + max + "]");
        return arg(new Arg<>(
            name, ArgParsers.longParser(),
            ArgContext.builder().longRange(min, max).rangeHint(min, max).build()
        ));
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
    public @NotNull SlashCommandBuilder argDoubleRange(@NotNull String name, double min, double max) {
        Preconditions.checkArgument(min <= max, "min must be <= max for range [" + min + ", " + max + "]");
        return arg(new Arg<>(
            name, ArgParsers.doubleParser(),
            ArgContext.builder().doubleRange(min, max).rangeHint(min, max).build()
        ));
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
    public @NotNull SlashCommandBuilder argFloatRange(@NotNull String name, float min, float max) {
        Preconditions.checkArgument(min <= max, "min must be <= max for range [" + min + ", " + max + "]");
        return arg(new Arg<>(
            name, ArgParsers.floatParser(),
            ArgContext.builder().floatRange(min, max).rangeHint((double) min, (double) max).build()
        ));
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
    public @NotNull SlashCommandBuilder argStringLength(@NotNull String name, int minLength, int maxLength) {
        Preconditions.checkNonNegative(minLength, "minLength");
        Preconditions.checkNonNegative(maxLength, "maxLength");
        Preconditions.checkArgument(
            minLength <= maxLength, "minLength must be <= maxLength for range [" + minLength + ", " + maxLength + "]");
        return arg(new Arg<>(
            name, ArgParsers.stringParser(),
            ArgContext.builder().stringLengthRange(minLength, maxLength).build()
        ));
    }

    /**
     * Add a string argument with predefined completion suggestions.
     * Convenience method for common pattern of string args with fixed completion options.
     *
     * @param name        argument name (no whitespace)
     * @param completions list of completion suggestions
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argStringWithCompletions(@NotNull String name,
                                                                 @NotNull List<String> completions) {
        Preconditions.checkNotNull(completions, "completions");
        return arg(new Arg<>(
            name, ArgParsers.stringParser(),
            ArgContext.builder().withCompletions(completions).build()
        ));
    }

    /**
     * Add a string argument with predefined completion suggestions (varargs version).
     * Convenience method for common pattern of string args with fixed completion options.
     *
     * @param name        argument name (no whitespace)
     * @param completions completion suggestions
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argStringWithCompletions(@NotNull String name, @NotNull String... completions) {
        Preconditions.checkNotNull(completions, "completions");
        return argStringWithCompletions(name, List.of(completions));
    }

    /**
     * Add an optional page number argument for paginated commands.
     * The argument defaults to page 1 and validates that the value is at least 1.
     * <p>
     * This is a convenience method equivalent to:
     * <pre>{@code
     * .argInt("page", ArgContext.builder()
     *     .optional(true)
     *     .defaultValue(1)
     *     .intMin(1)
     *     .build())
     * }</pre>
     * <p>
     * <b>Example usage with PaginationHelper:</b>
     * <pre>{@code
     * SlashCommand.builder("list")
     *     .argPage()
     *     .executes((sender, ctx) -> {
     *         int page = ctx.getIntOrDefault("page", 1);
     *         PaginationHelper.paginate(items)
     *             .page(page)
     *             .pageSize(10)
     *             .header("§6=== Items ===")
     *             .formatter(item -> "§7- " + item)
     *             .send(sender);
     *     })
     *     .register(plugin);
     * }</pre>
     *
     * @return this builder
     * @see de.feelix.leviathan.command.pagination.PaginationHelper
     */
    public @NotNull SlashCommandBuilder argPage() {
        return argPage("page");
    }

    /**
     * Add an optional page number argument with a custom name for paginated commands.
     * The argument defaults to page 1 and validates that the value is at least 1.
     *
     * @param name the argument name (no whitespace)
     * @return this builder
     * @see #argPage()
     * @see de.feelix.leviathan.command.pagination.PaginationHelper
     */
    public @NotNull SlashCommandBuilder argPage(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return arg(new Arg<>(
            name, ArgParsers.intParser(),
            ArgContext.builder()
                .optional(true)
                .defaultValue(1)
                .intMin(1)
                .build()
        ));
    }

    /**
     * Add an optional page number argument with custom default value.
     * Validates that the value is at least 1.
     *
     * @param name        the argument name (no whitespace)
     * @param defaultPage the default page number (must be >= 1)
     * @return this builder
     * @see #argPage()
     * @see de.feelix.leviathan.command.pagination.PaginationHelper
     */
    public @NotNull SlashCommandBuilder argPage(@NotNull String name, int defaultPage) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkArgument(defaultPage >= 1, "defaultPage must be >= 1");
        return arg(new Arg<>(
            name, ArgParsers.intParser(),
            ArgContext.builder()
                .optional(true)
                .defaultValue(defaultPage)
                .intMin(1)
                .build()
        ));
    }

    /**
     * Enable or disable validation of previously entered arguments during tab completion.
     * When enabled, if a prior argument cannot be parsed, tab suggestions will be hidden and,
     * if {@link #sendErrors(boolean)} is true, an error message will be sent to the sender.
     *
     * @param validate true to validate tokens typed before the current one
     * @return this builder
     */
    public @NotNull SlashCommandBuilder validateOnTab(boolean validate) {
        this.validateOnTab = validate;
        return this;
    }

    /**
     * Enable or disable input sanitization for string arguments.
     * When enabled, string argument values will be sanitized to remove potentially dangerous
     * characters and patterns that could be used for injection attacks (e.g., SQL injection,
     * command injection, XSS).
     * <p>
     * The sanitization process:
     * <ul>
     *   <li>Removes or escapes HTML/XML special characters (&lt;, &gt;, &amp;, etc.)</li>
     *   <li>Removes control characters and null bytes</li>
     *   <li>Escapes backslashes and quotes</li>
     *   <li>Trims excessive whitespace</li>
     * </ul>
     * <p>
     * This is disabled by default to preserve backward compatibility and to allow
     * commands that legitimately need special characters in their input.
     *
     * @param sanitize true to enable input sanitization for string arguments
     * @return this builder
     */
    public @NotNull SlashCommandBuilder sanitizeInputs(boolean sanitize) {
        this.sanitizeInputs = sanitize;
        return this;
    }

    /**
     * Enable or disable fuzzy matching for subcommands.
     * When enabled, if a user enters an unrecognized subcommand name, the system will attempt
     * to find a similar subcommand using fuzzy string matching (Levenshtein distance).
     * If a sufficiently similar match is found, it will be executed automatically.
     * <p>
     * This feature helps users who make typos when entering subcommand names, improving
     * the user experience by automatically correcting minor spelling mistakes.
     * <p>
     * Example: If subcommands "reload", "help", and "status" exist, typing "relaod" would
     * automatically match to "reload" when fuzzy matching is enabled.
     * <p>
     * This is disabled by default to preserve exact matching behavior.
     *
     * @param fuzzy true to enable fuzzy subcommand matching
     * @return this builder
     */
    public @NotNull SlashCommandBuilder fuzzySubcommandMatching(boolean fuzzy) {
        this.fuzzySubcommandMatching = fuzzy;
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
    public <T> @NotNull SlashCommandBuilder argChoices(@NotNull String name, @NotNull Map<String, T> choices,
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
    public <T> @NotNull SlashCommandBuilder argChoices(@NotNull String name, @NotNull Map<String, T> choices,
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
     *     SlashCommand sub = ctx.get("action", SlashCommand.class);
     *     sub.execute(s, sub.name(), new String[0]);
     * });
     * }</pre>
     *
     * @param name    argument name
     * @param choices mapping from alias (case-insensitive) to SlashCommand subcommand
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argCommandChoices(@NotNull String name,
                                                          @NotNull Map<String, SlashCommand> choices) {
        return arg(new Arg<>(name, false, ArgParsers.choices(choices, "command")));
    }

    /**
     * Add a command-choice argument with explicit {@link ArgContext}.
     *
     * @param name       argument name
     * @param choices    mapping from alias (case-insensitive) to SlashCommand subcommand
     * @param argContext per-argument configuration
     * @return this builder
     */
    public @NotNull SlashCommandBuilder argCommandChoices(@NotNull String name,
                                                          @NotNull Map<String, SlashCommand> choices,
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
    public <T> @NotNull SlashCommandBuilder arg(@NotNull Arg<T> arg) {
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
    public <T> @NotNull SlashCommandBuilder arg(@NotNull String name, @NotNull ArgumentParser<T> parser) {
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
    public <T> @NotNull SlashCommandBuilder arg(@NotNull String name, @NotNull ArgumentParser<T> parser,
                                                @NotNull ArgContext argContext) {
        return arg(new Arg<>(name, parser, Preconditions.checkNotNull(argContext, "argContext")));
    }

    /**
     * Add a conditional argument that is only parsed if the condition evaluates to true.
     * The condition is evaluated based on previously parsed arguments.
     *
     * @param name      argument name (no whitespace)
     * @param parser    custom parser for the argument
     * @param condition predicate that determines if this argument should be parsed
     * @return this builder
     */
    public <T> @NotNull SlashCommandBuilder argIf(@NotNull String name, @NotNull ArgumentParser<T> parser,
                                                  @NotNull Predicate<CommandContext> condition) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(parser, "parser");
        Preconditions.checkNotNull(condition, "condition");
        return arg(new Arg<>(name, false, parser).withCondition(condition));
    }

    /**
     * Add a conditional argument with explicit {@link ArgContext}.
     *
     * @param name       argument name (no whitespace)
     * @param parser     custom parser for the argument
     * @param condition  predicate that determines if this argument should be parsed
     * @param argContext per-argument configuration
     * @return this builder
     */
    public <T> @NotNull SlashCommandBuilder argIf(@NotNull String name, @NotNull ArgumentParser<T> parser,
                                                  @NotNull Predicate<CommandContext> condition,
                                                  @NotNull ArgContext argContext) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(parser, "parser");
        Preconditions.checkNotNull(condition, "condition");
        Preconditions.checkNotNull(argContext, "argContext");
        return arg(new Arg<>(name, parser, argContext).withCondition(condition));
    }

    /**
     * Define the action to execute when the command is successfully invoked with valid arguments.
     *
     * @param action callback invoked with the sender and parsed context
     * @return this builder
     */
    public @NotNull SlashCommandBuilder executes(@NotNull CommandAction action) {
        this.action = Preconditions.checkNotNull(action, "action");
        this.asyncAction = null;
        return this;
    }

    /**
     * Register one or more subcommands using each subcommand's own {@link SlashCommand#name()} as the alias.
     * Aliases are matched case-insensitively.
     *
     * @param subs Subcommands to register (must not contain nulls).
     * @return this builder
     * @throws CommandConfigurationException if any sub is null or duplicate aliases are detected
     */
    public @NotNull SlashCommandBuilder sub(@Nullable SlashCommand... subs) {
        if (subs == null) return this;
        for (SlashCommand sc : subs) {
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
     * Fluent alias for {@link #sub(SlashCommand...)}.
     * Makes the API read more naturally: {@code withSubcommands(cmd1, cmd2, cmd3)}
     *
     * @param subs Subcommands to register (must not contain nulls).
     * @return this builder
     * @throws CommandConfigurationException if any sub is null or duplicate aliases are detected
     */
    public @NotNull SlashCommandBuilder withSubcommands(@Nullable SlashCommand... subs) {
        return sub(subs);
    }

    /**
     * Convenience method to mark the command as player-only in one call.
     * Equivalent to {@code playerOnly(true)}.
     *
     * @return this builder
     */
    public @NotNull SlashCommandBuilder playersOnly() {
        return playerOnly(true);
    }

    /**
     * Require a specific sender type (e.g., Player.class).
     */
    public @NotNull SlashCommandBuilder require(@NotNull Class<? extends CommandSender> type) {
        Preconditions.checkNotNull(type, "type");
        final MessageProvider msgProvider = this.messages;
        this.guards.add(new Guard() {
            @Override
            public boolean test(@NotNull CommandSender sender) {
                return type.isInstance(sender);
            }

            @Override
            public @NotNull String errorMessage() {
                if (msgProvider != null) {
                    return msgProvider.requiresType(type.getSimpleName());
                }
                return "§cThis command requires a " + type.getSimpleName() + ".";
            }
        });
        return this;
    }

    /**
     * Add one or more custom guard predicates.
     */
    public @NotNull SlashCommandBuilder require(@NotNull Guard... guards) {
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
    public @NotNull SlashCommandBuilder addCrossArgumentValidator(@NotNull CrossArgumentValidator validator) {
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
    public @NotNull SlashCommandBuilder exceptionHandler(@Nullable ExceptionHandler handler) {
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * Enable the detailed exception handler that prints comprehensive diagnostic information
     * to the console when errors occur. This includes:
     * <ul>
     *   <li>Full stack traces with cause chain analysis</li>
     *   <li>JVM details (version, vendor, memory usage)</li>
     *   <li>Thread dump for debugging deadlocks and threading issues</li>
     *   <li>Contextual suggestions for why the error might have occurred</li>
     * </ul>
     *
     * @param plugin the plugin instance for logging
     * @return this builder
     * @see DetailedExceptionHandler
     */
    public @NotNull SlashCommandBuilder detailedExceptionHandler(@NotNull JavaPlugin plugin) {
        Preconditions.checkNotNull(plugin, "plugin");
        this.exceptionHandler = new DetailedExceptionHandler(plugin);
        return this;
    }

    /**
     * Enable the detailed exception handler with a custom builder configuration.
     * This allows fine-tuning which diagnostic sections are included in the output.
     *
     * @param handler the configured DetailedExceptionHandler instance
     * @return this builder
     * @see DetailedExceptionHandler.Builder
     */
    public @NotNull SlashCommandBuilder detailedExceptionHandler(@NotNull DetailedExceptionHandler handler) {
        Preconditions.checkNotNull(handler, "handler");
        this.exceptionHandler = handler;
        return this;
    }

    /**
     * Configure whether the action should run asynchronously via {@link CompletableFuture}.
     *
     * @param async true to execute off the main thread
     * @return this builder
     */
    public @NotNull SlashCommandBuilder async(boolean async) {
        this.async = async;
        return this;
    }

    /**
     * Shorthand to set the action and enable asynchronous execution in one call.
     *
     * @param action callback invoked with the sender and parsed context
     * @return this builder
     */
    public @NotNull SlashCommandBuilder executesAsync(@NotNull CommandAction action) {
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
    public @NotNull SlashCommandBuilder executesAsync(@NotNull AsyncCommandAction action, long timeoutMillis) {
        this.async = true;
        this.asyncAction = Preconditions.checkNotNull(action, "action");
        this.asyncTimeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * Define an asynchronous command action without timeout.
     */
    public @NotNull SlashCommandBuilder executesAsync(@NotNull AsyncCommandAction action) {
        return executesAsync(action, 0L);
    }

    /**
     * Set a per-user cooldown for this command.
     * Each user must wait the specified duration before executing the command again.
     *
     * @param cooldownMillis cooldown duration in milliseconds (0 = no cooldown)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder perUserCooldown(long cooldownMillis) {
        this.perUserCooldownMillis = Preconditions.checkNonNegative(cooldownMillis, "cooldownMillis");
        return this;
    }

    /**
     * Set a per-server cooldown for this command.
     * All users must wait the specified duration after any execution before the command can be executed again.
     *
     * @param cooldownMillis cooldown duration in milliseconds (0 = no cooldown)
     * @return this builder
     */
    public @NotNull SlashCommandBuilder perServerCooldown(long cooldownMillis) {
        this.perServerCooldownMillis = Preconditions.checkNonNegative(cooldownMillis, "cooldownMillis");
        return this;
    }

    /**
     * Validate the configuration and build the immutable {@link SlashCommand}.
     *
     * @return the configured command instance
     * @throws CommandConfigurationException if configuration is invalid (e.g., duplicate arg names,
     *                                       required-after-optional, bad greedy placement)
     * @throws ParsingException              if a parser violates its contract (e.g., blank type name)
     */
    public @NotNull SlashCommand build() {
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
        Map<String, SlashCommand> subs = new LinkedHashMap<>();
        for (Map.Entry<String, SlashCommand> e : subcommands.entrySet()) {
            String k = e.getKey();
            if (k == null || k.trim().isEmpty() || e.getValue() == null) {
                throw new CommandConfigurationException("Invalid subcommand entry");
            }
            SlashCommand subCmd = e.getValue();

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
        SlashCommand cmd = new SlashCommand(
            name, aliases, description, permission, playerOnly, sendErrors, args, action, async, validateOnTab, subs,
            asyncAction, (asyncTimeoutMillis == null ? 0L : asyncTimeoutMillis),
            guards, crossArgumentValidators, exceptionHandler,
            perUserCooldownMillis, perServerCooldownMillis, enableHelp, helpPageSize, messages, sanitizeInputs,
            fuzzySubcommandMatching
        );

        // Set parent reference for all subcommands
        for (SlashCommand subCmd : new java.util.HashSet<>(subs.values())) {
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
        SlashCommand cmd = build();
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
