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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // ================================
    // BULK & STREAM ACCESSOR METHODS
    // ================================

    /**
     * Get all parsed argument values as an unmodifiable map.
     *
     * @return unmodifiable view of all argument name-to-value mappings
     */
    public @NotNull Map<String, Object> getAll() {
        return values;
    }

    /**
     * Get a stream of all argument values.
     *
     * @return stream of all values
     */
    public @NotNull Stream<Object> valueStream() {
        return values.values().stream();
    }

    /**
     * Get a stream of all argument entries (name-value pairs).
     *
     * @return stream of all entries
     */
    public @NotNull Stream<Map.Entry<String, Object>> entryStream() {
        return values.entrySet().stream();
    }

    /**
     * Get the first value that matches the given type.
     *
     * @param type the expected type class
     * @param <T>  the type
     * @return Optional containing the first matching value, or empty
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> Optional<T> getFirstByType(@NotNull Class<T> type) {
        Preconditions.checkNotNull(type, "type");
        return values.values().stream()
            .filter(type::isInstance)
            .map(o -> (T) o)
            .findFirst();
    }

    /**
     * Get all values that match the given type.
     *
     * @param type the expected type class
     * @param <T>  the type
     * @return list of all values matching the type
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> List<T> getAllByType(@NotNull Class<T> type) {
        Preconditions.checkNotNull(type, "type");
        return values.values().stream()
            .filter(type::isInstance)
            .map(o -> (T) o)
            .collect(Collectors.toList());
    }

    /**
     * Check if the context contains no argument values.
     *
     * @return true if no argument values are present
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Check if the context contains argument values.
     *
     * @return true if at least one argument value is present
     */
    public boolean isPresent() {
        return !values.isEmpty();
    }

    /**
     * Get the number of argument values in this context.
     *
     * @return the number of argument values
     */
    public int size() {
        return values.size();
    }

    /**
     * Get all argument names that have values.
     *
     * @return unmodifiable set of argument names
     */
    public @NotNull Set<String> argumentNames() {
        return values.keySet();
    }

    /**
     * Try to retrieve a value using multiple alternative names.
     * Returns the first successfully found value.
     *
     * @param type         the expected type class
     * @param defaultValue the default value if none of the names are found
     * @param names        the argument names to try (in order)
     * @param <T>          the type
     * @return the first found value, or the default value
     */
    @SafeVarargs
    public final @NotNull <T> T getWithFallback(@NotNull Class<T> type, @NotNull T defaultValue,
                                                 @NotNull String... names) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(defaultValue, "defaultValue");
        Preconditions.checkNotNull(names, "names");
        for (String name : names) {
            if (name != null) {
                T value = get(name, type);
                if (value != null) {
                    return value;
                }
            }
        }
        return defaultValue;
    }

    /**
     * Try to retrieve a value using multiple alternative names.
     * Returns the first successfully found value as an Optional.
     *
     * @param type  the expected type class
     * @param names the argument names to try (in order)
     * @param <T>   the type
     * @return Optional containing the first found value, or empty
     */
    @SafeVarargs
    public final @NotNull <T> Optional<T> optionalWithFallback(@NotNull Class<T> type, @NotNull String... names) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(names, "names");
        for (String name : names) {
            if (name != null) {
                T value = get(name, type);
                if (value != null) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Transform a value using a mapper function if present.
     *
     * @param name   the argument name
     * @param type   the expected type class
     * @param mapper the transformation function
     * @param <T>    the input type
     * @param <R>    the result type
     * @return Optional containing the transformed value, or empty
     */
    public <T, R> @NotNull Optional<R> map(@NotNull String name, @NotNull Class<T> type,
                                           @NotNull Function<T, R> mapper) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(mapper, "mapper");
        return optional(name, type).map(mapper);
    }

    /**
     * Execute an action if a value is present.
     *
     * @param name   the argument name
     * @param type   the expected type class
     * @param action the action to execute with the value
     * @param <T>    the type
     */
    public <T> void ifPresent(@NotNull String name, @NotNull Class<T> type,
                              @NotNull java.util.function.Consumer<T> action) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(action, "action");
        optional(name, type).ifPresent(action);
    }

    /**
     * Get a value as String, converting if necessary.
     *
     * @param name the argument name
     * @return the string representation, or null if not present
     */
    public @Nullable String getAsString(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        Object value = values.get(name);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Get a value as Integer, attempting conversion if necessary.
     *
     * @param name the argument name
     * @return the integer value, or null if not present or not convertible
     */
    public @Nullable Integer getAsInt(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        Object value = values.get(name);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get a value as Long, attempting conversion if necessary.
     *
     * @param name the argument name
     * @return the long value, or null if not present or not convertible
     */
    public @Nullable Long getAsLong(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        Object value = values.get(name);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get a value as Double, attempting conversion if necessary.
     *
     * @param name the argument name
     * @return the double value, or null if not present or not convertible
     */
    public @Nullable Double getAsDouble(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        Object value = values.get(name);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get a value as Boolean, attempting conversion if necessary.
     *
     * @param name the argument name
     * @return the boolean value, or null if not present or not convertible
     */
    public @Nullable Boolean getAsBoolean(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        Object value = values.get(name);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            if ("true".equals(str) || "yes".equals(str) || "on".equals(str) || "1".equals(str)) {
                return true;
            }
            if ("false".equals(str) || "no".equals(str) || "off".equals(str) || "0".equals(str)) {
                return false;
            }
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return null;
    }

    /**
     * Check if all specified arguments are present.
     *
     * @param names the argument names to check
     * @return true if all specified arguments have values
     */
    public boolean hasAll(@NotNull String... names) {
        Preconditions.checkNotNull(names, "names");
        for (String name : names) {
            if (name == null || !values.containsKey(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if any of the specified arguments are present.
     *
     * @param names the argument names to check
     * @return true if at least one of the specified arguments has a value
     */
    public boolean hasAny(@NotNull String... names) {
        Preconditions.checkNotNull(names, "names");
        for (String name : names) {
            if (name != null && values.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the total number of all items (arguments + flags + key-values).
     *
     * @return total count of all context items
     */
    public int totalSize() {
        return values.size() + flagValues.size() + keyValuePairs.size() + multiValuePairs.size();
    }

    // ==================== Dependency Validation Helpers ====================

    /**
     * Require all specified arguments to be present.
     * Throws an exception if any are missing.
     *
     * @param names the argument names that must all be present
     * @throws ApiMisuseException if any argument is missing
     */
    public void requireAll(@NotNull String... names) {
        Preconditions.checkNotNull(names, "names");
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (name != null && !values.containsKey(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new ApiMisuseException("Missing required arguments: " + String.join(", ", missing));
        }
    }

    /**
     * Require at least one of the specified arguments to be present.
     * Throws an exception if none are present.
     *
     * @param names the argument names where at least one must be present
     * @throws ApiMisuseException if no argument is present
     */
    public void requireAny(@NotNull String... names) {
        Preconditions.checkNotNull(names, "names");
        if (!hasAny(names)) {
            throw new ApiMisuseException("At least one of these arguments is required: " + String.join(", ", names));
        }
    }

    /**
     * If a trigger argument is present, require all dependent arguments.
     * This is useful for conditional dependencies.
     *
     * @param trigger      the argument that triggers the requirement
     * @param dependencies arguments required when trigger is present
     * @throws ApiMisuseException if trigger is present but dependencies are missing
     */
    public void requireIfPresent(@NotNull String trigger, @NotNull String... dependencies) {
        Preconditions.checkNotNull(trigger, "trigger");
        Preconditions.checkNotNull(dependencies, "dependencies");
        if (values.containsKey(trigger)) {
            List<String> missing = new ArrayList<>();
            for (String dep : dependencies) {
                if (dep != null && !values.containsKey(dep)) {
                    missing.add(dep);
                }
            }
            if (!missing.isEmpty()) {
                throw new ApiMisuseException(
                    "When '" + trigger + "' is specified, these are also required: " + String.join(", ", missing));
            }
        }
    }

    /**
     * Require that at most one of the specified arguments is present.
     * Throws an exception if more than one is present.
     *
     * @param names the mutually exclusive argument names
     * @throws ApiMisuseException if more than one argument is present
     */
    public void requireMutuallyExclusive(@NotNull String... names) {
        Preconditions.checkNotNull(names, "names");
        List<String> present = new ArrayList<>();
        for (String name : names) {
            if (name != null && values.containsKey(name)) {
                present.add(name);
            }
        }
        if (present.size() > 1) {
            throw new ApiMisuseException("Arguments are mutually exclusive: " + String.join(", ", present));
        }
    }

    /**
     * Get all values for the specified argument names that are present.
     * Useful for gathering related optional arguments.
     *
     * @param type  the expected type for all values
     * @param names the argument names to gather
     * @param <T>   the type
     * @return a map of name to value for present arguments
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> Map<String, T> gatherPresent(@NotNull Class<T> type, @NotNull String... names) {
        Preconditions.checkNotNull(type, "type");
        Preconditions.checkNotNull(names, "names");
        Map<String, T> result = new LinkedHashMap<>();
        for (String name : names) {
            if (name != null && values.containsKey(name)) {
                Object value = values.get(name);
                if (type.isInstance(value)) {
                    result.put(name, (T) value);
                }
            }
        }
        return result;
    }

    /**
     * Execute an action if all specified arguments are present.
     *
     * @param action the action to execute with the context
     * @param names  the argument names that must all be present
     * @return true if action was executed, false if arguments were missing
     */
    public boolean ifAllPresent(@NotNull java.util.function.Consumer<CommandContext> action, @NotNull String... names) {
        Preconditions.checkNotNull(action, "action");
        if (hasAll(names)) {
            action.accept(this);
            return true;
        }
        return false;
    }

    /**
     * Execute an action if any of the specified arguments are present.
     *
     * @param action the action to execute with the context
     * @param names  the argument names where at least one must be present
     * @return true if action was executed, false if no arguments were present
     */
    public boolean ifAnyPresent(@NotNull java.util.function.Consumer<CommandContext> action, @NotNull String... names) {
        Preconditions.checkNotNull(action, "action");
        if (hasAny(names)) {
            action.accept(this);
            return true;
        }
        return false;
    }
}
