package com.foodordering.exception;

/**
 * Thrown when user authentication fails due to invalid credentials,
 * non-existent user identity, or unauthorized role access.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
