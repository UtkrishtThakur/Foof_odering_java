package com.foodordering.model;

/**
 * Represents the lifecycle states of a customer order.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED
}
