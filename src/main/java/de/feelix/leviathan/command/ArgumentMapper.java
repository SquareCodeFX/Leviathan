package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;

import java.util.UUID;

/**
 * Utility methods that can be used as method references with {@link CommandContext#arg(String, java.util.function.Function)},
 * for example: {@code context.arg("name", ArgumentMapper::getAsString)}.
 */
public final class ArgumentMapper {
    private ArgumentMapper() {}

    /**
     * Retrieve the value as a non-null String via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return non-null string value
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if the underlying value is missing or not a String
     */
    public static @NotNull String getAsString(@NotNull OptionMapping mapping) {
        return mapping.getAsString();
    }

    /**
     * Retrieve the value as a non-null Integer via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return non-null integer value
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if the underlying value is missing or not an Integer
     */
    public static @NotNull Integer getAsInt(@NotNull OptionMapping mapping) {
        return mapping.getAsInt();
    }

    /**
     * Retrieve the value as a non-null Long via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return non-null long value
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if the underlying value is missing or not a Long
     */
    public static @NotNull Long getAsLong(@NotNull OptionMapping mapping) {
        return mapping.getAsLong();
    }

    /**
     * Retrieve the value as a non-null UUID via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return non-null UUID value
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if the underlying value is missing or not a UUID
     */
    public static @NotNull UUID getAsUuid(@NotNull OptionMapping mapping) {
        return mapping.getAsUuid();
    }

    /**
     * Retrieve the value as the requested type via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @param type    expected type class
     * @param <T>     target value type
     * @return non-null value of the requested type
     * @throws de.feelix.leviathan.exceptions.ApiMisuseException if the underlying value is missing or not assignable to {@code type}
     */
    public static <T> @NotNull T getAs(@NotNull OptionMapping mapping, @NotNull Class<T> type) {
        return mapping.getAs(type);
    }
}
