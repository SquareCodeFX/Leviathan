package de.feelix.leviathan.command.suggestion;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.util.StringSimilarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Engine for generating "Did you mean...?" suggestions when parsing fails.
 * <p>
 * Uses Levenshtein distance for string similarity matching to suggest
 * valid alternatives when user input doesn't match expected values.
 * <p>
 * Example usage:
 * <pre>{@code
 * List<String> validOptions = List.of("diamond", "gold", "iron", "stone");
 * String userInput = "diamnod";  // typo
 *
 * Suggestion suggestion = SuggestionEngine.suggest(userInput, validOptions);
 * if (suggestion.hasSuggestions()) {
 *     sender.sendMessage("Did you mean: " + suggestion.formatted());
 * }
 * }</pre>
 */
public final class SuggestionEngine {

    /**
     * Default minimum similarity threshold for suggestions.
     */
    public static final double DEFAULT_MIN_SIMILARITY = 0.4;

    /**
     * Default maximum number of suggestions to return.
     */
    public static final int DEFAULT_MAX_SUGGESTIONS = 3;

    private SuggestionEngine() {
        throw new AssertionError("Utility class");
    }

    /**
     * Generate suggestions for an invalid input based on available options.
     *
     * @param input   the invalid user input
     * @param options the valid options to compare against
     * @return a Suggestion containing similar options
     */
    public static @NotNull Suggestion suggest(@NotNull String input, @NotNull Collection<String> options) {
        return suggest(input, options, DEFAULT_MAX_SUGGESTIONS, DEFAULT_MIN_SIMILARITY);
    }

    /**
     * Generate suggestions for an invalid input with custom parameters.
     *
     * @param input          the invalid user input
     * @param options        the valid options to compare against
     * @param maxSuggestions maximum number of suggestions to return
     * @param minSimilarity  minimum similarity threshold (0.0 to 1.0)
     * @return a Suggestion containing similar options
     */
    public static @NotNull Suggestion suggest(@NotNull String input,
                                               @NotNull Collection<String> options,
                                               int maxSuggestions,
                                               double minSimilarity) {
        Preconditions.checkNotNull(input, "input");
        Preconditions.checkNotNull(options, "options");

        if (options.isEmpty() || input.isBlank()) {
            return Suggestion.empty(input);
        }

        List<String> optionsList = new ArrayList<>(options);
        List<String> similar = StringSimilarity.findSimilar(input, optionsList, maxSuggestions, minSimilarity);

        return new Suggestion(input, similar);
    }

    /**
     * Generate suggestions for subcommand names.
     *
     * @param input       the invalid subcommand input
     * @param subcommands collection of valid subcommand names
     * @return a Suggestion containing similar subcommand names
     */
    public static @NotNull Suggestion suggestSubcommand(@NotNull String input,
                                                         @NotNull Collection<String> subcommands) {
        return suggest(input, subcommands, DEFAULT_MAX_SUGGESTIONS, 0.5); // Higher threshold for subcommands
    }

    /**
     * Generate suggestions for argument values from completions.
     *
     * @param input       the invalid argument value
     * @param completions collection of valid completion options
     * @return a Suggestion containing similar values
     */
    public static @NotNull Suggestion suggestArgument(@NotNull String input,
                                                       @NotNull Collection<String> completions) {
        return suggest(input, completions, DEFAULT_MAX_SUGGESTIONS, DEFAULT_MIN_SIMILARITY);
    }

    /**
     * Format suggestions as a user-friendly message.
     *
     * @param suggestion the suggestion to format
     * @return formatted message or empty string if no suggestions
     */
    public static @NotNull String formatMessage(@NotNull Suggestion suggestion) {
        Preconditions.checkNotNull(suggestion, "suggestion");
        if (!suggestion.hasSuggestions()) {
            return "";
        }
        return "Did you mean: " + suggestion.formatted() + "?";
    }

    /**
     * Represents a suggestion result containing similar options for an invalid input.
     */
    public record Suggestion(
        @NotNull String originalInput,
        @NotNull List<String> suggestions
    ) {
        /**
         * Create an empty suggestion (no matches found).
         *
         * @param input the original input
         * @return an empty Suggestion
         */
        public static @NotNull Suggestion empty(@NotNull String input) {
            return new Suggestion(input, Collections.emptyList());
        }

        /**
         * Check if there are any suggestions.
         *
         * @return true if at least one suggestion exists
         */
        public boolean hasSuggestions() {
            return !suggestions.isEmpty();
        }

        /**
         * Get the best (most similar) suggestion.
         *
         * @return the best suggestion, or null if none
         */
        public @Nullable String best() {
            return suggestions.isEmpty() ? null : suggestions.get(0);
        }

        /**
         * Format suggestions as a comma-separated string.
         *
         * @return formatted suggestions
         */
        public @NotNull String formatted() {
            return String.join(", ", suggestions);
        }

        /**
         * Format suggestions with quotes around each option.
         *
         * @return formatted suggestions with quotes
         */
        public @NotNull String formattedQuoted() {
            if (suggestions.isEmpty()) {
                return "";
            }
            return suggestions.stream()
                .map(s -> "'" + s + "'")
                .collect(java.util.stream.Collectors.joining(", "));
        }

        /**
         * Get the number of suggestions.
         *
         * @return count of suggestions
         */
        public int count() {
            return suggestions.size();
        }
    }
}
