package com.foodordering.exception;

/**
 * Thrown when a requested menu item cannot be found in the repository.
 */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String message) {
        super(message);
    }

    public ItemNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
