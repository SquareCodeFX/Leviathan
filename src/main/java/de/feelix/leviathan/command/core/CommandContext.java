package de.feelix.leviathan.command.core;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.mapping.OptionMapping;
import de.feelix.leviathan.command.mapping.OptionType;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Function;

/**
 * Holds parsed argument values and provides type-safe accessors for command actions.
 * <p>
 * The context is immutable and contains:
 * <ul>
 *   <li>A map of argument name to parsed value (only provided arguments are present)</li>
 *   <li>A map of flag name to boolean value</li>
 *   <li>A map of key-value name to parsed value</li>
 *   <li>A map of multi-value key-value name to list of parsed values</li>
 *   <li>The raw argument array as received from Bukkit</li>
 * </ul>
 */
public final class CommandContext {
    private final Map<String, Object> values;
    private final Map<String, Boolean> flagValues;
    private final Map<String, Object> keyValuePairs;
    private final Map<String, List<Object>> multiValuePairs;
    private final String[] rawArgs;

    /**
     * Create a new command context.
     *
     * @param values  parsed name-to-value mapping
     * @param rawArgs raw argument tokens provided by Bukkit
     */
    public CommandContext(@NotNull Map<String, Object> values, @NotNull String[] rawArgs) {
        this(values, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), rawArgs);
    }

    /**
     * Create a new command context with flags and key-value pairs.
     *
     * @param values          parsed argument name-to-value mapping
     * @param flagValues      parsed flag name-to-boolean mapping
     * @param keyValuePairs   parsed key-value name-to-value mapping (single values)
     * @param multiValuePairs parsed key-value name-to-list mapping (multiple values)
     * @param rawArgs         raw argument tokens provided by Bukkit
     */
    public CommandContext(@NotNull Map<String, Object> values,
                          @NotNull Map<String, Boolean> flagValues,
                          @NotNull Map<String, Object> keyValuePairs,
                          @NotNull Map<String, List<Object>> multiValuePairs,
                          @NotNull String[] rawArgs) {
        this.values = Map.copyOf(Preconditions.checkNotNull(values, "values"));
        this.flagValues = Map.copyOf(Preconditions.checkNotNull(flagValues, "flagValues"));
        this.keyValuePairs = Map.copyOf(Preconditions.checkNotNull(keyValuePairs, "keyValuePairs"));
        this.multiValuePairs = Map.copyOf(Preconditions.checkNotNull(multiValuePairs, "multiValuePairs"));
        this.rawArgs = Preconditions.checkNotNull(rawArgs, "rawArgs").clone();
    }

    /**
     * @return a clone of the raw argument array
     */
    public @NotNull String[] raw() {
        return rawArgs.clone();
    }

    /**
     * Retrieve an optional typed value by argument name.
     *
     * @param name argument name
     * @param type expected type class
     * @return Optional of the value if present and assignable to the given type, otherwise empty
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> Optional<T> optional(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Object o = values.get(name);
        if (o == null) return Optional.empty();
        if (!type.isInstance(o)) return Optional.empty();
        return Optional.of((T) o);
    }

    /**
     * Retrieve a typed value by argument name, returning null when missing or of a different type.
     *
     * @param name argument name
     * @param type expected type class
     * @return the value or null
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T> T get(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Object o = values.get(name);
        if (o == null) return null;
        if (!type.isInstance(o)) return null;
        return (T) o;
    }

    /**
     * Strictly retrieve a value by name, throwing if it is missing or of the wrong type.
     *
     * @param name argument name
     * @param type expected type class
     * @return non-null value of the requested type
     * @throws ApiMisuseException if missing or type-incompatible
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> T orThrow(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        if (!values.containsKey(name)) {
            throw new ApiMisuseException("Required argument '" + name + "' is missing in CommandContext");
        }
        Object o = values.get(name);
        if (!type.isInstance(o)) {
            String actual = (o == null) ? "null" : o.getClass().getName();
            throw new ApiMisuseException(
                "Argument '" + name + "' has type " + actual + ", not assignable to " + type.getName());
        }
        return (T) o;
    }

    /**
     * Alias for {@link #orThrow(String, Class)} for readability when a required argument is expected.
     */
    public @NotNull <T> T require(@NotNull String name, @NotNull Class<T> type) {
        return orThrow(name, type);
    }

    /**
     * Whether a value with the given name exists in the context.
     */
    public boolean has(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return values.containsKey(name);
    }

    /**
     * Retrieve a typed value by argument name, returning the provided default value when missing or of a different
     * type.
     *
     * @param name         argument name
     * @param type         expected type class
     * @param defaultValue value to return if the argument is missing or has a different type
     * @return the value or the default value
     */
    public @NotNull <T> T getOrDefault(@NotNull String name, @NotNull Class<T> type, @NotNull T defaultValue) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(defaultValue, "defaultValue");
        T value = get(name, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Convenience method to retrieve a String argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the string value or the default value
     */
    public @NotNull String getStringOrDefault(@NotNull String name, @NotNull String defaultValue) {
        return getOrDefault(name, String.class, defaultValue);
    }

    /**
     * Convenience method to retrieve an Integer argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the integer value or the default value
     */
    public @NotNull Integer getIntOrDefault(@NotNull String name, @NotNull Integer defaultValue) {
        return getOrDefault(name, Integer.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a Long argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the long value or the default value
     */
    public @NotNull Long getLongOrDefault(@NotNull String name, @NotNull Long defaultValue) {
        return getOrDefault(name, Long.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a Double argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the double value or the default value
     */
    public @NotNull Double getDoubleOrDefault(@NotNull String name, @NotNull Double defaultValue) {
        return getOrDefault(name, Double.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a Float argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the float value or the default value
     */
    public @NotNull Float getFloatOrDefault(@NotNull String name, @NotNull Float defaultValue) {
        return getOrDefault(name, Float.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a Boolean argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the boolean value or the default value
     */
    public @NotNull Boolean getBooleanOrDefault(@NotNull String name, @NotNull Boolean defaultValue) {
        return getOrDefault(name, Boolean.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a UUID argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the UUID value or the default value
     */
    public @NotNull UUID getUuidOrDefault(@NotNull String name, @NotNull UUID defaultValue) {
        return getOrDefault(name, UUID.class, defaultValue);
    }

    /**
     * Convenience method to retrieve a Player argument or return a default value.
     *
     * @param name         argument name
     * @param defaultValue value to return if the argument is missing
     * @return the Player value or the default value
     */
    public @NotNull Player getPlayerOrDefault(@NotNull String name, @NotNull Player defaultValue) {
        return getOrDefault(name, Player.class, defaultValue);
    }

    /**
     * Functional retrieval using an {@link OptionMapping} and a mapper function.
     * Example usage: {@code String n = ctx.arg("name", ArgumentMapper::asString);}.
     *
     * @return the mapped value, or null if the argument is not available
     */
    public <T> @Nullable T arg(@NotNull String name, @NotNull Function<OptionMapping, T> mapper) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(mapper, "mapper");
        return mapper.apply(new MappingImpl(name));
    }

    private final class MappingImpl implements OptionMapping {
        private final String name;

        private MappingImpl(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String name() {
            return name;
        }

        @Override
        public Object raw() {
            return values.get(name);
        }

        @Override
        public @NotNull OptionType optionType() {
            return inferType(raw());
        }

        @Override
        public <T> @Nullable T as(@NotNull Class<T> type) {
            Preconditions.checkNotNull(type, "type");
            if (!values.containsKey(name)) {
                return null;
            }
            Object o = values.get(name);
            if (o == null) {
                return null;
            }
            if (!type.isInstance(o)) {
                return null;
            }
            @SuppressWarnings("unchecked") T t = (T) o;
            return t;
        }

        @Override
        public @Nullable String asString() {
            return as(String.class);
        }

        @Override
        public @Nullable Integer asInt() {
            return as(Integer.class);
        }

        @Override
        public @Nullable Long asLong() {
            return as(Long.class);
        }

        @Override
        public @Nullable UUID asUuid() {
            return as(UUID.class);
        }
    }

    private static @NotNull OptionType inferType(Object o) {
        if (o instanceof Integer) return OptionType.INT;
        if (o instanceof Long) return OptionType.LONG;
        if (o instanceof Double) return OptionType.DOUBLE;
        if (o instanceof Float) return OptionType.FLOAT;
        if (o instanceof String) return OptionType.STRING;
        if (o instanceof UUID) return OptionType.UUID;
        if (o instanceof Boolean) return OptionType.BOOLEAN;
        if (o instanceof Player) return OptionType.PLAYER;
        return (o == null) ? OptionType.UNKNOWN : OptionType.CHOICE;
    }

    // ================================
    // FLAG ACCESSOR METHODS
    // ================================

    /**
     * Get a flag value by name.
     * <p>
     * Flags are boolean switches that can be enabled via command-line style arguments:
     * <ul>
     *   <li>Short form: {@code -s}</li>
     *   <li>Long form: {@code --silent}</li>
     *   <li>Combined: {@code -sf}</li>
     *   <li>Negated: {@code --no-confirm}</li>
     * </ul>
     *
     * @param name the flag name
     * @return the flag value, or false if not present
     */
    public boolean getFlag(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return flagValues.getOrDefault(name, false);
    }

    /**
     * Get a flag value by name with a specified default.
     *
     * @param name         the flag name
     * @param defaultValue value to return if flag is not defined
     * @return the flag value or default
     */
    public boolean getFlag(@NotNull String name, boolean defaultValue) {
        Preconditions.checkNotNull(name, "name");
        return flagValues.getOrDefault(name, defaultValue);
    }

    /**
     * Check if a flag is defined in this context.
     *
     * @param name the flag name
     * @return true if the flag exists in the context
     */
    public boolean hasFlag(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return flagValues.containsKey(name);
    }

    /**
     * @return unmodifiable view of all flag values
     */
    public @NotNull Map<String, Boolean> allFlags() {
        return flagValues;
    }

    // ================================
    // KEY-VALUE ACCESSOR METHODS
    // ================================

    /**
     * Get a key-value pair by name.
     * <p>
     * Key-value pairs support multiple input formats:
     * <ul>
     *   <li>{@code key=value}</li>
     *   <li>{@code key:value}</li>
     *   <li>{@code --key value}</li>
     *   <li>{@code --key=value}</li>
     * </ul>
     *
     * @param name the key-value name
     * @param type the expected type class
     * @param <T>  the type
     * @return the value or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public @Nullable <T> T getKeyValue(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Object value = keyValuePairs.get(name);
        if (value == null) return null;
        if (!type.isInstance(value)) return null;
        return (T) value;
    }

    /**
     * Get a key-value pair by name with a default value.
     *
     * @param name         the key-value name
     * @param type         the expected type class
     * @param defaultValue value to return if not present or wrong type
     * @param <T>          the type
     * @return the value or default
     */
    public @NotNull <T> T getKeyValue(@NotNull String name, @NotNull Class<T> type, @NotNull T defaultValue) {
        Preconditions.checkNotNull(defaultValue, "defaultValue");
        T value = getKeyValue(name, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a key-value pair as Optional.
     *
     * @param name the key-value name
     * @param type the expected type class
     * @param <T>  the type
     * @return Optional containing the value, or empty
     */
    public @NotNull <T> Optional<T> optionalKeyValue(@NotNull String name, @NotNull Class<T> type) {
        return Optional.ofNullable(getKeyValue(name, type));
    }

    /**
     * Check if a key-value pair exists in this context.
     *
     * @param name the key-value name
     * @return true if the key-value exists
     */
    public boolean hasKeyValue(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return keyValuePairs.containsKey(name);
    }

    /**
     * Get a string key-value pair.
     *
     * @param name the key-value name
     * @return the string value or null
     */
    public @Nullable String getKeyValueString(@NotNull String name) {
        return getKeyValue(name, String.class);
    }

    /**
     * Get a string key-value pair with a default.
     *
     * @param name         the key-value name
     * @param defaultValue default value
     * @return the string value or default
     */
    public @NotNull String getKeyValueString(@NotNull String name, @NotNull String defaultValue) {
        return getKeyValue(name, String.class, defaultValue);
    }

    /**
     * Get an integer key-value pair.
     *
     * @param name the key-value name
     * @return the integer value or null
     */
    public @Nullable Integer getKeyValueInt(@NotNull String name) {
        return getKeyValue(name, Integer.class);
    }

    /**
     * Get an integer key-value pair with a default.
     *
     * @param name         the key-value name
     * @param defaultValue default value
     * @return the integer value or default
     */
    public int getKeyValueInt(@NotNull String name, int defaultValue) {
        Integer value = getKeyValueInt(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a boolean key-value pair.
     *
     * @param name the key-value name
     * @return the boolean value or null
     */
    public @Nullable Boolean getKeyValueBoolean(@NotNull String name) {
        return getKeyValue(name, Boolean.class);
    }

    /**
     * Get a boolean key-value pair with a default.
     *
     * @param name         the key-value name
     * @param defaultValue default value
     * @return the boolean value or default
     */
    public boolean getKeyValueBoolean(@NotNull String name, boolean defaultValue) {
        Boolean value = getKeyValueBoolean(name);
        return value != null ? value : defaultValue;
    }

    /**
     * @return unmodifiable view of all key-value pairs (single values)
     */
    public @NotNull Map<String, Object> allKeyValues() {
        return keyValuePairs;
    }

    // ================================
    // MULTI-VALUE ACCESSOR METHODS
    // ================================

    /**
     * Get multiple values for a key-value pair.
     * <p>
     * Multiple values are specified like: {@code tags=pvp,survival,hardcore}
     *
     * @param name the key-value name
     * @param <T>  the element type
     * @return list of values, or empty list if not present
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> List<T> getMultiValue(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        List<Object> values = multiValuePairs.get(name);
        if (values == null) return Collections.emptyList();
        return (List<T>) Collections.unmodifiableList(values);
    }

    /**
     * Get multiple values for a key-value pair with type checking.
     *
     * @param name        the key-value name
     * @param elementType the expected element type class
     * @param <T>         the element type
     * @return list of values matching the type, or empty list
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> List<T> getMultiValue(@NotNull String name, @NotNull Class<T> elementType) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(elementType, "elementType");
        List<Object> values = multiValuePairs.get(name);
        if (values == null) return Collections.emptyList();
        List<T> result = new ArrayList<>();
        for (Object value : values) {
            if (elementType.isInstance(value)) {
                result.add((T) value);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Check if a multi-value key-value pair exists.
     *
     * @param name the key-value name
     * @return true if multi-values exist for this key
     */
    public boolean hasMultiValue(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return multiValuePairs.containsKey(name) && !multiValuePairs.get(name).isEmpty();
    }

    /**
     * @return unmodifiable view of all multi-value pairs
     */
    public @NotNull Map<String, List<Object>> allMultiValues() {
        return multiValuePairs;
    }
}
