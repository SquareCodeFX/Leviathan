package de.feelix.leviathan.util;

import de.feelix.leviathan.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for calculating string similarity using the Levenshtein distance algorithm.
 * Used for "Did You Mean" suggestions.
 */
public final class StringSimilarity {

    /**
     * Maximum string length allowed for Levenshtein distance calculation.
     * This limit prevents DoS attacks via extremely long strings (O(n*m) complexity).
     */
    public static final int MAX_STRING_LENGTH = 256;

    private StringSimilarity() {
        throw new AssertionError("Utility class");
    }

    /**
     * Calculate the Levenshtein distance between two strings.
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into another.
     * <p>
     * Security: Strings longer than {@link #MAX_STRING_LENGTH} characters are truncated
     * to prevent DoS attacks, as the algorithm has O(n*m) time complexity.
     *
     * @param s1 first string
     * @param s2 second string
     * @return the Levenshtein distance
     */
    public static int levenshteinDistance(@NotNull String s1, @NotNull String s2) {
        return levenshteinDistanceWithThreshold(s1, s2, Integer.MAX_VALUE);
    }

    /**
     * Calculate the Levenshtein distance with early termination if distance exceeds threshold.
     * This optimization significantly improves performance when searching for similar strings,
     * as it avoids computing the full distance matrix when a string is clearly dissimilar.
     *
     * @param s1        first string
     * @param s2        second string
     * @param threshold maximum distance threshold; if exceeded, returns threshold + 1
     * @return the Levenshtein distance, or threshold + 1 if distance exceeds threshold
     */
    public static int levenshteinDistanceWithThreshold(@NotNull String s1, @NotNull String s2, int threshold) {
        Preconditions.checkNotNull(s1, "s1");
        Preconditions.checkNotNull(s2, "s2");

        // Security: Limit string length to prevent DoS (O(n*m) complexity)
        String truncated1 = s1.length() > MAX_STRING_LENGTH ? s1.substring(0, MAX_STRING_LENGTH) : s1;
        String truncated2 = s2.length() > MAX_STRING_LENGTH ? s2.substring(0, MAX_STRING_LENGTH) : s2;

        String lower1 = truncated1.toLowerCase();
        String lower2 = truncated2.toLowerCase();

        int len1 = lower1.length();
        int len2 = lower2.length();

        // Early termination: if length difference exceeds threshold, they can't be similar enough
        if (Math.abs(len1 - len2) > threshold) {
            return threshold + 1;
        }

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];

        // Initialize first row
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }

        // Calculate distances with early termination
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            int rowMin = curr[0]; // Track minimum value in current row

            for (int j = 1; j <= len2; j++) {
                int cost = (lower1.charAt(i - 1) == lower2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
                rowMin = Math.min(rowMin, curr[j]);
            }

            // Early termination: if minimum in row exceeds threshold, no path can lead to
            // a distance <= threshold
            if (rowMin > threshold) {
                return threshold + 1;
            }

            // Swap arrays
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[len2];
    }

    /**
     * Calculate similarity score between two strings (0.0 to 1.0).
     * Higher score means more similar strings.
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity score between 0.0 (completely different) and 1.0 (identical)
     */
    public static double similarity(@NotNull String s1, @NotNull String s2) {
        Preconditions.checkNotNull(s1, "s1");
        Preconditions.checkNotNull(s2, "s2");

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Find the most similar strings from a list of candidates.
     * <p>
     * Optimized with early termination: candidates that cannot meet the similarity threshold
     * are rejected early without computing the full Levenshtein distance matrix.
     *
     * @param input          the input string to compare
     * @param candidates     list of candidate strings
     * @param maxSuggestions maximum number of suggestions to return
     * @param minSimilarity  minimum similarity threshold (0.0 to 1.0)
     * @return list of suggestions sorted by similarity (most similar first)
     */
    public static @NotNull List<String> findSimilar(@NotNull String input,
                                                    @NotNull List<String> candidates,
                                                    int maxSuggestions,
                                                    double minSimilarity) {
        Preconditions.checkNotNull(input, "input");
        Preconditions.checkNotNull(candidates, "candidates");

        if (candidates.isEmpty() || maxSuggestions <= 0) {
            return Collections.emptyList();
        }

        List<SimilarityResult> results = new ArrayList<>();
        int inputLen = Math.min(input.length(), MAX_STRING_LENGTH);

        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;

            int candidateLen = Math.min(candidate.length(), MAX_STRING_LENGTH);
            int maxLen = Math.max(inputLen, candidateLen);
            if (maxLen == 0) continue;

            // Calculate maximum allowed distance for minSimilarity threshold
            // similarity = 1.0 - (distance / maxLen)
            // minSimilarity <= 1.0 - (distance / maxLen)
            // distance <= (1.0 - minSimilarity) * maxLen
            int maxAllowedDistance = (int) ((1.0 - minSimilarity) * maxLen);

            // Use threshold-based calculation for early termination
            int distance = levenshteinDistanceWithThreshold(input, candidate, maxAllowedDistance);

            if (distance <= maxAllowedDistance) {
                double sim = 1.0 - ((double) distance / maxLen);
                results.add(new SimilarityResult(candidate, sim));
            }
        }

        results.sort(Comparator.comparingDouble(SimilarityResult::similarity).reversed());

        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < Math.min(maxSuggestions, results.size()); i++) {
            suggestions.add(results.get(i).value());
        }

        return suggestions;
    }

    /**
     * Find the most similar strings with default parameters (max 3 suggestions, min 0.4 similarity).
     *
     * @param input      the input string to compare
     * @param candidates list of candidate strings
     * @return list of suggestions sorted by similarity
     */
    public static @NotNull List<String> findSimilar(@NotNull String input, @NotNull List<String> candidates) {
        return findSimilar(input, candidates, 3, 0.4);
    }

    private record SimilarityResult(String value, double similarity) {}
}
