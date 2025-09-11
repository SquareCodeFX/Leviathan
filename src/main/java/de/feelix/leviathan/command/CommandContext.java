package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.util.Preconditions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Holds parsed argument values and provides type-safe accessors for command actions.
 * <p>
 * The context is immutable and contains:
 * <ul>
 *   <li>A map of argument name to parsed value (only provided arguments are present)</li>
 *   <li>The raw argument array as received from Bukkit</li>
 * </ul>
 */
public final class CommandContext {
    private final Map<String, Object> values;
    private final String[] rawArgs;

    /**
     * Create a new command context.
     * @param values parsed name-to-value mapping
     * @param rawArgs raw argument tokens provided by Bukkit
     */
    public CommandContext(@NotNull Map<String, Object> values, @NotNull String[] rawArgs) {
        this.values = Map.copyOf(Preconditions.checkNotNull(values, "values"));
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
     * @param name argument name
     * @param type expected type class
     * @return Optional of the value if present and assignable to the given type, otherwise empty
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> Optional<T> getOptional(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        Object o = values.get(name);
        if (o == null) return Optional.empty();
        if (!type.isInstance(o)) return Optional.empty();
        return Optional.of((T) o);
    }

    /**
     * Retrieve a typed value by argument name, returning null when missing or of a different type.
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
     * @param name argument name
     * @param type expected type class
     * @return non-null value of the requested type
     * @throws ApiMisuseException if missing or type-incompatible
     */
    @SuppressWarnings("unchecked")
    public @NotNull <T> T getOrThrow(@NotNull String name, @NotNull Class<T> type) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(type, "type");
        if (!values.containsKey(name)) {
            throw new ApiMisuseException("Required argument '" + name + "' is missing in CommandContext");
        }
        Object o = values.get(name);
        if (!type.isInstance(o)) {
            String actual = (o == null) ? "null" : o.getClass().getName();
            throw new ApiMisuseException("Argument '" + name + "' has type " + actual + ", not assignable to " + type.getName());
        }
        return (T) o;
    }

    /**
     * Alias for {@link #getOrThrow(String, Class)} for readability when a required argument is expected.
     */
    public @NotNull <T> T require(@NotNull String name, @NotNull Class<T> type) {
        return getOrThrow(name, type);
    }

    /**
     * Whether a value with the given name exists in the context.
     */
    public boolean has(@NotNull String name) {
        Preconditions.checkNotNull(name, "name");
        return values.containsKey(name);
    }

    /**
     * Functional retrieval using an {@link OptionMapping} and a mapper function.
     * Example usage: {@code String n = ctx.arg("name", ArgumentMapper::getAsString);}.
     * @throws ApiMisuseException if the argument is missing or cannot be converted
     */
    public <T> @NotNull T arg(@NotNull String name, @NotNull Function<OptionMapping, T> mapper) {
        Preconditions.checkNotNull(name, "name");
        Preconditions.checkNotNull(mapper, "mapper");
        return mapper.apply(new MappingImpl(name));
    }

    private final class MappingImpl implements OptionMapping {
        private final String name;
        private MappingImpl(String name) { this.name = name; }

        @Override public @NotNull String name() { return name; }
        @Override public Object raw() { return values.get(name); }
        @Override public @NotNull OptionType optionType() { return inferType(raw()); }

        @Override
        public <T> @NotNull T getAs(@NotNull Class<T> type) {
            Preconditions.checkNotNull(type, "type");
            if (!values.containsKey(name)) {
                throw new ApiMisuseException("Required argument '" + name + "' is missing in CommandContext");
            }
            Object o = values.get(name);
            if (o == null) {
                throw new ApiMisuseException("Argument '" + name + "' is null");
            }
            if (!type.isInstance(o)) {
                String actual = o.getClass().getName();
                throw new ApiMisuseException("Argument '" + name + "' has type " + actual + ", not assignable to " + type.getName());
            }
            @SuppressWarnings("unchecked") T t = (T) o;
            return t;
        }

        @Override public @NotNull String getAsString() { return getAs(String.class); }
        @Override public @NotNull Integer getAsInt() { return getAs(Integer.class); }
        @Override public @NotNull Long getAsLong() { return getAs(Long.class); }
        @Override public @NotNull UUID getAsUuid() { return getAs(UUID.class); }
    }

    private static @NotNull OptionType inferType(Object o) {
        if (o instanceof Integer) return OptionType.INT;
        if (o instanceof Long) return OptionType.LONG;
        if (o instanceof String) return OptionType.STRING;
        if (o instanceof UUID) return OptionType.UUID;
        return (o == null) ? OptionType.UNKNOWN : OptionType.CHOICE;
    }
}
