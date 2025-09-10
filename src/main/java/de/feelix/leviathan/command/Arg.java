package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.parser.ArgumentParser;

import java.util.Objects;

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
    private final boolean optional;
    private final ArgumentParser<T> parser;
    private final String permission; // optional permission required to use/see this argument
    private final boolean greedy; // if true and this is the last argument, it will capture the rest of the input as a single string

    /**
     * Create an argument.
     * @param name argument name (no whitespace)
     * @param optional whether this argument is optional
     * @param parser parser used to parse this argument's token(s)
     */
    public Arg(@NotNull String name, boolean optional, @NotNull ArgumentParser<T> parser) {
        this(name, optional, parser, null, false);
    }

    /**
     * Create an argument with a permission requirement.
     * @param name argument name (no whitespace)
     * @param optional whether this argument is optional
     * @param parser parser used to parse this argument's token(s)
     * @param permission required permission to use/see this argument (null/blank for none)
     */
    public Arg(@NotNull String name, boolean optional, @NotNull ArgumentParser<T> parser, @Nullable String permission) {
        this(name, optional, parser, permission, false);
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
        this.name = Objects.requireNonNull(name, "name");
        if (this.name.isBlank()) {
            throw new CommandConfigurationException("Argument name must not be blank");
        }
        if (this.name.chars().anyMatch(Character::isWhitespace)) {
            throw new CommandConfigurationException("Argument name must not contain whitespace: '" + this.name + "'");
        }
        this.optional = optional;
        this.parser = Objects.requireNonNull(parser, "parser");
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
        this.greedy = greedy;
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
        return optional;
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
    public @Nullable String permission() { return permission; }

    /**
     * @return true if this is a greedy trailing string argument
     */
    public boolean greedy() { return greedy; }

    /**
     * Return a copy of this argument with updated optionality.
     */
    public @NotNull Arg<T> optional(boolean optional) {
        return new Arg<>(name, optional, parser, permission, greedy);
    }

    /**
     * Return a copy of this argument with an updated permission requirement.
     */
    public @NotNull Arg<T> withPermission(@Nullable String permission) {
        return new Arg<>(name, optional, parser, permission, greedy);
    }

    /**
     * Return a copy of this argument with updated greedy flag.
     */
    public @NotNull Arg<T> withGreedy(boolean greedy) {
        return new Arg<>(name, optional, parser, permission, greedy);
    }
}
