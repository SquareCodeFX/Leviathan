package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.parser.ArgumentParser;
import de.feelix.leviathan.util.Preconditions;

/**
 * Describes a single command argument with its parser and metadata.
 * <p>
 * Instances are immutable and can be safely reused across commands. Argument names must be
 * non-blank and contain no whitespace. The optional permission, when set, is enforced during
 * execution and tab completion.
 *
 * @param <T> parsed value type for this argument
 */
public final class Arg<T> {
    private final String name;
    private final ArgumentParser<T> parser;
    private final ArgContext context;
    private final OptionType optionType; // broad type hint for mapping

    /**
     * Create an argument with default context.
     * @param name argument name (no whitespace)
     * @param optional whether this argument is optional
     * @param parser parser used to parse this argument's token(s)
     */
    public Arg(@NotNull String name, boolean optional, @NotNull ArgumentParser<T> parser) {
        this(name, parser, ArgContext.builder().optional(optional).build());
    }

    /**
     * Create an argument with a permission requirement (default greedy=false).
     * @param name argument name (no whitespace)
     * @param optional whether this argument is optional
     * @param parser parser used to parse this argument's token(s)
     * @param permission required permission to use/see this argument (null/blank for none)
     */
    public Arg(@NotNull String name, boolean optional, @NotNull ArgumentParser<T> parser, @Nullable String permission) {
        this(name, parser, ArgContext.builder().optional(optional).permission(permission).build());
    }

    /**
     * Full constructor allowing greedy configuration.
     * @param name argument name (no whitespace)
     * @param optional whether this argument is optional
     * @param parser parser used to parse this argument's token(s)
     * @param permission required permission to use/see this argument (null/blank for none)
     * @param greedy if true and this is last, captures the rest of the input as one string value
     */
    public Arg(@NotNull String name, boolean optional, @NotNull ArgumentParser<T> parser, @Nullable String permission, boolean greedy) {
        this(name, parser, ArgContext.builder().optional(optional).permission(permission).greedy(greedy).build());
    }

    /**
     * Construct an argument with an explicit {@link ArgContext}.
     */
    public Arg(@NotNull String name, @NotNull ArgumentParser<T> parser, @NotNull ArgContext context) {
        this.name = Preconditions.checkNotNull(name, "name");
        if (this.name.isBlank()) {
            throw new CommandConfigurationException("Argument name must not be blank");
        }
        if (this.name.chars().anyMatch(Character::isWhitespace)) {
            throw new CommandConfigurationException("Argument name must not contain whitespace: '" + this.name + "'");
        }
        this.parser = Preconditions.checkNotNull(parser, "parser");
        this.context = Preconditions.checkNotNull(context, "context");
        // infer option type from parser's public type name
        OptionType inferred;
        try {
            inferred = OptionType.fromTypeName(this.parser.getTypeName());
        } catch (Throwable t) {
            inferred = OptionType.UNKNOWN;
        }
        this.optionType = inferred;
    }

    /**
     * @return the argument name as used in the {@code CommandContext}
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return whether the argument is optional
     */
    public boolean optional() {
        return context.optional();
    }

    /**
     * @return the parser responsible for converting raw input to the target type
     */
    public @NotNull ArgumentParser<T> parser() {
        return parser;
    }

    /**
     * @return the required permission to use this argument, or null if none
     */
    public @Nullable String permission() { return context.permission(); }

    /**
     * @return true if this is a greedy trailing string argument
     */
    public boolean greedy() { return context.greedy(); }

    /**
     * @return the broad option type for this argument, inferred from its parser
     */
    public @NotNull OptionType optionType() { return optionType; }

    /**
     * @return the associated {@link ArgContext}
     */
    public @NotNull ArgContext context() { return context; }

    /**
     * Return a copy of this argument with updated optionality.
     */
    public @NotNull Arg<T> optional(boolean optional) {
        ArgContext.Builder b = ArgContext.builder()
                .optional(optional)
                .greedy(context.greedy())
                .permission(context.permission())
                .completionsPredefined(new java.util.ArrayList<>(context.completionsPredefined()))
                .completionsDynamic(context.completionsDynamic());
        return new Arg<>(name, parser, b.build());
    }

    /**
     * Return a copy of this argument with an updated permission requirement.
     */
    public @NotNull Arg<T> withPermission(@Nullable String permission) {
        ArgContext.Builder b = ArgContext.builder()
                .optional(context.optional())
                .greedy(context.greedy())
                .permission(permission)
                .completionsPredefined(new java.util.ArrayList<>(context.completionsPredefined()))
                .completionsDynamic(context.completionsDynamic());
        return new Arg<>(name, parser, b.build());
    }

    /**
     * Return a copy of this argument with updated greedy flag.
     */
    public @NotNull Arg<T> withGreedy(boolean greedy) {
        ArgContext.Builder b = ArgContext.builder()
                .optional(context.optional())
                .greedy(greedy)
                .permission(context.permission())
                .completionsPredefined(new java.util.ArrayList<>(context.completionsPredefined()))
                .completionsDynamic(context.completionsDynamic());
        return new Arg<>(name, parser, b.build());
    }
}
