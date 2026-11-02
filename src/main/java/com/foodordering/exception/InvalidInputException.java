package com.foodordering.exception;

/**
 * Thrown when a caller provides an invalid argument that does not
 * meet a business-rule constraint (e.g., negative price, blank name).
 *
 * <p>This is a domain-level complement to {@link IllegalArgumentException}
 * so that the service layer can communicate validation failures clearly
 * without leaking low-level exception types through the API.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
