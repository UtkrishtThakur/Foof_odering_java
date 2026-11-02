package com.foodordering.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents one line in a customer's shopping cart: a menu item paired with
 * the quantity the customer intends to purchase.
 *
 * <p>Design notes:
 * <ul>
 *   <li>The {@link MenuItem} reference is kept live — the subtotal reflects the
 *       item's current menu price. Price snapshots will be taken at the order/checkout
 *       stage (a later part of the project).</li>
 *   <li>The contained {@link MenuItem} object is not defensively copied because
 *       {@link Cart} and {@link com.foodordering.service.CartService} are responsible
 *       for ensuring that only valid, current items enter the cart.</li>
 *   <li>Quantity must always be &gt; 0; {@link #setQuantity(int)} enforces this.</li>
 * </ul>
 */
public class CartItem {

    private final MenuItem menuItem;
    private int quantity;

    /**
     * Constructs a CartItem for the given menu item and initial quantity.
     *
     * @param menuItem the menu item to add — must not be null
     * @param quantity the number of units — must be &gt; 0
     * @throws IllegalArgumentException if {@code menuItem} is null or
     *                                  {@code quantity} is not positive
     */
    public CartItem(MenuItem menuItem, int quantity) {
        if (menuItem == null) {
            throw new IllegalArgumentException("MenuItem must not be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        this.menuItem = menuItem;
        this.quantity = quantity;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** Returns the menu item this cart line refers to. */
    public MenuItem getMenuItem() {
        return menuItem;
    }

    /** Returns the quantity currently in the cart for this line. */
    public int getQuantity() {
        return quantity;
    }

    // -------------------------------------------------------------------------
    // Quantity update
    // -------------------------------------------------------------------------

    /**
     * Replaces the quantity for this cart line.
     *
     * @param quantity the new quantity — must be &gt; 0
     * @throws IllegalArgumentException if {@code quantity} is not positive
     */
    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        this.quantity = quantity;
    }

    // -------------------------------------------------------------------------
    // Subtotal
    // -------------------------------------------------------------------------

    /**
     * Calculates the line subtotal as {@code unitPrice × quantity}.
     * Uses the menu item's current price; no discount or tax is applied here.
     *
     * @return the line subtotal as a {@link BigDecimal}; never null
     */
    public BigDecimal getSubtotal() {
        return menuItem.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("CartItem{itemId='%s', name='%s', qty=%d, unitPrice=%s, subtotal=%s}",
                menuItem.getId(),
                menuItem.getName(),
                quantity,
                menuItem.getPrice().toPlainString(),
                getSubtotal().toPlainString());
    }
}
