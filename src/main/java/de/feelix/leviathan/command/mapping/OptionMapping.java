package de.feelix.leviathan.command.mapping;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;

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
     * @return value of the requested type, or null if the argument is not available or not assignable to {@code type}
     */
    <T> @Nullable T as(@NotNull Class<T> type);

    /**
     * Convenience getter for String values.
     *
     * @return String value, or null if the argument is not available or not a String
     */
    @Nullable String asString();

    /**
     * Convenience getter for Integer values.
     *
     * @return Integer value, or null if the argument is not available or not an Integer
     */
    @Nullable Integer asInt();

    /**
     * Convenience getter for Long values.
     *
     * @return Long value, or null if the argument is not available or not a Long
     */
    @Nullable Long asLong();

    /**
     * Convenience getter for UUID values.
     *
     * @return UUID value, or null if the argument is not available or not a UUID
     */
    @Nullable UUID asUuid();
}
