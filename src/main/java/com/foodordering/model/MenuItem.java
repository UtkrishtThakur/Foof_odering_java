package com.foodordering.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a single item on the restaurant menu.
 *
 * <p>Encapsulation rules:
 * <ul>
 *   <li>ID is immutable after construction.</li>
 *   <li>Price must be non-null and non-negative.</li>
 *   <li>Stock must be non-negative.</li>
 *   <li>Name and category must not be blank.</li>
 * </ul>
 */
public class MenuItem {

    private final String id;       // immutable unique identifier
    private String name;
    private String category;
    private BigDecimal price;
    private int stockQuantity;
    private boolean available;

    /**
     * Constructs a new MenuItem with full validation.
     *
     * @param id            unique identifier — must not be null or blank
     * @param name          item name — must not be null or blank
     * @param category      menu category (e.g., "Burgers") — must not be null or blank
     * @param price         unit price — must not be null and must be &gt;= 0
     * @param stockQuantity initial stock — must be &gt;= 0
     * @param available     whether the item is currently offered to customers
     */
    public MenuItem(String id, String name, String category,
                    BigDecimal price, int stockQuantity, boolean available) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("MenuItem ID must not be null or blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MenuItem name must not be null or blank.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("MenuItem category must not be null or blank.");
        }
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null.");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must not be negative.");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity must not be negative.");
        }

        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.stockQuantity = stockQuantity;
        this.available = available;
    }

    // -------------------------------------------------------------------------
    // Getters — ID has no setter (immutable)
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public boolean isAvailable() {
        return available;
    }

    // -------------------------------------------------------------------------
    // Setters with validation
    // -------------------------------------------------------------------------

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MenuItem name must not be null or blank.");
        }
        this.name = name;
    }

    public void setCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("MenuItem category must not be null or blank.");
        }
        this.category = category;
    }

    public void setPrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price must not be null.");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must not be negative.");
        }
        this.price = price.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Sets the stock quantity directly (used by the repository on load).
     * Callers should prefer {@link #addStock(int)} and {@link #reduceStock(int)}
     * for transactional changes.
     *
     * @param stockQuantity new stock level — must be &gt;= 0
     */
    public void setStockQuantity(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity must not be negative.");
        }
        this.stockQuantity = stockQuantity;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    // -------------------------------------------------------------------------
    // Stock management helpers
    // -------------------------------------------------------------------------

    /**
     * Increases stock by the given positive amount.
     *
     * @param amount units to add — must be &gt; 0
     */
    public void addStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Restock amount must be positive.");
        }
        this.stockQuantity += amount;
    }

    /**
     * Decreases stock by the given amount if sufficient stock exists.
     *
     * @param amount units to deduct — must be &gt; 0
     * @throws IllegalStateException    if stock would go below zero
     * @throws IllegalArgumentException if amount is not positive
     */
    public void reduceStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount to reduce must be positive.");
        }
        if (amount > this.stockQuantity) {
            throw new IllegalStateException(
                    "Insufficient stock for '" + name + "'. Available: "
                    + stockQuantity + ", requested: " + amount + ".");
        }
        this.stockQuantity -= amount;
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("MenuItem{id='%s', name='%s', category='%s', "
                + "price=%s, stock=%d, available=%b}",
                id, name, category, price.toPlainString(), stockQuantity, available);
    }
}
