package de.feelix.leviathan.parser;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Parses a single argument token and optionally provides tab-completion suggestions.
 * <p>
 * Contract:
 * <ul>
 *   <li>Implementations must be stateless and thread-safe.</li>
 *   <li>{@link #parse(String, CommandSender)} must never return null.</li>
 *   <li>{@link #complete(String, CommandSender)} must return a non-null list (possibly empty).</li>
 *   <li>{@link #getTypeName()} should return a short human-readable name used in error messages.</li>
 * </ul>
 *
 * @param <T> parsed value type
 */
public interface ArgumentParser<T> {
    /**
     * @return a short human-readable type name used in error messages (e.g., "int", "uuid").
     */
    String getTypeName();

    /**
     * Attempt to parse the given raw input token into the target type.
     *
     * @param input  The raw token for this argument position.
     * @param sender The command sender (for context such as permissions, world, etc.).
     * @return ParseResult with either a value or an error message.
     */
    ParseResult<T> parse(String input, CommandSender sender);

    /**
     * Provide tab-completion suggestions for the current partial token.
     *
     * @param input  Current partial token (may be empty string if the user has not typed anything yet).
     * @param sender The command sender (for dynamic completions).
     * @return List of suggestion strings. Never null.
     */
    List<String> complete(String input, CommandSender sender);
}
