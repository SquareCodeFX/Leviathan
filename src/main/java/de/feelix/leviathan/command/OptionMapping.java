package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.exceptions.ApiMisuseException;

import java.util.UUID;

/**
 * Provides strongly-typed access to a single argument value within a {@link CommandContext}.
 */
public interface OptionMapping {
    /**
     * Gets the logical argument name backing this mapping.
     *
     * @return non-null argument name
     */
    @NotNull String name();

    /**
     * Gets the raw stored value for this argument, which may be null when the argument was optional and omitted.
     *
     * @return raw value or null
     */
    @Nullable Object raw();

    /**
     * Gets a broad kind describing this option's type, if known.
     *
     * @return non-null option kind, or {@link OptionType#UNKNOWN}
     */
    @NotNull OptionType optionType();

    /**
     * Returns the value cast to the requested type.
     *
     * @param type expected target class
     * @param <T>  target type parameter
     * @return non-null value of the requested type
     * @throws ApiMisuseException if the argument is missing or the value is not assignable to {@code type}
     */
    <T> @NotNull T getAs(@NotNull Class<T> type);

    /**
     * Convenience getter for String values.
     *
     * @return non-null String value
     * @throws ApiMisuseException if missing or not a String
     */
    @NotNull String getAsString();

    /**
     * Convenience getter for Integer values.
     *
     * @return non-null Integer value
     * @throws ApiMisuseException if missing or not an Integer
     */
    @NotNull Integer getAsInt();

    /**
     * Convenience getter for Long values.
     *
     * @return non-null Long value
     * @throws ApiMisuseException if missing or not a Long
     */
    @NotNull Long getAsLong();

    /**
     * Convenience getter for UUID values.
     *
     * @return non-null UUID value
     * @throws ApiMisuseException if missing or not a UUID
     */
    @NotNull UUID getAsUuid();
}
