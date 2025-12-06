package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Registry for exception-specific suggestions used by the DetailedExceptionHandler.
 * <p>
 * This class provides a centralized, extensible mechanism for mapping exception types
 * and message patterns to helpful diagnostic suggestions.
 */
public final class ExceptionSuggestionRegistry {

    private static final Map<Class<? extends Throwable>, List<String>> TYPE_SUGGESTIONS = new LinkedHashMap<>();
    private static final Map<Predicate<String>, List<String>> NAME_PATTERN_SUGGESTIONS = new LinkedHashMap<>();
    private static final Map<Predicate<String>, List<String>> MESSAGE_PATTERN_SUGGESTIONS = new LinkedHashMap<>();
    private static final List<String> DEFAULT_SUGGESTIONS;

    static {
        // Initialize type-based suggestions
        registerTypeSuggestion(
            NullPointerException.class,
            "A null value was accessed where an object was expected",
            "Check if all required dependencies are initialized",
            "Verify method return values before using them",
            "Consider using Optional or null checks"
        );

        registerTypeSuggestion(
            IllegalArgumentException.class,
            "An invalid argument was passed to a method",
            "Check argument validation before method calls",
            "Review the expected parameter constraints"
        );

        registerTypeSuggestion(
            IllegalStateException.class,
            "Object is in an invalid state for the operation",
            "Check initialization order of components",
            "Verify that prerequisites are met before operation"
        );

        registerTypeSuggestion(
            ClassCastException.class,
            "Type casting failed - incompatible types",
            "Check generic type parameters",
            "Verify object types before casting"
        );

        registerTypeSuggestion(
            IndexOutOfBoundsException.class,
            "Array or list index is out of valid range",
            "Check array/list bounds before accessing",
            "Verify loop conditions and index calculations"
        );

        registerTypeSuggestion(
            NumberFormatException.class,
            "String could not be parsed as a number",
            "Validate input format before parsing",
            "Check for empty strings or non-numeric characters"
        );

        registerTypeSuggestion(
            UnsupportedOperationException.class,
            "Operation is not supported by this implementation",
            "Check if using an immutable collection",
            "Verify API compatibility"
        );

        registerTypeSuggestion(
            SecurityException.class,
            "Security manager denied the operation",
            "Check security policy configuration",
            "Verify required permissions are granted"
        );

        // Initialize name pattern-based suggestions
        registerNamePatternSuggestion(
            name -> name.contains("SQL") || name.contains("Database"),
            "Database operation failed",
            "Check database connection and credentials",
            "Verify SQL syntax and table existence"
        );

        registerNamePatternSuggestion(
            name -> name.contains("IO") || name.contains("File"),
            "I/O operation failed",
            "Check file permissions and path validity",
            "Verify disk space and file existence"
        );

        registerNamePatternSuggestion(
            name -> name.contains("Timeout") || name.contains("Connection"),
            "Network or connection timeout occurred",
            "Check network connectivity",
            "Verify remote service availability"
        );

        // Initialize message pattern-based suggestions
        registerMessagePatternSuggestion(
            msg -> msg.toLowerCase().contains("null"),
            "Exception message mentions 'null' - check for uninitialized variables"
        );

        registerMessagePatternSuggestion(
            msg -> msg.toLowerCase().contains("not found") || msg.toLowerCase().contains("missing"),
            "Something is missing - check configuration and dependencies"
        );

        registerMessagePatternSuggestion(
            msg -> msg.toLowerCase().contains("denied") || msg.toLowerCase().contains("permission"),
            "Access was denied - check permissions and access rights"
        );

        // Default suggestions
        DEFAULT_SUGGESTIONS = List.of(
            "Review the exception message for specific details",
            "Check the stack trace for the error origin",
            "Consult documentation for this exception type"
        );
    }

    private ExceptionSuggestionRegistry() {
        // Utility class
    }

    private static void registerTypeSuggestion(Class<? extends Throwable> type, String... suggestions) {
        TYPE_SUGGESTIONS.put(type, List.of(suggestions));
    }

    private static void registerNamePatternSuggestion(Predicate<String> pattern, String... suggestions) {
        NAME_PATTERN_SUGGESTIONS.put(pattern, List.of(suggestions));
    }

    private static void registerMessagePatternSuggestion(Predicate<String> pattern, String... suggestions) {
        MESSAGE_PATTERN_SUGGESTIONS.put(pattern, List.of(suggestions));
    }

    /**
     * Gets suggestions for the given exception based on its type.
     *
     * @param exception the exception to get suggestions for
     * @return a list of suggestion strings
     */
    @NotNull
    public static List<String> getSuggestionsForException(@NotNull Throwable exception) {
        List<String> suggestions = new ArrayList<>();

        // Check type-based suggestions (including superclasses)
        boolean foundTypeSuggestion = false;
        for (Map.Entry<Class<? extends Throwable>, List<String>> entry : TYPE_SUGGESTIONS.entrySet()) {
            if (entry.getKey().isInstance(exception)) {
                suggestions.addAll(entry.getValue());
                foundTypeSuggestion = true;
                break;
            }
        }

        // Check name pattern-based suggestions if no type match
        if (!foundTypeSuggestion) {
            String exceptionName = exception.getClass().getSimpleName();
            boolean foundNamePattern = false;
            for (Map.Entry<Predicate<String>, List<String>> entry : NAME_PATTERN_SUGGESTIONS.entrySet()) {
                if (entry.getKey().test(exceptionName)) {
                    suggestions.addAll(entry.getValue());
                    foundNamePattern = true;
                    break;
                }
            }

            // Use default suggestions if no match found
            if (!foundNamePattern) {
                suggestions.addAll(DEFAULT_SUGGESTIONS);
            }
        }

        return Collections.unmodifiableList(suggestions);
    }

    /**
     * Gets additional suggestions based on the exception message content.
     *
     * @param message the exception message (may be null)
     * @return a list of additional suggestion strings
     */
    @NotNull
    public static List<String> getMessageBasedSuggestions(@NotNull String message) {
        if (message == null || message.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<Predicate<String>, List<String>> entry : MESSAGE_PATTERN_SUGGESTIONS.entrySet()) {
            if (entry.getKey().test(message)) {
                suggestions.addAll(entry.getValue());
            }
        }
        return Collections.unmodifiableList(suggestions);
    }
}
