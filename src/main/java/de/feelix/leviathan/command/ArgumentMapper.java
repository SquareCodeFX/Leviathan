package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;

import java.util.UUID;

/**
 * Utility methods that can be used as method references with CommandContext#arg,
 * e.g. context.arg("name", ArgumentMapper::getAsString).
 */
public final class ArgumentMapper {
    private ArgumentMapper() {}

    public static @NotNull String getAsString(@NotNull OptionMapping mapping) {
        return mapping.getAsString();
    }

    public static @NotNull Integer getAsInt(@NotNull OptionMapping mapping) {
        return mapping.getAsInt();
    }

    public static @NotNull Long getAsLong(@NotNull OptionMapping mapping) {
        return mapping.getAsLong();
    }

    public static @NotNull UUID getAsUuid(@NotNull OptionMapping mapping) {
        return mapping.getAsUuid();
    }

    public static <T> @NotNull T getAs(@NotNull OptionMapping mapping, @NotNull Class<T> type) {
        return mapping.getAs(type);
    }
}
