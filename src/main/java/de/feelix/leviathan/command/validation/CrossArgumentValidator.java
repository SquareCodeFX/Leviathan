package de.feelix.leviathan.command.validation;


import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.annotations.NotNull;

import de.feelix.leviathan.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Cross-argument validator for validating relationships between multiple arguments.
 * <p>
 * Invoked after all arguments have been parsed and individually validated.
 * This allows validation of complex relationships between arguments, such as ensuring
 * a "start date" is before an "end date", or that mutually exclusive options aren't
 * both provided.
 * <p>
 * Factory methods are provided for common validation patterns:
 * <ul>
 *   <li>{@link #mutuallyExclusive(String...)} - Ensures only one of the specified arguments is provided</li>
 *   <li>{@link #requiresAll(String...)} - Ensures all specified arguments are provided together</li>
 *   <li>{@link #requiresAny(String...)} - Ensures at least one of the specified arguments is provided</li>
 *   <li>{@link #requiresIfPresent(String, String...)} - If argument A is present, require other arguments</li>
 *   <li>{@link #conditionalRequires(Predicate, String, String...)} - Conditional requirement based on predicate</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand.create("transfer")
 *     .argument("amount", Parsers.integer())
 *     .argument("player", Parsers.onlinePlayer())
 *     .argument("all", Parsers.flag())
 *     .crossValidate(CrossArgumentValidator.mutuallyExclusive("amount", "all"))
 *     .crossValidate(CrossArgumentValidator.requiresAny("amount", "all"))
 *     .build();
 * }</pre>
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

    // ==================== Factory Methods ====================

    /**
     * Creates a validator that ensures at most one of the specified arguments is provided.
     * Useful for mutually exclusive options.
     *
     * @param argumentNames the names of arguments that cannot be provided together
     * @return a validator for mutual exclusivity
     * @throws IllegalArgumentException if fewer than 2 argument names are provided
     */
    static @NotNull CrossArgumentValidator mutuallyExclusive(@NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for mutually exclusive validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            List<String> provided = names.stream()
                .filter(context::isPresent)
                .collect(Collectors.toList());
            if (provided.size() > 1) {
                return "Arguments are mutually exclusive: " + String.join(", ", provided);
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures at most one of the specified arguments is provided,
     * with a custom error message.
     *
     * @param errorMessage  custom error message to display
     * @param argumentNames the names of arguments that cannot be provided together
     * @return a validator for mutual exclusivity
     */
    static @NotNull CrossArgumentValidator mutuallyExclusive(@NotNull String errorMessage,
                                                              @NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for mutually exclusive validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            long providedCount = names.stream()
                .filter(context::isPresent)
                .count();
            if (providedCount > 1) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures all specified arguments are provided together.
     * If any of the arguments is present, all must be present.
     *
     * @param argumentNames the names of arguments that must all be present together
     * @return a validator for requiring all arguments
     * @throws IllegalArgumentException if fewer than 2 argument names are provided
     */
    static @NotNull CrossArgumentValidator requiresAll(@NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for requiresAll validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            boolean anyPresent = names.stream().anyMatch(context::isPresent);
            boolean allPresent = names.stream().allMatch(context::isPresent);
            if (anyPresent && !allPresent) {
                List<String> missing = names.stream()
                    .filter(name -> !context.isPresent(name))
                    .collect(Collectors.toList());
                return "Missing required arguments: " + String.join(", ", missing);
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures all specified arguments are provided together,
     * with a custom error message.
     *
     * @param errorMessage  custom error message to display
     * @param argumentNames the names of arguments that must all be present together
     * @return a validator for requiring all arguments
     */
    static @NotNull CrossArgumentValidator requiresAll(@NotNull String errorMessage,
                                                        @NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for requiresAll validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            boolean anyPresent = names.stream().anyMatch(context::isPresent);
            boolean allPresent = names.stream().allMatch(context::isPresent);
            if (anyPresent && !allPresent) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures at least one of the specified arguments is provided.
     *
     * @param argumentNames the names of arguments where at least one must be present
     * @return a validator for requiring at least one argument
     * @throws IllegalArgumentException if fewer than 2 argument names are provided
     */
    static @NotNull CrossArgumentValidator requiresAny(@NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for requiresAny validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            boolean anyPresent = names.stream().anyMatch(context::isPresent);
            if (!anyPresent) {
                return "At least one of these arguments is required: " + String.join(", ", names);
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures at least one of the specified arguments is provided,
     * with a custom error message.
     *
     * @param errorMessage  custom error message to display
     * @param argumentNames the names of arguments where at least one must be present
     * @return a validator for requiring at least one argument
     */
    static @NotNull CrossArgumentValidator requiresAny(@NotNull String errorMessage,
                                                        @NotNull String... argumentNames) {
        if (argumentNames == null || argumentNames.length < 2) {
            throw new IllegalArgumentException("At least 2 argument names are required for requiresAny validation");
        }
        final List<String> names = List.of(argumentNames);
        return context -> {
            boolean anyPresent = names.stream().anyMatch(context::isPresent);
            if (!anyPresent) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that requires certain arguments if a trigger argument is present.
     * Useful for conditional requirements like "if --output is specified, --format is required".
     *
     * @param triggerArgument the argument that triggers the requirement
     * @param requiredArgs    arguments that become required when trigger is present
     * @return a validator for conditional requirements
     */
    static @NotNull CrossArgumentValidator requiresIfPresent(@NotNull String triggerArgument,
                                                              @NotNull String... requiredArgs) {
        if (triggerArgument == null || requiredArgs == null || requiredArgs.length == 0) {
            throw new IllegalArgumentException("Trigger argument and at least one required argument must be provided");
        }
        final List<String> required = List.of(requiredArgs);
        return context -> {
            if (context.isPresent(triggerArgument)) {
                List<String> missing = required.stream()
                    .filter(name -> !context.isPresent(name))
                    .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    return "When '" + triggerArgument + "' is specified, the following are required: "
                           + String.join(", ", missing);
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator that requires certain arguments if a trigger argument is present,
     * with a custom error message.
     *
     * @param triggerArgument the argument that triggers the requirement
     * @param errorMessage    custom error message to display
     * @param requiredArgs    arguments that become required when trigger is present
     * @return a validator for conditional requirements
     */
    static @NotNull CrossArgumentValidator requiresIfPresent(@NotNull String triggerArgument,
                                                              @NotNull String errorMessage,
                                                              @NotNull String... requiredArgs) {
        if (triggerArgument == null || requiredArgs == null || requiredArgs.length == 0) {
            throw new IllegalArgumentException("Trigger argument and at least one required argument must be provided");
        }
        final List<String> required = List.of(requiredArgs);
        return context -> {
            if (context.isPresent(triggerArgument)) {
                boolean allPresent = required.stream().allMatch(context::isPresent);
                if (!allPresent) {
                    return errorMessage;
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator based on a custom predicate condition.
     * If the condition is true, the specified arguments are required.
     *
     * @param condition    predicate that determines if validation should be applied
     * @param errorMessage error message to display if validation fails
     * @param requiredArgs arguments required when condition is true
     * @return a conditional validator
     */
    static @NotNull CrossArgumentValidator conditionalRequires(
        @NotNull Predicate<CommandContext> condition,
        @NotNull String errorMessage,
        @NotNull String... requiredArgs) {
        if (condition == null || requiredArgs == null || requiredArgs.length == 0) {
            throw new IllegalArgumentException("Condition and at least one required argument must be provided");
        }
        final List<String> required = List.of(requiredArgs);
        return context -> {
            if (condition.test(context)) {
                boolean allPresent = required.stream().allMatch(context::isPresent);
                if (!allPresent) {
                    return errorMessage;
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator that compares two argument values.
     * Useful for range validations like "min must be less than max".
     *
     * @param <T>          the type of the arguments being compared
     * @param arg1         first argument name
     * @param arg2         second argument name
     * @param comparison   predicate comparing the two values (returns true if valid)
     * @param errorMessage error message to display if validation fails
     * @param type         the expected type of both arguments
     * @return a comparison validator
     */
    static <T> @NotNull CrossArgumentValidator comparing(
        @NotNull String arg1,
        @NotNull String arg2,
        @NotNull BiPredicate<T, T> comparison,
        @NotNull String errorMessage,
        @NotNull Class<T> type) {
        return context -> {
            if (context.isPresent(arg1) && context.isPresent(arg2)) {
                T val1 = context.get(arg1, type);
                T val2 = context.get(arg2, type);
                if (val1 != null && val2 != null && !comparison.test(val1, val2)) {
                    return errorMessage;
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator for numeric range arguments ensuring min is less than or equal to max.
     *
     * @param minArg       name of the minimum value argument
     * @param maxArg       name of the maximum value argument
     * @param errorMessage error message to display if min > max
     * @return a range validator
     */
    static @NotNull CrossArgumentValidator range(@NotNull String minArg, @NotNull String maxArg,
                                                  @NotNull String errorMessage) {
        return comparing(minArg, maxArg,
            (Number min, Number max) -> min.doubleValue() <= max.doubleValue(),
            errorMessage, Number.class);
    }

    /**
     * Creates a validator for numeric range arguments ensuring min is less than or equal to max.
     * Uses a default error message.
     *
     * @param minArg name of the minimum value argument
     * @param maxArg name of the maximum value argument
     * @return a range validator
     */
    static @NotNull CrossArgumentValidator range(@NotNull String minArg, @NotNull String maxArg) {
        return range(minArg, maxArg, "'" + minArg + "' must be less than or equal to '" + maxArg + "'");
    }

    /**
     * Combines multiple validators into one. All validators must pass.
     *
     * @param validators the validators to combine
     * @return a combined validator that runs all validators
     */
    static @NotNull CrossArgumentValidator all(@NotNull CrossArgumentValidator... validators) {
        if (validators == null || validators.length == 0) {
            return context -> null;
        }
        final List<CrossArgumentValidator> list = List.of(validators);
        return context -> {
            for (CrossArgumentValidator validator : list) {
                String error = validator.validate(context);
                if (error != null) {
                    return error;
                }
            }
            return null;
        };
    }
}
