package de.feelix.leviathan.command.argument;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.command.mapping.OptionMapping;
import de.feelix.leviathan.util.Preconditions;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Utility methods that can be used as method references with {@link CommandContext#arg(String, java.util.function.Function)},
 * for example: {@code context.arg("name", ArgumentMapper::asString)}.
 */
public final class ArgumentMapper {
    private ArgumentMapper() {}

    /**
     * Retrieve the value as a String via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return string value, or null if the underlying value is missing or not a String
     */
    public static @Nullable String asString(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.asString();
    }

    /**
     * Retrieve the value as an Integer via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return integer value, or null if the underlying value is missing or not an Integer
     */
    public static @Nullable Integer asInt(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.asInt();
    }

    /**
     * Retrieve the value as a Long via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return long value, or null if the underlying value is missing or not a Long
     */
    public static @Nullable Long asLong(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.asLong();
    }

    /**
     * Retrieve the value as a UUID via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return UUID value, or null if the underlying value is missing or not a UUID
     */
    public static @Nullable UUID asUuid(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.asUuid();
    }

    /**
     * Retrieve the value as a Double via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return double value, or null if the underlying value is missing or not a Double
     */
    public static @Nullable Double asDouble(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(Double.class);
    }

    /**
     * Retrieve the value as a Float via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return float value, or null if the underlying value is missing or not a Float
     */
    public static @Nullable Float asFloat(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(Float.class);
    }

    /**
     * Retrieve the value as a Boolean via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return boolean value, or null if the underlying value is missing or not a Boolean
     */
    public static @Nullable Boolean asBoolean(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(Boolean.class);
    }

    /**
     * Retrieve the value as a Player via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return player value, or null if the underlying value is missing or not a Player
     */
    public static @Nullable Player asPlayer(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(Player.class);
    }

    /**
     * Retrieve the value as an OfflinePlayer via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return offline player value, or null if the underlying value is missing or not an OfflinePlayer
     */
    public static @Nullable OfflinePlayer asOfflinePlayer(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(OfflinePlayer.class);
    }

    /**
     * Retrieve the value as a World via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return world value, or null if the underlying value is missing or not a World
     */
    public static @Nullable World asWorld(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(World.class);
    }

    /**
     * Retrieve the value as a Material via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @return material value, or null if the underlying value is missing or not a Material
     */
    public static @Nullable Material asMaterial(@NotNull OptionMapping mapping) {
        Preconditions.checkNotNull(mapping, "mapping");
        return mapping.as(Material.class);
    }

    /**
     * Retrieve the value as an enum constant via the provided mapping.
     * Provides type-safe enum casting for command arguments parsed with enumParser.
     *
     * @param mapping   the option mapping supplied by the command context
     * @param enumClass the enum class
     * @param <E>       enum type
     * @return enum value, or null if the underlying value is missing or not of the specified enum type
     */
    public static <E extends Enum<E>> @Nullable E asEnum(@NotNull OptionMapping mapping, @NotNull Class<E> enumClass) {
        Preconditions.checkNotNull(mapping, "mapping");
        Preconditions.checkNotNull(enumClass, "enumClass");
        return mapping.as(enumClass);
    }

    /**
     * Retrieve the value as the requested type via the provided mapping.
     *
     * @param mapping the option mapping supplied by the command context
     * @param type    expected type class
     * @param <T>     target value type
     * @return value of the requested type, or null if the underlying value is missing or not assignable to {@code type}
     */
    public static <T> @Nullable T as(@NotNull OptionMapping mapping, @NotNull Class<T> type) {
        Preconditions.checkNotNull(mapping, "mapping");
        Preconditions.checkNotNull(type, "type");
        return mapping.as(type);
    }
}
