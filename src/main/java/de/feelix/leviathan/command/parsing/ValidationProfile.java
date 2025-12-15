package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A reusable validation profile that defines a set of validation rules for command parsing.
 * <p>
 * ValidationProfile allows you to define common validation configurations that can be
 * reused across multiple commands or parse operations. This promotes consistency and
 * reduces code duplication.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Define a profile for admin commands
 * ValidationProfile adminProfile = ValidationProfile.builder()
 *     .name("admin")
 *     .requirePermission("admin.commands")
 *     .requirePlayer(false)
 *     .addArgumentRule("target", value -> value != null, "Target cannot be null")
 *     .addArgumentConstraint("amount", value -> (Integer) value > 0, "Amount must be positive")
 *     .build();
 *
 * // Apply the profile during parsing
 * CommandParseResult result = command.parse(sender, label, args,
 *     ParseOptions.builder()
 *         .withValidationProfile(adminProfile)
 *         .build());
 *
 * // Or validate a result against the profile
 * List<CommandParseError> errors = adminProfile.validate(result);
 * }</pre>
 */
public final class ValidationProfile {

    /**
     * Default profile with no additional validation rules.
     */
    public static final ValidationProfile DEFAULT = builder().name("default").build();

    /**
     * Strict profile that requires all arguments to be non-null.
     */
    public static final ValidationProfile STRICT = builder()
        .name("strict")
        .requireAllArgumentsNonNull(true)
        .build();

    private final String name;
    private final @Nullable String requiredPermission;
    private final boolean requirePlayer;
    private final boolean requireAllArgumentsNonNull;
    private final Map<String, List<ArgumentRule>> argumentRules;
    private final List<Predicate<CommandParseResult>> globalRules;
    private final List<String> globalRuleMessages;

    private ValidationProfile(Builder builder) {
        this.name = builder.name;
        this.requiredPermission = builder.requiredPermission;
        this.requirePlayer = builder.requirePlayer;
        this.requireAllArgumentsNonNull = builder.requireAllArgumentsNonNull;
        this.argumentRules = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentRules));
        this.globalRules = Collections.unmodifiableList(new ArrayList<>(builder.globalRules));
        this.globalRuleMessages = Collections.unmodifiableList(new ArrayList<>(builder.globalRuleMessages));
    }

    /**
     * Create a new builder for ValidationProfile.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Get the profile name.
     *
     * @return the profile name
     */
    public @NotNull String name() {
        return name;
    }

    /**
     * Get the required permission for this profile.
     *
     * @return the required permission, or null if none
     */
    public @Nullable String requiredPermission() {
        return requiredPermission;
    }

    /**
     * Check if this profile requires the sender to be a player.
     *
     * @return true if player is required
     */
    public boolean requirePlayer() {
        return requirePlayer;
    }

    /**
     * Check if all arguments must be non-null.
     *
     * @return true if all arguments must be non-null
     */
    public boolean requireAllArgumentsNonNull() {
        return requireAllArgumentsNonNull;
    }

    /**
     * Get all argument rules.
     *
     * @return unmodifiable map of argument name to rules
     */
    public @NotNull Map<String, List<ArgumentRule>> argumentRules() {
        return argumentRules;
    }

    /**
     * Validate a parse result against this profile.
     * <p>
     * Returns a list of errors found during validation. An empty list indicates
     * the result passes all validation rules.
     *
     * @param result the parse result to validate
     * @return list of validation errors (empty if valid)
     */
    public @NotNull List<CommandParseError> validate(@NotNull CommandParseResult result) {
        Preconditions.checkNotNull(result, "result");

        List<CommandParseError> errors = new ArrayList<>();

        // Skip validation if the result already failed
        if (result.isFailure()) {
            return errors;
        }

        var context = result.context();
        if (context == null) {
            return errors;
        }

        // Validate non-null requirements
        if (requireAllArgumentsNonNull) {
            for (Map.Entry<String, Object> entry : context.allArguments().entrySet()) {
                if (entry.getValue() == null) {
                    errors.add(CommandParseError.validation(entry.getKey(), "Argument cannot be null"));
                }
            }
        }

        // Validate argument-specific rules
        for (Map.Entry<String, List<ArgumentRule>> entry : argumentRules.entrySet()) {
            String argName = entry.getKey();
            Object value = context.argument(argName);

            for (ArgumentRule rule : entry.getValue()) {
                try {
                    if (!rule.test(value)) {
                        errors.add(CommandParseError.validation(argName, rule.errorMessage()));
                    }
                } catch (Exception e) {
                    errors.add(CommandParseError.validation(argName, "Validation failed: " + e.getMessage()));
                }
            }
        }

        // Validate global rules
        for (int i = 0; i < globalRules.size(); i++) {
            try {
                if (!globalRules.get(i).test(result)) {
                    errors.add(CommandParseError.crossValidation(globalRuleMessages.get(i)));
                }
            } catch (Exception e) {
                errors.add(CommandParseError.internal("Global validation failed: " + e.getMessage()));
            }
        }

        return errors;
    }

    /**
     * Check if a parse result is valid according to this profile.
     *
     * @param result the parse result to check
     * @return true if the result passes all validation rules
     */
    public boolean isValid(@NotNull CommandParseResult result) {
        return validate(result).isEmpty();
    }

    /**
     * Apply this profile's validation to a result and return an updated result.
     * <p>
     * If validation fails, returns a new failure result with the validation errors
     * added to any existing errors.
     *
     * @param result the original parse result
     * @return the result with validation errors applied
     */
    public @NotNull CommandParseResult applyTo(@NotNull CommandParseResult result) {
        Preconditions.checkNotNull(result, "result");

        if (result.isFailure()) {
            return result;
        }

        List<CommandParseError> validationErrors = validate(result);
        if (validationErrors.isEmpty()) {
            return result;
        }

        return CommandParseResult.failureWithMetrics(validationErrors, result.rawArgs(), result.metrics());
    }

    @Override
    public String toString() {
        return "ValidationProfile{" +
               "name='" + name + '\'' +
               ", requiredPermission='" + requiredPermission + '\'' +
               ", requirePlayer=" + requirePlayer +
               ", requireAllArgumentsNonNull=" + requireAllArgumentsNonNull +
               ", argumentRules=" + argumentRules.size() +
               ", globalRules=" + globalRules.size() +
               '}';
    }

    /**
     * A validation rule for a specific argument.
     */
    public static final class ArgumentRule {
        private final Predicate<Object> predicate;
        private final String errorMessage;

        private ArgumentRule(Predicate<Object> predicate, String errorMessage) {
            this.predicate = predicate;
            this.errorMessage = errorMessage;
        }

        /**
         * Create a new argument rule.
         *
         * @param predicate    the validation predicate
         * @param errorMessage the error message if validation fails
         * @return a new argument rule
         */
        public static @NotNull ArgumentRule of(@NotNull Predicate<Object> predicate, @NotNull String errorMessage) {
            Preconditions.checkNotNull(predicate, "predicate");
            Preconditions.checkNotNull(errorMessage, "errorMessage");
            return new ArgumentRule(predicate, errorMessage);
        }

        /**
         * Test if the value passes this rule.
         *
         * @param value the value to test
         * @return true if the value passes
         */
        public boolean test(Object value) {
            return predicate.test(value);
        }

        /**
         * Get the error message for this rule.
         *
         * @return the error message
         */
        public @NotNull String errorMessage() {
            return errorMessage;
        }
    }

    /**
     * Builder for ValidationProfile.
     */
    public static final class Builder {
        private String name = "unnamed";
        private @Nullable String requiredPermission = null;
        private boolean requirePlayer = false;
        private boolean requireAllArgumentsNonNull = false;
        private final Map<String, List<ArgumentRule>> argumentRules = new LinkedHashMap<>();
        private final List<Predicate<CommandParseResult>> globalRules = new ArrayList<>();
        private final List<String> globalRuleMessages = new ArrayList<>();

        private Builder() {}

        /**
         * Set the profile name.
         *
         * @param name the profile name
         * @return this builder
         */
        public @NotNull Builder name(@NotNull String name) {
            Preconditions.checkNotNull(name, "name");
            this.name = name;
            return this;
        }

        /**
         * Set the required permission.
         *
         * @param permission the required permission
         * @return this builder
         */
        public @NotNull Builder requirePermission(@NotNull String permission) {
            Preconditions.checkNotNull(permission, "permission");
            this.requiredPermission = permission;
            return this;
        }

        /**
         * Set whether a player sender is required.
         *
         * @param require true if player is required
         * @return this builder
         */
        public @NotNull Builder requirePlayer(boolean require) {
            this.requirePlayer = require;
            return this;
        }

        /**
         * Set whether all arguments must be non-null.
         *
         * @param require true if all arguments must be non-null
         * @return this builder
         */
        public @NotNull Builder requireAllArgumentsNonNull(boolean require) {
            this.requireAllArgumentsNonNull = require;
            return this;
        }

        /**
         * Add a validation rule for a specific argument.
         *
         * @param argumentName the argument name
         * @param predicate    the validation predicate
         * @param errorMessage the error message if validation fails
         * @return this builder
         */
        public @NotNull Builder addArgumentRule(@NotNull String argumentName,
                                                 @NotNull Predicate<Object> predicate,
                                                 @NotNull String errorMessage) {
            Preconditions.checkNotNull(argumentName, "argumentName");
            Preconditions.checkNotNull(predicate, "predicate");
            Preconditions.checkNotNull(errorMessage, "errorMessage");

            argumentRules.computeIfAbsent(argumentName, k -> new ArrayList<>())
                .add(ArgumentRule.of(predicate, errorMessage));
            return this;
        }

        /**
         * Add a non-null constraint for a specific argument.
         *
         * @param argumentName the argument name
         * @return this builder
         */
        public @NotNull Builder requireArgumentNonNull(@NotNull String argumentName) {
            return addArgumentRule(argumentName, Objects::nonNull, "Argument '" + argumentName + "' cannot be null");
        }

        /**
         * Add a constraint that an argument must be a positive number.
         *
         * @param argumentName the argument name
         * @return this builder
         */
        public @NotNull Builder requirePositive(@NotNull String argumentName) {
            return addArgumentRule(argumentName,
                value -> value instanceof Number && ((Number) value).doubleValue() > 0,
                "Argument '" + argumentName + "' must be positive");
        }

        /**
         * Add a constraint that an argument must be non-negative.
         *
         * @param argumentName the argument name
         * @return this builder
         */
        public @NotNull Builder requireNonNegative(@NotNull String argumentName) {
            return addArgumentRule(argumentName,
                value -> value instanceof Number && ((Number) value).doubleValue() >= 0,
                "Argument '" + argumentName + "' must be non-negative");
        }

        /**
         * Add a constraint that an argument must be within a range.
         *
         * @param argumentName the argument name
         * @param min          the minimum value (inclusive)
         * @param max          the maximum value (inclusive)
         * @return this builder
         */
        public @NotNull Builder requireInRange(@NotNull String argumentName, double min, double max) {
            return addArgumentRule(argumentName,
                value -> value instanceof Number &&
                         ((Number) value).doubleValue() >= min &&
                         ((Number) value).doubleValue() <= max,
                "Argument '" + argumentName + "' must be between " + min + " and " + max);
        }

        /**
         * Add a constraint that a string argument must match a pattern.
         *
         * @param argumentName the argument name
         * @param pattern      the regex pattern
         * @return this builder
         */
        public @NotNull Builder requireMatches(@NotNull String argumentName, @NotNull String pattern) {
            Preconditions.checkNotNull(pattern, "pattern");
            return addArgumentRule(argumentName,
                value -> value instanceof String && ((String) value).matches(pattern),
                "Argument '" + argumentName + "' must match pattern: " + pattern);
        }

        /**
         * Add a constraint that a string argument must not be blank.
         *
         * @param argumentName the argument name
         * @return this builder
         */
        public @NotNull Builder requireNotBlank(@NotNull String argumentName) {
            return addArgumentRule(argumentName,
                value -> value instanceof String && !((String) value).trim().isEmpty(),
                "Argument '" + argumentName + "' cannot be blank");
        }

        /**
         * Add a global validation rule that checks the entire result.
         *
         * @param predicate    the validation predicate
         * @param errorMessage the error message if validation fails
         * @return this builder
         */
        public @NotNull Builder addGlobalRule(@NotNull Predicate<CommandParseResult> predicate,
                                               @NotNull String errorMessage) {
            Preconditions.checkNotNull(predicate, "predicate");
            Preconditions.checkNotNull(errorMessage, "errorMessage");
            globalRules.add(predicate);
            globalRuleMessages.add(errorMessage);
            return this;
        }

        /**
         * Build the ValidationProfile.
         *
         * @return the built profile
         */
        public @NotNull ValidationProfile build() {
            return new ValidationProfile(this);
        }
    }
}
