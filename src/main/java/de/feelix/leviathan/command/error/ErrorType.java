package de.feelix.leviathan.command.error;

/**
 * Types of errors that can occur during command processing.
 * <p>
 * Used by exception handlers to identify the category of error
 * and provide appropriate handling or messaging.
 */
public enum ErrorType {
    /** Command-level permission denied */
    PERMISSION,
    
    /** Player-only command used by non-player */
    PLAYER_ONLY,
    
    /** Guard check failed */
    GUARD_FAILED,
    
    /** Argument permission denied */
    ARGUMENT_PERMISSION,
    
    /** Parsing failed for an argument */
    PARSING,
    
    /** Validation failed for an argument */
    VALIDATION,
    
    /** Cross-argument validation failed */
    CROSS_VALIDATION,
    
    /** Error during command execution */
    EXECUTION,
    
    /** Async command timeout */
    TIMEOUT,
    
    /** Invalid argument count */
    USAGE,
    
    /** Internal error during command processing (unexpected exception) */
    INTERNAL_ERROR
}
