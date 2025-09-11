package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.util.UUID;

/**
 * Provides strongly-typed access to a single argument value in a CommandContext.
 */
public interface OptionMapping {
    /** @return the argument name */
    @NotNull String name();

    /** @return the raw stored value (may be null) */
    @Nullable Object raw();

    /** @return a broad kind of this option if known, or UNKNOWN otherwise */
    @NotNull OptionType optionType();

    /** Return the value cast to the requested type or throw if not assignable. */
    <T> @NotNull T getAs(@NotNull Class<T> type);

    /** Convenience typed getters. */
    @NotNull String getAsString();
    @NotNull Integer getAsInt();
    @NotNull Long getAsLong();
    @NotNull UUID getAsUuid();
}
