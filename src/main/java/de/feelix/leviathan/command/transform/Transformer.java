package de.feelix.leviathan.command.transform;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A transformer that modifies parsed argument values after parsing but before validation.
 * <p>
 * Transformers are useful for:
 * <ul>
 *   <li>Normalizing input (trim whitespace, convert case)</li>
 *   <li>Expanding shortcuts or aliases</li>
 *   <li>Formatting values consistently</li>
 *   <li>Resolving references or placeholders</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // String transformers
 * ArgContext.builder()
 *     .transformer(Transformer.trim())
 *     .transformer(Transformer.lowercase())
 *     .build();
 *
 * // Custom transformer
 * Transformer<String> expandShortcuts = Transformer.of(value -> {
 *     if ("dia".equalsIgnoreCase(value)) return "diamond";
 *     if ("g".equalsIgnoreCase(value)) return "gold";
 *     return value;
 * });
 * }</pre>
 *
 * @param <T> the type of value being transformed
 */
@FunctionalInterface
public interface Transformer<T> {

    /**
     * Transform the given value.
     * <p>
     * Transformers should handle null values gracefully (typically returning null).
     *
     * @param value the value to transform (may be null)
     * @return the transformed value
     */
    @Nullable
    T transform(@Nullable T value);

    /**
     * Create a transformer from a function.
     *
     * @param function the transformation function
     * @param <T>      the value type
     * @return a new transformer
     */
    static <T> @NotNull Transformer<T> of(@NotNull Function<T, T> function) {
        return value -> value == null ? null : function.apply(value);
    }

    /**
     * Create a transformer from a unary operator.
     *
     * @param operator the transformation operator
     * @param <T>      the value type
     * @return a new transformer
     */
    static <T> @NotNull Transformer<T> from(@NotNull UnaryOperator<T> operator) {
        return value -> value == null ? null : operator.apply(value);
    }

    /**
     * Chain this transformer with another, applying this one first.
     *
     * @param after the transformer to apply after this one
     * @return a combined transformer
     */
    default @NotNull Transformer<T> andThen(@NotNull Transformer<T> after) {
        return value -> after.transform(this.transform(value));
    }

    /**
     * Chain this transformer with another, applying the other one first.
     *
     * @param before the transformer to apply before this one
     * @return a combined transformer
     */
    default @NotNull Transformer<T> compose(@NotNull Transformer<T> before) {
        return value -> this.transform(before.transform(value));
    }

    // ==================== String Transformers ====================

    /**
     * Create a transformer that trims whitespace from strings.
     *
     * @return a trim transformer
     */
    static @NotNull Transformer<String> trim() {
        return value -> value == null ? null : value.trim();
    }

    /**
     * Create a transformer that converts strings to lowercase.
     *
     * @return a lowercase transformer
     */
    static @NotNull Transformer<String> lowercase() {
        return value -> value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /**
     * Create a transformer that converts strings to uppercase.
     *
     * @return an uppercase transformer
     */
    static @NotNull Transformer<String> uppercase() {
        return value -> value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    /**
     * Create a transformer that normalizes whitespace (trims and collapses multiple spaces).
     *
     * @return a whitespace normalizing transformer
     */
    static @NotNull Transformer<String> normalizeWhitespace() {
        return value -> value == null ? null : value.trim().replaceAll("\\s+", " ");
    }

    /**
     * Create a transformer that strips all whitespace from strings.
     *
     * @return a whitespace stripping transformer
     */
    static @NotNull Transformer<String> stripWhitespace() {
        return value -> value == null ? null : value.replaceAll("\\s+", "");
    }

    /**
     * Create a transformer that capitalizes the first letter of a string.
     *
     * @return a capitalize transformer
     */
    static @NotNull Transformer<String> capitalize() {
        return value -> {
            if (value == null || value.isEmpty()) return value;
            return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Create a transformer that replaces a pattern with a replacement.
     *
     * @param pattern     the regex pattern to match
     * @param replacement the replacement string
     * @return a replacing transformer
     */
    static @NotNull Transformer<String> replace(@NotNull String pattern, @NotNull String replacement) {
        return value -> value == null ? null : value.replaceAll(pattern, replacement);
    }

    /**
     * Create a transformer that prefixes strings with a given prefix.
     *
     * @param prefix the prefix to add
     * @return a prefixing transformer
     */
    static @NotNull Transformer<String> prefix(@NotNull String prefix) {
        return value -> value == null ? null : prefix + value;
    }

    /**
     * Create a transformer that suffixes strings with a given suffix.
     *
     * @param suffix the suffix to add
     * @return a suffixing transformer
     */
    static @NotNull Transformer<String> suffix(@NotNull String suffix) {
        return value -> value == null ? null : value + suffix;
    }

    /**
     * Create a transformer that expands shortcuts using a mapping.
     *
     * @param shortcuts map of shortcut -> full value
     * @return a shortcut expanding transformer
     */
    static @NotNull Transformer<String> expandShortcuts(@NotNull java.util.Map<String, String> shortcuts) {
        return value -> {
            if (value == null) return null;
            String lower = value.toLowerCase(Locale.ROOT);
            return shortcuts.getOrDefault(lower, value);
        };
    }

    /**
     * Create a transformer that truncates strings to a maximum length.
     *
     * @param maxLength the maximum length
     * @return a truncating transformer
     */
    static @NotNull Transformer<String> truncate(int maxLength) {
        return value -> {
            if (value == null || value.length() <= maxLength) return value;
            return value.substring(0, maxLength);
        };
    }

    /**
     * Create a transformer that pads strings to a minimum length.
     *
     * @param minLength the minimum length
     * @param padChar   the character to pad with
     * @param padLeft   true to pad on the left, false to pad on the right
     * @return a padding transformer
     */
    static @NotNull Transformer<String> pad(int minLength, char padChar, boolean padLeft) {
        return value -> {
            if (value == null) return null;
            if (value.length() >= minLength) return value;
            StringBuilder sb = new StringBuilder();
            int padding = minLength - value.length();
            String padString = String.valueOf(padChar).repeat(padding);
            if (padLeft) {
                sb.append(padString).append(value);
            } else {
                sb.append(value).append(padString);
            }
            return sb.toString();
        };
    }

    // ==================== Numeric Transformers ====================

    /**
     * Create a transformer that clamps integers to a range.
     *
     * @param min minimum value
     * @param max maximum value
     * @return a clamping transformer
     */
    static @NotNull Transformer<Integer> clampInt(int min, int max) {
        return value -> value == null ? null : Math.max(min, Math.min(max, value));
    }

    /**
     * Create a transformer that clamps longs to a range.
     *
     * @param min minimum value
     * @param max maximum value
     * @return a clamping transformer
     */
    static @NotNull Transformer<Long> clampLong(long min, long max) {
        return value -> value == null ? null : Math.max(min, Math.min(max, value));
    }

    /**
     * Create a transformer that clamps doubles to a range.
     *
     * @param min minimum value
     * @param max maximum value
     * @return a clamping transformer
     */
    static @NotNull Transformer<Double> clampDouble(double min, double max) {
        return value -> value == null ? null : Math.max(min, Math.min(max, value));
    }

    /**
     * Create a transformer that rounds doubles to a specified number of decimal places.
     *
     * @param decimalPlaces number of decimal places
     * @return a rounding transformer
     */
    static @NotNull Transformer<Double> round(int decimalPlaces) {
        double factor = Math.pow(10, decimalPlaces);
        return value -> value == null ? null : Math.round(value * factor) / factor;
    }

    /**
     * Create an identity transformer that returns values unchanged.
     *
     * @param <T> the value type
     * @return an identity transformer
     */
    static <T> @NotNull Transformer<T> identity() {
        return value -> value;
    }
}
