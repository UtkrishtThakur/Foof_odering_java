package com.foodordering.model;

/**
 * Represents a registered customer who can browse the menu,
 * manage a cart, and place orders.
 */
public class Customer extends User {

    private String deliveryAddress;

    /**
     * Constructs a Customer with basic identity information.
     *
     * @param userId           unique identifier
     * @param name             display name
     * @param email            email address
     * @param passwordHash     pre-hashed password
     * @param deliveryAddress  default delivery address (may be null)
     */
    public Customer(String userId, String name, String email,
                    String passwordHash, String deliveryAddress) {
        super(userId, name, email, passwordHash);
        this.deliveryAddress = deliveryAddress;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    @Override
    public String getRole() {
        return "CUSTOMER";
    }
}
