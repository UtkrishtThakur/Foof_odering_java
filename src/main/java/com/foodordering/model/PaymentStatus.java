package com.foodordering.model;

/**
 * Represents the payment state associated with an order.
 */
public enum PaymentStatus {
    UNPAID,
    PENDING,
    PAID,
    REFUNDED,
    FAILED
}
