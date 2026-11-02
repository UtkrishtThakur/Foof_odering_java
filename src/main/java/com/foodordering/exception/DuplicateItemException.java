package com.foodordering.exception;

/**
 * Thrown when an attempt is made to add a menu item whose ID already
 * exists in the repository.
 */
public class DuplicateItemException extends RuntimeException {

    public DuplicateItemException(String message) {
        super(message);
    }

    public DuplicateItemException(String message, Throwable cause) {
        super(message, cause);
    }
}
