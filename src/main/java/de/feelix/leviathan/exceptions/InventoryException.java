package de.feelix.leviathan.exceptions;

/**
 * Generic runtime exception for the inventory API subsystem.
 */
public class InventoryException extends RuntimeException {
    public InventoryException(String message) { super(message); }
    public InventoryException(String message, Throwable cause) { super(message, cause); }
}
