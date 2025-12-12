package de.feelix.leviathan.command.validation;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.core.CommandContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.function.Predicate;

/**
 * Aggregates multiple validation errors instead of failing on the first error.
 * <p>
 * This allows users to see ALL validation problems at once, improving UX
 * by not forcing them to fix one issue at a time.
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand.create("register")
 *     .argString("username")
 *     .argString("email")
 *     .argInt("age")
 *     .executes((sender, ctx) -> {
 *         ValidationAggregator validator = ValidationAggregator.create()
 *             .validate("username", ctx.get("username", String.class),
 *                 v -> v != null && v.length() >= 3,
 *                 "Username must be at least 3 characters")
 *             .validate("email", ctx.get("email", String.class),
 *                 v -> v != null && v.contains("@"),
 *                 "Invalid email format")
 *             .validate("age", ctx.get("age", Integer.class),
 *                 v -> v != null && v >= 18,
 *                 "Must be 18 or older");
 *
 *         if (!validator.isValid()) {
 *             validator.sendErrors(sender);
 *             return;
 *         }
 *
 *         // All validations passed
 *         // ...
 *     })
 *     .build();
 * }</pre>
 */
public final class ValidationAggregator {

    private final List<ValidationError> errors = new ArrayList<>();
    private final Map<String, Object> validatedValues = new LinkedHashMap<>();

    /**
     * Represents a single validation error.
     */
    public static final class ValidationError {
        private final String fieldName;
        private final String message;
        private final @Nullable Object invalidValue;

        ValidationError(@NotNull String fieldName, @NotNull String message, @Nullable Object invalidValue) {
            this.fieldName = fieldName;
            this.message = message;
            this.invalidValue = invalidValue;
        }

        /**
         * @return the name of the field that failed validation
         */
        public @NotNull String fieldName() {
            return fieldName;
        }

        /**
         * @return the error message
         */
        public @NotNull String message() {
            return message;
        }

        /**
         * @return the value that failed validation, or null
         */
        public @Nullable Object invalidValue() {
            return invalidValue;
        }

        /**
         * @return formatted error string "fieldName: message"
         */
        public @NotNull String format() {
            return fieldName + ": " + message;
        }

        /**
         * @return formatted error string with color codes
         */
        public @NotNull String formatColored() {
            return "§c" + fieldName + "§7: §f" + message;
        }

        @Override
        public String toString() {
            return format();
        }
    }

    /**
     * The result of aggregated validation.
     */
    public static final class ValidationResult {
        private final List<ValidationError> errors;
        private final Map<String, Object> values;

        ValidationResult(@NotNull List<ValidationError> errors, @NotNull Map<String, Object> values) {
            this.errors = List.copyOf(errors);
            this.values = Map.copyOf(values);
        }

        /**
         * @return true if validation passed (no errors)
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * @return true if validation failed (has errors)
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * @return the number of validation errors
         */
        public int errorCount() {
            return errors.size();
        }

        /**
         * @return unmodifiable list of all errors
         */
        public @NotNull List<ValidationError> errors() {
            return errors;
        }

        /**
         * @return unmodifiable map of successfully validated values
         */
        public @NotNull Map<String, Object> validatedValues() {
            return values;
        }

        /**
         * Get all error messages as a list of strings.
         *
         * @return list of formatted error messages
         */
        public @NotNull List<String> errorMessages() {
            List<String> messages = new ArrayList<>();
            for (ValidationError e : errors) {
                messages.add(e.format());
            }
            return messages;
        }

        /**
         * Get all error messages as a list of colored strings.
         *
         * @return list of colored error messages
         */
        public @NotNull List<String> errorMessagesColored() {
            List<String> messages = new ArrayList<>();
            for (ValidationError e : errors) {
                messages.add(e.formatColored());
            }
            return messages;
        }

        /**
         * Get errors for a specific field.
         *
         * @param fieldName the field name
         * @return list of errors for that field
         */
        public @NotNull List<ValidationError> errorsFor(@NotNull String fieldName) {
            Preconditions.checkNotNull(fieldName, "fieldName");
            List<ValidationError> fieldErrors = new ArrayList<>();
            for (ValidationError e : errors) {
                if (e.fieldName().equals(fieldName)) {
                    fieldErrors.add(e);
                }
            }
            return fieldErrors;
        }

        /**
         * Check if a specific field has errors.
         *
         * @param fieldName the field name
         * @return true if the field has validation errors
         */
        public boolean hasErrorsFor(@NotNull String fieldName) {
            return !errorsFor(fieldName).isEmpty();
        }
    }

    private ValidationAggregator() {
    }

    /**
     * Create a new validation aggregator.
     *
     * @return a new ValidationAggregator instance
     */
    public static @NotNull ValidationAggregator create() {
        return new ValidationAggregator();
    }

    /**
     * Create a validation aggregator from a CommandContext.
     * Pre-populates with the context's argument names and values.
     *
     * @param context the command context
     * @return a new ValidationAggregator with context values
     */
    public static @NotNull ValidationAggregator fromContext(@NotNull CommandContext context) {
        Preconditions.checkNotNull(context, "context");
        ValidationAggregator aggregator = new ValidationAggregator();
        aggregator.validatedValues.putAll(context.getAll());
        return aggregator;
    }

    /**
     * Validate a value with a predicate.
     *
     * @param fieldName    the name of the field being validated
     * @param value        the value to validate
     * @param predicate    validation predicate (returns true if valid)
     * @param errorMessage error message if validation fails
     * @param <T>          value type
     * @return this aggregator for chaining
     */
    public <T> @NotNull ValidationAggregator validate(
        @NotNull String fieldName,
        @Nullable T value,
        @NotNull Predicate<T> predicate,
        @NotNull String errorMessage) {
        Preconditions.checkNotNull(fieldName, "fieldName");
        Preconditions.checkNotNull(predicate, "predicate");
        Preconditions.checkNotNull(errorMessage, "errorMessage");

        try {
            if (!predicate.test(value)) {
                errors.add(new ValidationError(fieldName, errorMessage, value));
            } else {
                validatedValues.put(fieldName, value);
            }
        } catch (Exception e) {
            errors.add(new ValidationError(fieldName, errorMessage + " (validation error: " + e.getMessage() + ")", value));
        }
        return this;
    }

    /**
     * Validate that a value is not null.
     *
     * @param fieldName the field name
     * @param value     the value to check
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireNotNull(@NotNull String fieldName, @Nullable Object value) {
        return validate(fieldName, value, Objects::nonNull, "is required");
    }

    /**
     * Validate that a value is not null with a custom message.
     *
     * @param fieldName    the field name
     * @param value        the value to check
     * @param errorMessage custom error message
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireNotNull(@NotNull String fieldName, @Nullable Object value,
                                                         @NotNull String errorMessage) {
        return validate(fieldName, value, Objects::nonNull, errorMessage);
    }

    /**
     * Validate a string is not empty.
     *
     * @param fieldName the field name
     * @param value     the string value
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireNotEmpty(@NotNull String fieldName, @Nullable String value) {
        return validate(fieldName, value, v -> v != null && !v.isEmpty(), "cannot be empty");
    }

    /**
     * Validate a string matches a minimum length.
     *
     * @param fieldName the field name
     * @param value     the string value
     * @param minLength minimum length required
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireMinLength(@NotNull String fieldName, @Nullable String value,
                                                           int minLength) {
        return validate(fieldName, value,
            v -> v != null && v.length() >= minLength,
            "must be at least " + minLength + " characters");
    }

    /**
     * Validate a string matches a maximum length.
     *
     * @param fieldName the field name
     * @param value     the string value
     * @param maxLength maximum length allowed
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireMaxLength(@NotNull String fieldName, @Nullable String value,
                                                           int maxLength) {
        return validate(fieldName, value,
            v -> v == null || v.length() <= maxLength,
            "must be at most " + maxLength + " characters");
    }

    /**
     * Validate a string length is within a range.
     *
     * @param fieldName the field name
     * @param value     the string value
     * @param minLength minimum length
     * @param maxLength maximum length
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireLengthRange(@NotNull String fieldName, @Nullable String value,
                                                             int minLength, int maxLength) {
        return validate(fieldName, value,
            v -> v != null && v.length() >= minLength && v.length() <= maxLength,
            "must be between " + minLength + " and " + maxLength + " characters");
    }

    /**
     * Validate a string matches a regex pattern.
     *
     * @param fieldName the field name
     * @param value     the string value
     * @param pattern   regex pattern
     * @param message   error message if pattern doesn't match
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requirePattern(@NotNull String fieldName, @Nullable String value,
                                                         @NotNull String pattern, @NotNull String message) {
        return validate(fieldName, value, v -> v != null && v.matches(pattern), message);
    }

    /**
     * Validate a number is at least a minimum value.
     *
     * @param fieldName the field name
     * @param value     the numeric value
     * @param min       minimum value (inclusive)
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireMin(@NotNull String fieldName, @Nullable Number value, double min) {
        return validate(fieldName, value,
            v -> v != null && v.doubleValue() >= min,
            "must be at least " + min);
    }

    /**
     * Validate a number is at most a maximum value.
     *
     * @param fieldName the field name
     * @param value     the numeric value
     * @param max       maximum value (inclusive)
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireMax(@NotNull String fieldName, @Nullable Number value, double max) {
        return validate(fieldName, value,
            v -> v != null && v.doubleValue() <= max,
            "must be at most " + max);
    }

    /**
     * Validate a number is within a range.
     *
     * @param fieldName the field name
     * @param value     the numeric value
     * @param min       minimum value (inclusive)
     * @param max       maximum value (inclusive)
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireRange(@NotNull String fieldName, @Nullable Number value,
                                                       double min, double max) {
        return validate(fieldName, value,
            v -> v != null && v.doubleValue() >= min && v.doubleValue() <= max,
            "must be between " + min + " and " + max);
    }

    /**
     * Validate a value is positive.
     *
     * @param fieldName the field name
     * @param value     the numeric value
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requirePositive(@NotNull String fieldName, @Nullable Number value) {
        return validate(fieldName, value,
            v -> v != null && v.doubleValue() > 0,
            "must be positive");
    }

    /**
     * Validate a value is non-negative.
     *
     * @param fieldName the field name
     * @param value     the numeric value
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator requireNonNegative(@NotNull String fieldName, @Nullable Number value) {
        return validate(fieldName, value,
            v -> v != null && v.doubleValue() >= 0,
            "must be non-negative");
    }

    /**
     * Validate a value is one of the allowed values.
     *
     * @param fieldName     the field name
     * @param value         the value to check
     * @param allowedValues allowed values
     * @param <T>           value type
     * @return this aggregator for chaining
     */
    @SafeVarargs
    public final <T> @NotNull ValidationAggregator requireOneOf(@NotNull String fieldName, @Nullable T value,
                                                                 @NotNull T... allowedValues) {
        Set<T> allowed = new HashSet<>(Arrays.asList(allowedValues));
        return validate(fieldName, value, allowed::contains,
            "must be one of: " + Arrays.toString(allowedValues));
    }

    /**
     * Add a custom error without a validation check.
     *
     * @param fieldName the field name
     * @param message   error message
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator addError(@NotNull String fieldName, @NotNull String message) {
        Preconditions.checkNotNull(fieldName, "fieldName");
        Preconditions.checkNotNull(message, "message");
        errors.add(new ValidationError(fieldName, message, null));
        return this;
    }

    /**
     * Add a custom error with the invalid value.
     *
     * @param fieldName    the field name
     * @param message      error message
     * @param invalidValue the value that caused the error
     * @return this aggregator for chaining
     */
    public @NotNull ValidationAggregator addError(@NotNull String fieldName, @NotNull String message,
                                                   @Nullable Object invalidValue) {
        Preconditions.checkNotNull(fieldName, "fieldName");
        Preconditions.checkNotNull(message, "message");
        errors.add(new ValidationError(fieldName, message, invalidValue));
        return this;
    }

    /**
     * Check if all validations passed.
     *
     * @return true if no errors
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Check if any validation failed.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Get the number of errors.
     *
     * @return error count
     */
    public int errorCount() {
        return errors.size();
    }

    /**
     * Get all errors.
     *
     * @return list of all errors
     */
    public @NotNull List<ValidationError> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Build the final validation result.
     *
     * @return the validation result
     */
    public @NotNull ValidationResult build() {
        return new ValidationResult(errors, validatedValues);
    }

    /**
     * Collect all validation results and return.
     * Alias for {@link #build()}.
     *
     * @return the validation result
     */
    public @NotNull ValidationResult collect() {
        return build();
    }

    /**
     * Throw an exception if there are validation errors.
     *
     * @throws ValidationException if validation failed
     */
    public void throwIfInvalid() throws ValidationException {
        if (hasErrors()) {
            throw new ValidationException(build());
        }
    }

    /**
     * Send all error messages to a command sender.
     *
     * @param sender the sender to send errors to
     */
    public void sendErrors(@NotNull org.bukkit.command.CommandSender sender) {
        Preconditions.checkNotNull(sender, "sender");
        if (errors.isEmpty()) return;

        sender.sendMessage("§c§lValidation Errors:");
        for (ValidationError error : errors) {
            sender.sendMessage("  " + error.formatColored());
        }
    }

    /**
     * Send all error messages with a header.
     *
     * @param sender the sender to send errors to
     * @param header header message
     */
    public void sendErrors(@NotNull org.bukkit.command.CommandSender sender, @NotNull String header) {
        Preconditions.checkNotNull(sender, "sender");
        Preconditions.checkNotNull(header, "header");
        if (errors.isEmpty()) return;

        sender.sendMessage(header);
        for (ValidationError error : errors) {
            sender.sendMessage("  " + error.formatColored());
        }
    }

    /**
     * Exception thrown when validation fails.
     */
    public static final class ValidationException extends RuntimeException {
        private final ValidationResult result;

        ValidationException(@NotNull ValidationResult result) {
            super("Validation failed with " + result.errorCount() + " error(s)");
            this.result = result;
        }

        /**
         * @return the validation result containing all errors
         */
        public @NotNull ValidationResult result() {
            return result;
        }

        /**
         * @return list of all error messages
         */
        public @NotNull List<String> errorMessages() {
            return result.errorMessages();
        }
    }
}
