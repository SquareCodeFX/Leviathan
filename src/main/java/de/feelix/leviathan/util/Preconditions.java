package de.feelix.leviathan.util;

import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

/**
 * Simple runtime precondition utilities for API contracts.
 * <p>
 * Intended to complement the {@code @NotNull} annotation by enforcing non-null
 * requirements at runtime with clear, developer-focused exceptions.
 */
public final class Preconditions {
    private Preconditions() {}

    /**
     * Ensure that the given value is not {@code null}.
     *
     * @param value the value to check
     * @param paramName the parameter name for error messages
     * @param <T> value type
     * @return the non-null value (same reference), for fluent usage
     * @throws ApiMisuseException if {@code value} is {@code null}
     */
    public static <T> @NotNull T checkNotNull(T value, @Nullable String paramName) {
        if (value == null) {
            String name = (paramName == null || paramName.isBlank()) ? "parameter" : paramName;
            throw new ApiMisuseException("@NotNull parameter '" + name + "' is null");
        }
        return value;
    }

    /**
     * Ensure the given string is not null or blank (after trimming).
     * Returns the same instance for fluent usage.
     */
    public static @NotNull String checkNotBlank(String value, @Nullable String paramName) {
        checkNotNull(value, paramName);
        if (value.isBlank()) {
            String name = (paramName == null || paramName.isBlank()) ? "string" : paramName;
            throw new ApiMisuseException("@NotNull @NotBlank parameter '" + name + "' is blank");
        }
        return value;
    }

    /**
     * Verify a boolean argument condition and throw an ApiMisuseException when false.
     */
    public static void checkArgument(boolean condition, @Nullable String message) {
        if (!condition) {
            throw new ApiMisuseException(message == null ? "Illegal argument" : message);
        }
    }

    /**
     * Verify a boolean state condition and throw an ApiMisuseException when false.
     */
    public static void checkState(boolean condition, @Nullable String message) {
        if (!condition) {
            throw new ApiMisuseException(message == null ? "Illegal state" : message);
        }
    }

    /**
     * Ensure that the given long value is non-negative (>= 0).
     *
     * @param value the value to check
     * @param paramName the parameter name for error messages
     * @return the same value, for fluent usage
     * @throws ApiMisuseException if {@code value} is negative
     */
    public static long checkNonNegative(long value, @Nullable String paramName) {
        if (value < 0) {
            String name = (paramName == null || paramName.isBlank()) ? "parameter" : paramName;
            throw new ApiMisuseException("Parameter '" + name + "' must be non-negative, but was: " + value);
        }
        return value;
    }

    /**
     * Ensure that the given int value is non-negative (>= 0).
     *
     * @param value the value to check
     * @param paramName the parameter name for error messages
     * @return the same value, for fluent usage
     * @throws ApiMisuseException if {@code value} is negative
     */
    public static int checkNonNegative(int value, @Nullable String paramName) {
        if (value < 0) {
            String name = (paramName == null || paramName.isBlank()) ? "parameter" : paramName;
            throw new ApiMisuseException("Parameter '" + name + "' must be non-negative, but was: " + value);
        }
        return value;
    }
}
