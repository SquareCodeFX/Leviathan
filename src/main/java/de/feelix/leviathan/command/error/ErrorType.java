package de.feelix.leviathan.command.error;

import de.feelix.leviathan.annotations.NotNull;

import java.util.List;

/**
 * Types of errors that can occur during command processing.
 * <p>
 * Used by exception handlers to identify the category of error
 * and provide appropriate handling or messaging.
 * <p>
 * Each error type includes:
 * <ul>
 *   <li>A category for grouping related errors</li>
 *   <li>Diagnostic suggestions to help resolve the error</li>
 * </ul>
 */
public enum ErrorType {
    /**
     * Command-level permission denied
     */
    PERMISSION(
        ErrorCategory.ACCESS,
        "The player lacks the required permission node",
        "Check if the permission is registered correctly",
        "Verify permission plugin configuration"
    ),

    /**
     * Player-only command used by non-player
     */
    PLAYER_ONLY(
        ErrorCategory.ACCESS,
        "This command can only be executed by players",
        "Console or command blocks cannot use this command",
        "Consider adding a non-player alternative if needed"
    ),

    /**
     * Guard check failed
     */
    GUARD_FAILED(
        ErrorCategory.ACCESS,
        "A guard condition was not met",
        "Check if player meets all requirements (level, items, etc.)",
        "Review guard logic for edge cases"
    ),

    /**
     * Argument permission denied
     */
    ARGUMENT_PERMISSION(
        ErrorCategory.ACCESS,
        "The player lacks permission for this argument",
        "Check argument-level permission configuration",
        "Verify permission inheritance"
    ),

    /**
     * Parsing failed for an argument
     */
    PARSING(
        ErrorCategory.INPUT,
        "Invalid argument format provided",
        "Check if the argument type matches expected format",
        "Ensure argument parser handles edge cases"
    ),

    /**
     * Validation failed for an argument
     */
    VALIDATION(
        ErrorCategory.INPUT,
        "Argument value failed validation",
        "Value may be out of allowed range",
        "Check validation rules and constraints"
    ),

    /**
     * Cross-argument validation failed
     */
    CROSS_VALIDATION(
        ErrorCategory.INPUT,
        "Multiple arguments have conflicting values",
        "Check cross-argument validation logic",
        "Ensure argument combinations are valid"
    ),

    /**
     * Error during command execution
     */
    EXECUTION(
        ErrorCategory.RUNTIME,
        "Error occurred during command execution logic",
        "Check for null pointer dereferences",
        "Verify external API calls and database connections"
    ),

    /**
     * Async command timeout
     */
    TIMEOUT(
        ErrorCategory.PERFORMANCE,
        "Async command execution exceeded time limit",
        "Consider increasing timeout duration",
        "Optimize long-running operations"
    ),

    /**
     * Invalid argument count
     */
    USAGE(
        ErrorCategory.INPUT,
        "Incorrect number of arguments provided",
        "Check command syntax and required arguments",
        "Review optional vs required argument configuration"
    ),

    /**
     * Internal error during command processing (unexpected exception)
     */
    INTERNAL_ERROR(
        ErrorCategory.RUNTIME,
        "Unexpected internal error occurred",
        "This may indicate a bug in the command framework",
        "Check for recent code changes that might cause issues"
    );

    private final ErrorCategory category;
    private final List<String> suggestions;

    ErrorType(@NotNull ErrorCategory category, @NotNull String... suggestions) {
        this.category = category;
        this.suggestions = List.of(suggestions);
    }

    /**
     * Gets the category of this error type.
     *
     * @return the error category
     */
    @NotNull
    public ErrorCategory getCategory() {
        return category;
    }

    /**
     * Gets the diagnostic suggestions for this error type.
     *
     * @return an immutable list of suggestion strings
     */
    @NotNull
    public List<String> getSuggestions() {
        return suggestions;
    }

    /**
     * Gets a human-readable description of this error's category.
     *
     * @return the category description
     */
    @NotNull
    public String getCategoryDescription() {
        return category.getDescription();
    }

    /**
     * Categories for grouping related error types.
     */
    public enum ErrorCategory {
        /**
         * Access and authorization related errors
         */
        ACCESS("Access/Authorization Issue"),
        /**
         * Input and argument related errors
         */
        INPUT("Input/Argument Issue"),
        /**
         * Runtime and execution related errors
         */
        RUNTIME("Runtime/Execution Issue"),
        /**
         * Performance related errors
         */
        PERFORMANCE("Performance Issue");

        private final String description;

        ErrorCategory(@NotNull String description) {
            this.description = description;
        }

        /**
         * Gets the human-readable description of this category.
         *
         * @return the category description
         */
        @NotNull
        public String getDescription() {
            return description;
        }
    }
}
