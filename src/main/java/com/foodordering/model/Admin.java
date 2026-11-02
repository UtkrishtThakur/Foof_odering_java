package com.foodordering.model;

/**
 * Represents a system administrator who can manage menu items,
 * orders, and discount codes.
 */
public class Admin extends User {

    private String adminCode; // internal access code, not exposed publicly

    /**
     * Constructs an Admin user.
     *
     * @param userId       unique identifier
     * @param name         display name
     * @param email        email address
     * @param passwordHash pre-hashed password
     * @param adminCode    internal admin access code
     */
    public Admin(String userId, String name, String email,
                 String passwordHash, String adminCode) {
        super(userId, name, email, passwordHash);
        this.adminCode = adminCode;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getAdminCode() {
        return adminCode;
    }

    public void setAdminCode(String adminCode) {
        this.adminCode = adminCode;
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }
}
