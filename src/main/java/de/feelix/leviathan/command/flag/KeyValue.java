package de.feelix.leviathan.command.flag;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgumentParser;
import de.feelix.leviathan.command.argument.ArgParsers;
import de.feelix.leviathan.exceptions.CommandConfigurationException;
import de.feelix.leviathan.util.Preconditions;

import java.util.List;

/**
 * Describes a key-value pair parameter for commands.
 * <p>
 * Key-value pairs support multiple formats:
 * <ul>
 *   <li>{@code key=value}</li>
 *   <li>{@code key:value}</li>
 *   <li>{@code --key value}</li>
 *   <li>{@code --key=value}</li>
 * </ul>
 * <p>
 * Additional features:
 * <ul>
 *   <li>Type parsing: String, Integer, Long, Double, Float, Boolean, Enum</li>
 *   <li>Default values when not present</li>
 *   <li>Required vs optional support</li>
 *   <li>Multiple values: {@code tags=pvp,survival,hardcore}</li>
 *   <li>Quoted values for spaces: {@code reason="This is the reason"}</li>
 * </ul>
 * <p>
 * Instances are immutable and can be safely reused across commands.
 *
 * @param <T> the type of the parsed value
 */
public final class KeyValue<T> {
    private final String name;
    private final @Nullable String longForm;
    private final ArgumentParser<T> parser;
    private final @Nullable String description;
    private final @Nullable T defaultValue;
    private final boolean required;
    private final boolean multipleValues;
    private final @Nullable String valueSeparator;
    private final @Nullable String permission;

    private KeyValue(@NotNull String name,
                     @Nullable String longForm,
                     @NotNull ArgumentParser<T> parser,
                     @Nullable String description,
                     @Nullable T defaultValue,
                     boolean required,
                     boolean multipleValues,
                     @Nullable String valueSeparator,
                     @Nullable String permission) {
        this.name = Preconditions.checkNotNull(name, "name");
        if (this.name.isBlank()) {
            throw new CommandConfigurationException("KeyValue name must not be blank");
        }
        if (this.name.chars().anyMatch(Character::isWhitespace)) {
            throw new CommandConfigurationException("KeyValue name must not contain whitespace: '" + this.name + "'");
        }
        this.parser = Preconditions.checkNotNull(parser, "parser");
        if (longForm != null && longForm.isBlank()) {
            throw new CommandConfigurationException("KeyValue long form must not be blank if provided");
        }
        if (longForm != null && longForm.contains(" ")) {
            throw new CommandConfigurationException("KeyValue long form must not contain spaces: '" + longForm + "'");
        }
        if (required && defaultValue != null) {
            throw new CommandConfigurationException("Required KeyValue '" + name + "' should not have a default value");
        }
        this.longForm = longForm;
        this.description = description;
        this.defaultValue = defaultValue;
        this.required = required;
        this.multipleValues = multipleValues;
        this.valueSeparator = (valueSeparator == null || valueSeparator.isEmpty()) ? "," : valueSeparator;
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
    }

    /**
     * Create a new key-value builder with the given name and parser.
     *
     * @param name   the key name used to retrieve the value from context
     * @param parser the parser for converting string values to the target type
     * @param <T>    the target type
     * @return a new key-value builder
     */
    public static <T> @NotNull Builder<T> builder(@NotNull String name, @NotNull ArgumentParser<T> parser) {
        return new Builder<>(name, parser);
    }

    /**
     * Create a required string key-value pair.
     *
     * @param name the key name
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<String> ofString(@NotNull String name) {
        return builder(name, ArgParsers.stringParser()).required(true).build();
    }

    /**
     * Create an optional string key-value pair with a default value.
     *
     * @param name         the key name
     * @param defaultValue the default value when not provided
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<String> ofString(@NotNull String name, @NotNull String defaultValue) {
        return builder(name, ArgParsers.stringParser()).defaultValue(defaultValue).build();
    }

    /**
     * Create a required integer key-value pair.
     *
     * @param name the key name
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Integer> ofInt(@NotNull String name) {
        return builder(name, ArgParsers.intParser()).required(true).build();
    }

    /**
     * Create an optional integer key-value pair with a default value.
     *
     * @param name         the key name
     * @param defaultValue the default value when not provided
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Integer> ofInt(@NotNull String name, int defaultValue) {
        return builder(name, ArgParsers.intParser()).defaultValue(defaultValue).build();
    }

    /**
     * Create a required long key-value pair.
     *
     * @param name the key name
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Long> ofLong(@NotNull String name) {
        return builder(name, ArgParsers.longParser()).required(true).build();
    }

    /**
     * Create an optional long key-value pair with a default value.
     *
     * @param name         the key name
     * @param defaultValue the default value when not provided
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Long> ofLong(@NotNull String name, long defaultValue) {
        return builder(name, ArgParsers.longParser()).defaultValue(defaultValue).build();
    }

    /**
     * Create a required double key-value pair.
     *
     * @param name the key name
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Double> ofDouble(@NotNull String name) {
        return builder(name, ArgParsers.doubleParser()).required(true).build();
    }

    /**
     * Create an optional double key-value pair with a default value.
     *
     * @param name         the key name
     * @param defaultValue the default value when not provided
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Double> ofDouble(@NotNull String name, double defaultValue) {
        return builder(name, ArgParsers.doubleParser()).defaultValue(defaultValue).build();
    }

    /**
     * Create a required boolean key-value pair.
     *
     * @param name the key name
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Boolean> ofBoolean(@NotNull String name) {
        return builder(name, ArgParsers.booleanParser()).required(true).build();
    }

    /**
     * Create an optional boolean key-value pair with a default value.
     *
     * @param name         the key name
     * @param defaultValue the default value when not provided
     * @return a new key-value instance
     */
    public static @NotNull KeyValue<Boolean> ofBoolean(@NotNull String name, boolean defaultValue) {
        return builder(name, ArgParsers.booleanParser()).defaultValue(defaultValue).build();
    }

    /**
     * Create a required enum key-value pair.
     *
     * @param name      the key name
     * @param enumClass the enum class
     * @param <E>       the enum type
     * @return a new key-value instance
     */
    public static <E extends Enum<E>> @NotNull KeyValue<E> ofEnum(@NotNull String name, @NotNull Class<E> enumClass) {
        return builder(name, ArgParsers.enumParser(enumClass)).required(true).build();
    }

    /**
     * Create an optional enum key-value pair with a default value.
     *
     * @param name         the key name
     * @param enumClass    the enum class
     * @param defaultValue the default value when not provided
     * @param <E>          the enum type
     * @return a new key-value instance
     */
    public static <E extends Enum<E>> @NotNull KeyValue<E> ofEnum(@NotNull String name, @NotNull Class<E> enumClass,
                                                                   @NotNull E defaultValue) {
        return builder(name, ArgParsers.enumParser(enumClass)).defaultValue(defaultValue).build();
    }

    /**
     * @return the key name as used in the {@code CommandContext}
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * @return the long form string (without dashes), or null if using name as key
     */
    public @Nullable String longForm() {
        return longForm;
    }

    /**
     * @return the key used in command input (longForm if set, otherwise name)
     */
    public @NotNull String key() {
        return longForm != null ? longForm : name;
    }

    /**
     * @return the parser for converting string values to the target type
     */
    public @NotNull ArgumentParser<T> parser() {
        return parser;
    }

    /**
     * @return the human-readable description, or null if not set
     */
    public @Nullable String description() {
        return description;
    }

    /**
     * @return the default value when not present, or null
     */
    public @Nullable T defaultValue() {
        return defaultValue;
    }

    /**
     * @return true if this key-value is required
     */
    public boolean required() {
        return required;
    }

    /**
     * @return true if this key-value accepts multiple comma-separated values
     */
    public boolean multipleValues() {
        return multipleValues;
    }

    /**
     * @return the separator used for multiple values (default: ",")
     */
    public @NotNull String valueSeparator() {
        return valueSeparator;
    }

    /**
     * @return the required permission to use this key-value, or null if none
     */
    public @Nullable String permission() {
        return permission;
    }

    /**
     * Check if this key-value matches the given key string.
     *
     * @param keyStr the key string to check (without any prefix like --)
     * @return true if the key matches (case-insensitive)
     */
    public boolean matchesKey(@NotNull String keyStr) {
        return key().equalsIgnoreCase(keyStr);
    }

    /**
     * Builder for creating {@link KeyValue} instances with a fluent API.
     *
     * @param <T> the type of the parsed value
     */
    public static final class Builder<T> {
        private final String name;
        private final ArgumentParser<T> parser;
        private @Nullable String longForm;
        private @Nullable String description;
        private @Nullable T defaultValue;
        private boolean required = false;
        private boolean multipleValues = false;
        private @Nullable String valueSeparator = ",";
        private @Nullable String permission;

        private Builder(@NotNull String name, @NotNull ArgumentParser<T> parser) {
            this.name = name;
            this.parser = parser;
        }

        /**
         * Set a custom long form for this key-value.
         * If not set, the name will be used as the key.
         *
         * @param longForm the key form used in input (e.g., "timeout" for --timeout=value)
         * @return this builder
         */
        public @NotNull Builder<T> longForm(@NotNull String longForm) {
            this.longForm = longForm;
            return this;
        }

        /**
         * Set a human-readable description for this key-value.
         *
         * @param description the description
         * @return this builder
         */
        public @NotNull Builder<T> description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the default value for this key-value when not present.
         * Setting a default value implies the key-value is optional.
         *
         * @param defaultValue the default value
         * @return this builder
         */
        public @NotNull Builder<T> defaultValue(@Nullable T defaultValue) {
            this.defaultValue = defaultValue;
            this.required = false;
            return this;
        }

        /**
         * Set whether this key-value is required.
         * Required key-values cannot have a default value.
         *
         * @param required true if required
         * @return this builder
         */
        public @NotNull Builder<T> required(boolean required) {
            this.required = required;
            if (required) {
                this.defaultValue = null;
            }
            return this;
        }

        /**
         * Mark this key-value as optional.
         *
         * @return this builder
         */
        public @NotNull Builder<T> optional() {
            this.required = false;
            return this;
        }

        /**
         * Enable multiple values support (e.g., tags=pvp,survival,hardcore).
         *
         * @param multipleValues true to enable multiple values
         * @return this builder
         */
        public @NotNull Builder<T> multipleValues(boolean multipleValues) {
            this.multipleValues = multipleValues;
            return this;
        }

        /**
         * Set the separator for multiple values.
         *
         * @param separator the separator string (default: ",")
         * @return this builder
         */
        public @NotNull Builder<T> valueSeparator(@NotNull String separator) {
            this.valueSeparator = separator;
            return this;
        }

        /**
         * Set the required permission to use this key-value.
         *
         * @param permission the permission node, or null for no permission required
         * @return this builder
         */
        public @NotNull Builder<T> permission(@Nullable String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Build the key-value instance.
         *
         * @return a new immutable KeyValue instance
         * @throws CommandConfigurationException if the configuration is invalid
         */
        public @NotNull KeyValue<T> build() {
            return new KeyValue<>(name, longForm, parser, description, defaultValue, required,
                                  multipleValues, valueSeparator, permission);
        }
    }
}
