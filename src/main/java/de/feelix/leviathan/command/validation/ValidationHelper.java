package de.feelix.leviathan.command.validation;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgContext;

/**
 * Utility class for validating parsed argument values against validation rules.
 * <p>
 * This class handles numeric range validation, string validation, and custom validators
 * defined in {@link ArgContext}.
 */
public final class ValidationHelper {

    private ValidationHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates a numeric value against min/max constraints.
     *
     * @param value the numeric value to validate
     * @param min   the minimum allowed value (null means no minimum)
     * @param max   the maximum allowed value (null means no maximum)
     * @param <T>   the numeric type (Integer, Long, Double, Float)
     * @return null if valid, or an error message string if invalid
     */
    private static <T extends Number & Comparable<T>> @Nullable String validateNumericRange(
        @NotNull T value, @Nullable T min, @Nullable T max) {
        if (min != null && value.compareTo(min) < 0) {
            return "must be at least " + min + " (got " + value + ")";
        }
        if (max != null && value.compareTo(max) > 0) {
            return "must be at most " + max + " (got " + value + ")";
        }
        return null;
    }

    /**
     * Validates a parsed value against the validation rules defined in ArgContext.
     *
     * @param value    the parsed value to validate
     * @param ctx      the ArgContext containing validation rules
     * @param argName  the argument name (for error messages)
     * @param typeName the type name (for error messages)
     * @return null if valid, or an error message string if invalid
     */
    @SuppressWarnings("unchecked")
    public static @Nullable String validateValue(@Nullable Object value, @NotNull ArgContext ctx,
                                                 @NotNull String argName, @NotNull String typeName) {
        if (value == null) {
            return null; // null values pass validation (optionality is handled separately)
        }

        // Numeric range validation using helper method
        if (value instanceof Integer) {
            String error = validateNumericRange((Integer) value, ctx.intMin(), ctx.intMax());
            if (error != null) return error;
        } else if (value instanceof Long) {
            String error = validateNumericRange((Long) value, ctx.longMin(), ctx.longMax());
            if (error != null) return error;
        } else if (value instanceof Double) {
            String error = validateNumericRange((Double) value, ctx.doubleMin(), ctx.doubleMax());
            if (error != null) return error;
        } else if (value instanceof Float) {
            String error = validateNumericRange((Float) value, ctx.floatMin(), ctx.floatMax());
            if (error != null) return error;
        }

        // String validation
        if (value instanceof String) {
            String strVal = (String) value;
            if (ctx.stringMinLength() != null && strVal.length() < ctx.stringMinLength()) {
                return "length must be at least " + ctx.stringMinLength() + " (got " + strVal.length() + ")";
            }
            if (ctx.stringMaxLength() != null && strVal.length() > ctx.stringMaxLength()) {
                return "length must be at most " + ctx.stringMaxLength() + " (got " + strVal.length() + ")";
            }
            if (ctx.stringPattern() != null && !ctx.stringPattern().matcher(strVal).matches()) {
                return "does not match required pattern: " + ctx.stringPattern().pattern();
            }
        }

        // Custom validators
        for (ArgContext.Validator<?> validator : ctx.customValidators()) {
            try {
                ArgContext.Validator<Object> objValidator = (ArgContext.Validator<Object>) validator;
                String error = objValidator.validate(value);
                if (error != null) {
                    return error;
                }
            } catch (ClassCastException e) {
                // Type mismatch - skip this validator
            }
        }

        return null; // All validations passed
    }
}
