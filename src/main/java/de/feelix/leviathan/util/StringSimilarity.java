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
    
    private StringSimilarity() {
        throw new AssertionError("Utility class");
    }
    
    /**
     * Calculate the Levenshtein distance between two strings.
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into another.
     *
     * @param s1 first string
     * @param s2 second string
     * @return the Levenshtein distance
     */
    public static int levenshteinDistance(@NotNull String s1, @NotNull String s2) {
        Preconditions.checkNotNull(s1, "s1");
        Preconditions.checkNotNull(s2, "s2");
        
        String lower1 = s1.toLowerCase();
        String lower2 = s2.toLowerCase();
        
        int len1 = lower1.length();
        int len2 = lower2.length();
        
        if (len1 == 0) return len2;
        if (len2 == 0) return len1;
        
        int[] prev = new int[len2 + 1];
        int[] curr = new int[len2 + 1];
        
        // Initialize first row
        for (int j = 0; j <= len2; j++) {
            prev[j] = j;
        }
        
        // Calculate distances
        for (int i = 1; i <= len1; i++) {
            curr[0] = i;
            for (int j = 1; j <= len2; j++) {
                int cost = (lower1.charAt(i - 1) == lower2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                    Math.min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
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
     *
     * @param input the input string to compare
     * @param candidates list of candidate strings
     * @param maxSuggestions maximum number of suggestions to return
     * @param minSimilarity minimum similarity threshold (0.0 to 1.0)
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
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            double sim = similarity(input, candidate);
            if (sim >= minSimilarity) {
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
     * @param input the input string to compare
     * @param candidates list of candidate strings
     * @return list of suggestions sorted by similarity
     */
    public static @NotNull List<String> findSimilar(@NotNull String input, @NotNull List<String> candidates) {
        return findSimilar(input, candidates, 3, 0.4);
    }
    
    private record SimilarityResult(String value, double similarity) {}
}
