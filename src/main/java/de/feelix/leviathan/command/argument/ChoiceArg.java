package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.function.Function;

/**
 * A type-safe choice argument that restricts values to a predefined set.
 * <p>
 * Choice arguments provide several advantages over regular string arguments:
 * <ul>
 *   <li>Type safety - values are validated at parse time</li>
 *   <li>Tab completion - available choices are suggested automatically</li>
 *   <li>Display names - choices can have user-friendly display names</li>
 *   <li>Mapped values - string choices can map to arbitrary objects</li>
 *   <li>Case insensitivity - choices are matched case-insensitively by default</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Simple string choices
 * ChoiceArg<String> colorChoice = ChoiceArg.ofStrings("color", "red", "green", "blue");
 *
 * // Enum choices (automatic)
 * ChoiceArg<GameMode> modeChoice = ChoiceArg.ofEnum("mode", GameMode.class);
 *
 * // Custom mapped choices
 * ChoiceArg<Integer> sizeChoice = ChoiceArg.<Integer>builder("size")
 *     .choice("small", 10)
 *     .choice("medium", 25)
 *     .choice("large", 50)
 *     .build();
 *
 * // With display names
 * ChoiceArg<Difficulty> diffChoice = ChoiceArg.<Difficulty>builder("difficulty")
 *     .choice("easy", Difficulty.EASY, "Easy Mode")
 *     .choice("normal", Difficulty.NORMAL, "Normal Mode")
 *     .choice("hard", Difficulty.HARD, "Hard Mode")
 *     .build();
 * }</pre>
 *
 * @param <T> the type of value the choice represents
 */
public final class ChoiceArg<T> {

    private final String name;
    private final Map<String, Choice<T>> choices;
    private final List<String> choiceKeys;
    private final boolean caseSensitive;
    private final String description;
    private final ArgumentParser<T> parser;

    private ChoiceArg(Builder<T> builder) {
        this.name = builder.name;
        this.choices = Collections.unmodifiableMap(new LinkedHashMap<>(builder.choices));
        this.choiceKeys = Collections.unmodifiableList(new ArrayList<>(builder.choices.keySet()));
        this.caseSensitive = builder.caseSensitive;
        this.description = builder.description;
        this.parser = createParser();
    }

    /**
     * Create a builder for a choice argument.
     *
     * @param name the argument name
     * @param <T>  the value type
     * @return a new builder
     */
    public static <T> @NotNull Builder<T> builder(@NotNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a choice argument from string values.
     * Each string maps to itself.
     *
     * @param name   the argument name
     * @param values the allowed string values
     * @return a new choice argument
     */
    public static @NotNull ChoiceArg<String> ofStrings(@NotNull String name, @NotNull String... values) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(values, "values");
        Builder<String> builder = new Builder<>(name);
        for (String value : values) {
            builder.choice(value, value);
        }
        return builder.build();
    }

    /**
     * Create a choice argument from a collection of string values.
     *
     * @param name   the argument name
     * @param values the allowed string values
     * @return a new choice argument
     */
    public static @NotNull ChoiceArg<String> ofStrings(@NotNull String name, @NotNull Collection<String> values) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(values, "values");
        Builder<String> builder = new Builder<>(name);
        for (String value : values) {
            builder.choice(value, value);
        }
        return builder.build();
    }

    /**
     * Create a choice argument from an enum class.
     * Uses enum constant names (lowercase) as choice keys.
     *
     * @param name      the argument name
     * @param enumClass the enum class
     * @param <E>       the enum type
     * @return a new choice argument
     */
    public static <E extends Enum<E>> @NotNull ChoiceArg<E> ofEnum(@NotNull String name, @NotNull Class<E> enumClass) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(enumClass, "enumClass");
        Builder<E> builder = new Builder<>(name);
        for (E constant : enumClass.getEnumConstants()) {
            builder.choice(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        return builder.build();
    }

    /**
     * Create a choice argument from an enum class with a custom key mapper.
     *
     * @param name      the argument name
     * @param enumClass the enum class
     * @param keyMapper function to convert enum constants to choice keys
     * @param <E>       the enum type
     * @return a new choice argument
     */
    public static <E extends Enum<E>> @NotNull ChoiceArg<E> ofEnum(
            @NotNull String name,
            @NotNull Class<E> enumClass,
            @NotNull Function<E, String> keyMapper) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(enumClass, "enumClass");
        Preconditions.checkNotNull(keyMapper, "keyMapper");
        Builder<E> builder = new Builder<>(name);
        for (E constant : enumClass.getEnumConstants()) {
            builder.choice(keyMapper.apply(constant), constant);
        }
        return builder.build();
    }

    /**
     * Create a choice argument from a map of key-value pairs.
     *
     * @param name   the argument name
     * @param values map of choice keys to values
     * @param <T>    the value type
     * @return a new choice argument
     */
    public static <T> @NotNull ChoiceArg<T> ofMap(@NotNull String name, @NotNull Map<String, T> values) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(values, "values");
        Builder<T> builder = new Builder<>(name);
        for (Map.Entry<String, T> entry : values.entrySet()) {
            builder.choice(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    /**
     * @return the argument name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return the description, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * @return an unmodifiable list of available choice keys
     */
    public @NotNull List<String> choiceKeys() {
        return choiceKeys;
    }

    /**
     * @return true if matching is case-sensitive
     */
    public boolean caseSensitive() {
        return caseSensitive;
    }

    /**
     * Get the value for a choice key.
     *
     * @param key the choice key
     * @return the value, or null if not found
     */
    public @Nullable T getValue(@NotNull String key) {
        Choice<T> choice = findChoice(key);
        return choice != null ? choice.value() : null;
    }

    /**
     * Check if a key is a valid choice.
     *
     * @param key the key to check
     * @return true if the key is a valid choice
     */
    public boolean isValidChoice(@NotNull String key) {
        return findChoice(key) != null;
    }

    /**
     * Get the display name for a choice key.
     *
     * @param key the choice key
     * @return the display name, or the key itself if no display name is set
     */
    public @NotNull String getDisplayName(@NotNull String key) {
        Choice<T> choice = findChoice(key);
        if (choice != null && choice.displayName() != null) {
            return choice.displayName();
        }
        return key;
    }

    /**
     * Get the argument parser for this choice argument.
     *
     * @return the argument parser
     */
    public @NotNull ArgumentParser<T> parser() {
        return parser;
    }

    /**
     * Convert this choice argument to an Arg with default configuration.
     *
     * @return a new Arg instance
     */
    public @NotNull Arg<T> toArg() {
        return new Arg<>(name, parser, ArgContext.builder()
                .description(description)
                .completions(choiceKeys)
                .didYouMean(true)
                .build());
    }

    /**
     * Convert this choice argument to an Arg with custom context.
     *
     * @param context the argument context
     * @return a new Arg instance
     */
    public @NotNull Arg<T> toArg(@NotNull ArgContext context) {
        Preconditions.checkNotNull(context, "context");
        // Merge completions with choice keys
        List<String> completions = new ArrayList<>(choiceKeys);
        completions.addAll(context.completionsPredefined());
        return new Arg<>(name, parser, ArgContext.builder()
                .from(context)
                .completions(completions)
                .didYouMean(true)
                .build());
    }

    private @Nullable Choice<T> findChoice(@NotNull String key) {
        if (caseSensitive) {
            return choices.get(key);
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Choice<T>> entry : choices.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private ArgumentParser<T> createParser() {
        return new ArgumentParser<T>() {
            @Override
            public @NotNull ParseResult<T> parse(@NotNull String input, @NotNull CommandSender sender) {
                Choice<T> choice = findChoice(input);
                if (choice == null) {
                    String validChoices = String.join(", ", choiceKeys);
                    return ParseResult.failure("must be one of: " + validChoices);
                }
                return ParseResult.success(choice.value());
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull String partial, @NotNull CommandSender sender) {
                String lowerPartial = caseSensitive ? partial : partial.toLowerCase(Locale.ROOT);
                List<String> suggestions = new ArrayList<>();
                for (String key : choiceKeys) {
                    String matchKey = caseSensitive ? key : key.toLowerCase(Locale.ROOT);
                    if (matchKey.startsWith(lowerPartial)) {
                        suggestions.add(key);
                    }
                }
                return suggestions;
            }

            @Override
            public @NotNull String getTypeName() {
                return "choice";
            }
        };
    }

    /**
     * Internal representation of a single choice.
     */
    private record Choice<T>(T value, @Nullable String displayName) {}

    /**
     * Builder for {@link ChoiceArg}.
     *
     * @param <T> the value type
     */
    public static final class Builder<T> {
        private final String name;
        private final Map<String, Choice<T>> choices = new LinkedHashMap<>();
        private boolean caseSensitive = false;
        private String description;

        private Builder(@NotNull String name) {
            this.name = Preconditions.checkNotNull(name, "name");
        }

        /**
         * Add a choice with just a key and value.
         *
         * @param key   the choice key (what users type)
         * @param value the value to return when this choice is selected
         * @return this builder
         */
        public @NotNull Builder<T> choice(@NotNull String key, @NotNull T value) {
            return choice(key, value, null);
        }

        /**
         * Add a choice with a key, value, and display name.
         *
         * @param key         the choice key (what users type)
         * @param value       the value to return when this choice is selected
         * @param displayName the user-friendly display name (optional)
         * @return this builder
         */
        public @NotNull Builder<T> choice(@NotNull String key, @NotNull T value, @Nullable String displayName) {
            Preconditions.checkNotNull(key, "key");
            Preconditions.checkNotNull(value, "value");
            choices.put(key, new Choice<>(value, displayName));
            return this;
        }

        /**
         * Add multiple choices from a map.
         *
         * @param choices map of key-value pairs
         * @return this builder
         */
        public @NotNull Builder<T> choices(@NotNull Map<String, T> choices) {
            Preconditions.checkNotNull(choices, "choices");
            for (Map.Entry<String, T> entry : choices.entrySet()) {
                choice(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * Set whether matching is case-sensitive.
         * Default is false (case-insensitive).
         *
         * @param caseSensitive true for case-sensitive matching
         * @return this builder
         */
        public @NotNull Builder<T> caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        /**
         * Set the description for this choice argument.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Build the choice argument.
         *
         * @return a new ChoiceArg instance
         * @throws IllegalStateException if no choices were added
         */
        public @NotNull ChoiceArg<T> build() {
            if (choices.isEmpty()) {
                throw new IllegalStateException("At least one choice must be added");
            }
            return new ChoiceArg<>(this);
        }
    }
}
