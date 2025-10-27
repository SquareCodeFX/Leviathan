package de.feelix.leviathan.command.mapping;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

/**
 * Represents the broad kind of a command argument for mapping convenience.
 */
public enum OptionType {
    INT,
    LONG,
    DOUBLE,
    FLOAT,
    STRING,
    UUID,
    BOOLEAN,
    PLAYER,
    CHOICE,
    UNKNOWN;

    /**
     * Best-effort mapping from an ArgumentParser#getTypeName() string to an OptionType.
     */
    public static @NotNull OptionType fromTypeName(@Nullable String typeName) {
        if (typeName == null) return UNKNOWN;
        String t = typeName.trim().toLowerCase();
        return switch (t) {
            case "int" -> INT;
            case "long" -> LONG;
            case "double" -> DOUBLE;
            case "float" -> FLOAT;
            case "string" -> STRING;
            case "uuid" -> UUID;
            case "boolean" -> BOOLEAN;
            case "player" -> PLAYER;
            case "command" -> // treat command choice like generic choice
                CHOICE;
            default ->
                // any custom display type used by choices(..) will be treated as CHOICE
                CHOICE;
        };
    }
}
