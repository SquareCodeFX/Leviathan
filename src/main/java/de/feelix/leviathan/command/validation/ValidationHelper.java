package de.feelix.leviathan.command.validation;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.command.message.MessageProvider;
import de.feelix.leviathan.util.Preconditions;

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
     * @param value    the numeric value to validate
     * @param min      the minimum allowed value (null means no minimum)
     * @param max      the maximum allowed value (null means no maximum)
     * @param messages the message provider for validation messages
     * @param <T>      the numeric type (Integer, Long, Double, Float)
     * @return null if valid, or an error message string if invalid
     */
    private static <T extends Number & Comparable<T>> @Nullable String validateNumericRange(
        @NotNull T value, @Nullable T min, @Nullable T max, @NotNull MessageProvider messages) {
        Preconditions.checkNotNull(value, "value");
        Preconditions.checkNotNull(messages, "messages");
        if (min != null && value.compareTo(min) < 0) {
            return messages.numericTooSmall(min.toString(), value.toString());
        }
        if (max != null && value.compareTo(max) > 0) {
            return messages.numericTooLarge(max.toString(), value.toString());
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
     * @param messages the message provider for validation messages
     * @return null if valid, or an error message string if invalid
     */
    @SuppressWarnings("unchecked")
    public static @Nullable String validateValue(@Nullable Object value, @NotNull ArgContext ctx,
                                                 @NotNull String argName, @NotNull String typeName,
                                                 @NotNull MessageProvider messages) {
        Preconditions.checkNotNull(ctx, "ctx");
        Preconditions.checkNotNull(argName, "argName");
        Preconditions.checkNotNull(typeName, "typeName");
        Preconditions.checkNotNull(messages, "messages");
        if (value == null) {
            return null; // null values pass validation (optionality is handled separately)
        }

        // Numeric range validation using helper method
        if (value instanceof Integer) {
            String error = validateNumericRange((Integer) value, ctx.intMin(), ctx.intMax(), messages);
            if (error != null) return error;
        } else if (value instanceof Long) {
            String error = validateNumericRange((Long) value, ctx.longMin(), ctx.longMax(), messages);
            if (error != null) return error;
        } else if (value instanceof Double) {
            String error = validateNumericRange((Double) value, ctx.doubleMin(), ctx.doubleMax(), messages);
            if (error != null) return error;
        } else if (value instanceof Float) {
            String error = validateNumericRange((Float) value, ctx.floatMin(), ctx.floatMax(), messages);
            if (error != null) return error;
        }

        // String validation
        if (value instanceof String) {
            String strVal = (String) value;
            if (ctx.stringMinLength() != null && strVal.length() < ctx.stringMinLength()) {
                return messages.stringTooShort(ctx.stringMinLength(), strVal.length());
            }
            if (ctx.stringMaxLength() != null && strVal.length() > ctx.stringMaxLength()) {
                return messages.stringTooLong(ctx.stringMaxLength(), strVal.length());
            }
            if (ctx.stringPattern() != null && !ctx.stringPattern().matcher(strVal).matches()) {
                return messages.stringPatternMismatch(ctx.stringPattern().pattern());
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
