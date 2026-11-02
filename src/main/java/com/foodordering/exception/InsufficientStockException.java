package com.foodordering.exception;

/**
 * Thrown when a stock-reduction request exceeds the available inventory
 * for a menu item.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}
