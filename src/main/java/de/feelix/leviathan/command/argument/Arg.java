package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.command.mapping.OptionType;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private final @Nullable Predicate<CommandContext> condition; // conditional argument evaluation
    private final @Nullable Function<T, T> transformer; // value transformation

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
        this(name, parser, context, null, null);
    }

    /**
     * Private constructor with condition and transformer support.
     */
    private Arg(@NotNull String name, @NotNull ArgumentParser<T> parser, @NotNull ArgContext context,
                @Nullable Predicate<CommandContext> condition, @Nullable Function<T, T> transformer) {
        this.name = Preconditions.checkNotNull(name, "name");
        if (this.name.isBlank()) {
            throw new CommandConfigurationException("Argument name must not be blank");
        }
        if (this.name.chars().anyMatch(Character::isWhitespace)) {
            throw new CommandConfigurationException("Argument name must not contain whitespace: '" + this.name + "'");
        }
        this.parser = Preconditions.checkNotNull(parser, "parser");
        this.context = Preconditions.checkNotNull(context, "context");
        this.condition = condition;
        this.transformer = transformer;
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
     * @return the condition predicate for this argument, or null if none
     */
    public @Nullable Predicate<CommandContext> condition() { return condition; }

    /**
     * @return the transformer function for this argument, or null if none
     */
    public @Nullable Function<T, T> transformer() { return transformer; }

    /**
     * Helper method to copy all properties from the current context into a new builder.
     * This eliminates code duplication in the various withXxx() methods.
     *
     * @return a new builder populated with all current context properties
     */
    private @NotNull ArgContext.Builder copyContextToBuilder() {
        ArgContext.Builder b = ArgContext.builder()
                .optional(context.optional())
                .greedy(context.greedy())
                .permission(context.permission())
                .completionsPredefined(new ArrayList<>(context.completionsPredefined()))
                .completionsDynamic(context.completionsDynamic())
                .intRange(context.intMin(), context.intMax())
                .longRange(context.longMin(), context.longMax())
                .doubleRange(context.doubleMin(), context.doubleMax())
                .floatRange(context.floatMin(), context.floatMax())
                .stringLengthRange(context.stringMinLength(), context.stringMaxLength())
                .stringPattern(context.stringPattern())
                .didYouMean(context.didYouMean())
                .defaultValue(context.defaultValue());
        for (ArgContext.Validator<?> validator : context.customValidators()) {
            b.addValidator(validator);
        }
        return b;
    }

    /**
     * Return a copy of this argument with updated optionality.
     */
    public @NotNull Arg<T> optional(boolean optional) {
        return new Arg<>(name, parser, copyContextToBuilder().optional(optional).build(), condition, transformer);
    }

    /**
     * Return a copy of this argument with an updated permission requirement.
     */
    public @NotNull Arg<T> withPermission(@Nullable String permission) {
        return new Arg<>(name, parser, copyContextToBuilder().permission(permission).build(), condition, transformer);
    }

    /**
     * Return a copy of this argument with updated greedy flag.
     */
    public @NotNull Arg<T> withGreedy(boolean greedy) {
        return new Arg<>(name, parser, copyContextToBuilder().greedy(greedy).build(), condition, transformer);
    }

    /**
     * Return a copy of this argument with a default value.
     * When this argument is not provided by the user, the default value will be used.
     * @param value the default value to use when the argument is missing
     * @return a new Arg instance with the default value set
     */
    public @NotNull Arg<T> defaultValue(@Nullable T value) {
        return new Arg<>(name, parser, copyContextToBuilder().defaultValue(value).build(), condition, transformer);
    }

    /**
     * Return a copy of this argument with a condition.
     * The argument will only be parsed if the condition evaluates to true.
     * @param condition the predicate to evaluate based on previously parsed arguments
     * @return a new Arg instance with the condition set
     */
    public @NotNull Arg<T> withCondition(@Nullable Predicate<CommandContext> condition) {
        return new Arg<>(name, parser, context, condition, transformer);
    }

    /**
     * Return a copy of this argument with a transformer.
     * The transformer will be applied to the parsed value before validation.
     * @param transformer the function to transform the parsed value
     * @return a new Arg instance with the transformer set
     */
    public @NotNull Arg<T> transform(@Nullable Function<T, T> transformer) {
        return new Arg<>(name, parser, context, condition, transformer);
    }
}
