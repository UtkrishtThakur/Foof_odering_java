package com.foodordering.model;

/**
 * Abstract base class representing a system user.
 * Encapsulates shared identity and credential fields used by
 * both {@link Customer} and {@link Admin}.
 */
public abstract class User {

    private final String userId;
    private String name;
    private String email;
    private String passwordHash; // stored as a hash — never in plain text

    /**
     * Constructs a User with the required identity fields.
     *
     * @param userId       unique identifier — must not be null or blank
     * @param name         display name — must not be null or blank
     * @param email        email address — must not be null or blank
     * @param passwordHash pre-hashed password string
     */
    protected User(String userId, String name, String email, String passwordHash) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank.");
        }
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    /** Returns the stored password hash. Never expose the raw value in logs. */
    public String getPasswordHash() {
        return passwordHash;
    }

    // -------------------------------------------------------------------------
    // Setters — userId is immutable after construction
    // -------------------------------------------------------------------------

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank.");
        }
        this.name = name;
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank.");
        }
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    // -------------------------------------------------------------------------
    // Abstract behaviour — subclasses declare their role
    // -------------------------------------------------------------------------

    /** Returns the role label for this user (e.g., "CUSTOMER" or "ADMIN"). */
    public abstract String getRole();

    @Override
    public String toString() {
        return getRole() + "[id=" + userId + ", name=" + name + ", email=" + email + "]";
    }
}
