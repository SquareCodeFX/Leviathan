package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.ApiMisuseException;

import java.util.Map;
import java.util.Optional;

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
        this.values = Map.copyOf(values);
        this.rawArgs = rawArgs.clone();
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
    public @NotNull <T> Optional<T> getOptional(String name, Class<T> type) {
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
    public @Nullable <T> T get(String name, Class<T> type) {
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
    public @NotNull <T> T getOrThrow(String name, Class<T> type) {
        if (!values.containsKey(name)) {
            throw new ApiMisuseException("Required argument '" + name + "' is missing in CommandContext");
        }
        Object o = values.get(name);
        if (o == null || !type.isInstance(o)) {
            String actual = (o == null) ? "null" : o.getClass().getName();
            throw new ApiMisuseException("Argument '" + name + "' has type " + actual + ", not assignable to " + type.getName());
        }
        return (T) o;
    }

    /**
     * Alias for {@link #getOrThrow(String, Class)} for readability when a required argument is expected.
     */
    public @NotNull <T> T require(String name, Class<T> type) {
        return getOrThrow(name, type);
    }

    /**
     * Whether a value with the given name exists in the context.
     */
    public boolean has(String name) {
        return values.containsKey(name);
    }
}
