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
                .filter(context::has)
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
                .filter(context::has)
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
            boolean anyPresent = names.stream().anyMatch(context::has);
            boolean allPresent = names.stream().allMatch(context::has);
            if (anyPresent && !allPresent) {
                List<String> missing = names.stream()
                    .filter(name -> !context.has(name))
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
            boolean anyPresent = names.stream().anyMatch(context::has);
            boolean allPresent = names.stream().allMatch(context::has);
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
            boolean anyPresent = names.stream().anyMatch(context::has);
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
            boolean anyPresent = names.stream().anyMatch(context::has);
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
            if (context.has(triggerArgument)) {
                List<String> missing = required.stream()
                    .filter(name -> !context.has(name))
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
            if (context.has(triggerArgument)) {
                boolean allPresent = required.stream().allMatch(context::has);
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
                boolean allPresent = required.stream().allMatch(context::has);
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
            if (context.has(arg1) && context.has(arg2)) {
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

    // ==================== ARGUMENT DEPENDENCY METHODS ====================

    /**
     * Creates a validator that ensures an argument can only be used if a dependency argument is present.
     * <p>
     * Example: "output-file" can only be used if "save" flag is present.
     * <pre>{@code
     * .crossValidate(CrossArgumentValidator.dependsOn("output-file", "save"))
     * // Valid:   /cmd --save --output-file=file.txt
     * // Invalid: /cmd --output-file=file.txt (without --save)
     * }</pre>
     *
     * @param dependentArg the argument that has a dependency
     * @param dependencyArg the argument that must be present for dependentArg to be used
     * @return a dependency validator
     */
    static @NotNull CrossArgumentValidator dependsOn(@NotNull String dependentArg, @NotNull String dependencyArg) {
        if (dependentArg == null || dependencyArg == null) {
            throw new IllegalArgumentException("Both dependent and dependency argument names must be provided");
        }
        return context -> {
            if (context.has(dependentArg) && !context.has(dependencyArg)) {
                return "'" + dependentArg + "' can only be used when '" + dependencyArg + "' is present";
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures an argument can only be used if a dependency argument is present,
     * with a custom error message.
     *
     * @param dependentArg  the argument that has a dependency
     * @param dependencyArg the argument that must be present
     * @param errorMessage  custom error message
     * @return a dependency validator
     */
    static @NotNull CrossArgumentValidator dependsOn(@NotNull String dependentArg, @NotNull String dependencyArg,
                                                      @NotNull String errorMessage) {
        if (dependentArg == null || dependencyArg == null) {
            throw new IllegalArgumentException("Both dependent and dependency argument names must be provided");
        }
        return context -> {
            if (context.has(dependentArg) && !context.has(dependencyArg)) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures an argument can only be used if ALL dependency arguments are present.
     * <p>
     * Example: "advanced-settings" can only be used if both "mode" and "config" are present.
     *
     * @param dependentArg   the argument that has dependencies
     * @param dependencyArgs the arguments that must all be present
     * @return a multi-dependency validator
     */
    static @NotNull CrossArgumentValidator dependsOnAll(@NotNull String dependentArg, @NotNull String... dependencyArgs) {
        if (dependentArg == null || dependencyArgs == null || dependencyArgs.length == 0) {
            throw new IllegalArgumentException("Dependent argument and at least one dependency must be provided");
        }
        final List<String> dependencies = List.of(dependencyArgs);
        return context -> {
            if (context.has(dependentArg)) {
                List<String> missing = dependencies.stream()
                    .filter(name -> !context.has(name))
                    .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    return "'" + dependentArg + "' requires these arguments to be present: "
                           + String.join(", ", missing);
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures an argument can only be used if ANY of the dependency arguments is present.
     * <p>
     * Example: "format" can only be used if either "export" or "save" is present.
     *
     * @param dependentArg   the argument that has dependencies
     * @param dependencyArgs the arguments where at least one must be present
     * @return a multi-dependency validator
     */
    static @NotNull CrossArgumentValidator dependsOnAny(@NotNull String dependentArg, @NotNull String... dependencyArgs) {
        if (dependentArg == null || dependencyArgs == null || dependencyArgs.length == 0) {
            throw new IllegalArgumentException("Dependent argument and at least one dependency must be provided");
        }
        final List<String> dependencies = List.of(dependencyArgs);
        return context -> {
            if (context.has(dependentArg)) {
                boolean anyPresent = dependencies.stream().anyMatch(context::has);
                if (!anyPresent) {
                    return "'" + dependentArg + "' requires at least one of: " + String.join(", ", dependencies);
                }
            }
            return null;
        };
    }

    /**
     * Creates a validator that excludes an argument when another is present.
     * <p>
     * Example: "verbose" cannot be used when "quiet" is present.
     * <pre>{@code
     * .crossValidate(CrossArgumentValidator.excludedBy("verbose", "quiet"))
     * // Valid:   /cmd --verbose
     * // Valid:   /cmd --quiet
     * // Invalid: /cmd --verbose --quiet
     * }</pre>
     *
     * @param excludedArg the argument that cannot be used
     * @param excluderArg the argument that excludes the other
     * @return an exclusion validator
     */
    static @NotNull CrossArgumentValidator excludedBy(@NotNull String excludedArg, @NotNull String excluderArg) {
        if (excludedArg == null || excluderArg == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            if (context.has(excludedArg) && context.has(excluderArg)) {
                return "'" + excludedArg + "' cannot be used when '" + excluderArg + "' is present";
            }
            return null;
        };
    }

    /**
     * Creates a validator that excludes an argument when another is present,
     * with a custom error message.
     *
     * @param excludedArg  the argument that cannot be used
     * @param excluderArg  the argument that excludes the other
     * @param errorMessage custom error message
     * @return an exclusion validator
     */
    static @NotNull CrossArgumentValidator excludedBy(@NotNull String excludedArg, @NotNull String excluderArg,
                                                       @NotNull String errorMessage) {
        if (excludedArg == null || excluderArg == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            if (context.has(excludedArg) && context.has(excluderArg)) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that requires a default value argument unless an override is present.
     * <p>
     * Example: "config" is required unless "use-defaults" is specified.
     *
     * @param requiredArg  the argument that is normally required
     * @param overrideArg  the argument that makes requiredArg optional
     * @return a default override validator
     */
    static @NotNull CrossArgumentValidator requiredUnless(@NotNull String requiredArg, @NotNull String overrideArg) {
        if (requiredArg == null || overrideArg == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            if (!context.has(requiredArg) && !context.has(overrideArg)) {
                return "'" + requiredArg + "' is required unless '" + overrideArg + "' is specified";
            }
            return null;
        };
    }

    /**
     * Creates a validator that requires a default value argument unless an override is present,
     * with a custom error message.
     *
     * @param requiredArg  the argument that is normally required
     * @param overrideArg  the argument that makes requiredArg optional
     * @param errorMessage custom error message
     * @return a default override validator
     */
    static @NotNull CrossArgumentValidator requiredUnless(@NotNull String requiredArg, @NotNull String overrideArg,
                                                           @NotNull String errorMessage) {
        if (requiredArg == null || overrideArg == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            if (!context.has(requiredArg) && !context.has(overrideArg)) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a bidirectional dependency - if either argument is present, both must be present.
     * <p>
     * Example: "username" and "password" must be provided together or not at all.
     *
     * @param arg1 first argument
     * @param arg2 second argument
     * @return a bidirectional dependency validator
     */
    static @NotNull CrossArgumentValidator coDependent(@NotNull String arg1, @NotNull String arg2) {
        if (arg1 == null || arg2 == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            boolean has1 = context.has(arg1);
            boolean has2 = context.has(arg2);
            if (has1 != has2) {
                if (has1) {
                    return "'" + arg1 + "' requires '" + arg2 + "' to also be specified";
                } else {
                    return "'" + arg2 + "' requires '" + arg1 + "' to also be specified";
                }
            }
            return null;
        };
    }

    /**
     * Creates a bidirectional dependency with a custom error message.
     *
     * @param arg1         first argument
     * @param arg2         second argument
     * @param errorMessage custom error message
     * @return a bidirectional dependency validator
     */
    static @NotNull CrossArgumentValidator coDependent(@NotNull String arg1, @NotNull String arg2,
                                                        @NotNull String errorMessage) {
        if (arg1 == null || arg2 == null) {
            throw new IllegalArgumentException("Both argument names must be provided");
        }
        return context -> {
            boolean has1 = context.has(arg1);
            boolean has2 = context.has(arg2);
            if (has1 != has2) {
                return errorMessage;
            }
            return null;
        };
    }

    /**
     * Creates a validator that ensures an argument has a specific value when another argument is present.
     * <p>
     * Example: "level" must be "admin" when "admin-action" is used.
     *
     * @param checkedArg    the argument whose value is checked
     * @param expectedValue the expected value
     * @param triggerArg    the argument that triggers the check
     * @return a value constraint validator
     */
    static @NotNull CrossArgumentValidator valueRequiredWhen(@NotNull String checkedArg, @NotNull Object expectedValue,
                                                              @NotNull String triggerArg) {
        if (checkedArg == null || expectedValue == null || triggerArg == null) {
            throw new IllegalArgumentException("All parameters must be provided");
        }
        return context -> {
            if (context.has(triggerArg) && context.has(checkedArg)) {
                Object actual = context.get(checkedArg, Object.class);
                if (!expectedValue.equals(actual)) {
                    return "'" + checkedArg + "' must be '" + expectedValue + "' when '" + triggerArg + "' is used";
                }
            }
            return null;
        };
    }

    /**
     * Creates a chain of dependencies: A requires B, B requires C, etc.
     * <p>
     * Example: "step3" requires "step2", "step2" requires "step1".
     *
     * @param argumentChain ordered list of arguments where each requires the previous
     * @return a chained dependency validator
     */
    static @NotNull CrossArgumentValidator dependencyChain(@NotNull String... argumentChain) {
        if (argumentChain == null || argumentChain.length < 2) {
            throw new IllegalArgumentException("At least 2 arguments are required for a dependency chain");
        }
        final List<String> chain = List.of(argumentChain);
        return context -> {
            for (int i = chain.size() - 1; i > 0; i--) {
                String dependent = chain.get(i);
                String dependency = chain.get(i - 1);
                if (context.has(dependent) && !context.has(dependency)) {
                    return "'" + dependent + "' requires '" + dependency + "' to be specified first";
                }
            }
            return null;
        };
    }
}
