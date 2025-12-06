package de.feelix.leviathan.command.validation;


import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.annotations.NotNull;

import de.feelix.leviathan.annotations.Nullable;

/**
 * Cross-argument validator for validating relationships between multiple arguments.
 * <p>
 * Invoked after all arguments have been parsed and individually validated.
 * This allows validation of complex relationships between arguments, such as ensuring
 * a "start date" is before an "end date", or that mutually exclusive options aren't
 * both provided.
 */
@FunctionalInterface
public interface CrossArgumentValidator {
    /**
     * Validates the parsed argument values.
     *
     * @param context the command context containing all parsed argument values
     * @return null if valid, or an error message string if invalid
     */
    @Nullable
    String validate(@NotNull CommandContext context);
}
