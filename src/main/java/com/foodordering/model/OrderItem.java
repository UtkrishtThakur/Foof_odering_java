package com.foodordering.model;

import com.foodordering.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents an immutable historical snapshot of a purchased item in an order.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Stores purchase-time snapshot values (item ID, name, unit price, quantity, and line subtotal).</li>
 *   <li>Does not store a live or mutable {@link MenuItem} reference, isolating order history
 *       from subsequent menu edits, price changes, or item deletions.</li>
 *   <li>All fields are {@code final} to ensure immutability once created.</li>
 *   <li>Line subtotal is computed as {@code unitPrice × quantity} with 2 decimal places and
 *       {@link RoundingMode#HALF_UP}.</li>
 * </ul>
 * </p>
 */
public class OrderItem {

    private static final int SCALE = 2;

    private final String menuItemId;
    private final String itemName;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal subtotal;

    /**
     * Constructs an immutable OrderItem snapshot.
     *
     * @param menuItemId unique identifier of the menu item; must not be null or blank
     * @param itemName   display name of the item at purchase time; must not be null or blank
     * @param unitPrice  unit price at purchase time; must be non-null and non-negative (&gt;= 0)
     * @param quantity   number of units purchased; must be strictly positive (&gt; 0)
     * @throws InvalidInputException if any argument violates validation constraints
     */
    public OrderItem(String menuItemId, String itemName, BigDecimal unitPrice, int quantity) {
        if (menuItemId == null || menuItemId.isBlank()) {
            throw new InvalidInputException("Menu item ID must not be null or blank.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new InvalidInputException("Item name must not be null or blank.");
        }
        if (unitPrice == null) {
            throw new InvalidInputException("Unit price must not be null.");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Unit price must not be negative: " + unitPrice.toPlainString());
        }
        if (quantity <= 0) {
            throw new InvalidInputException("Quantity must be positive (> 0), received: " + quantity);
        }

        this.menuItemId = menuItemId.strip();
        this.itemName = itemName.strip();
        this.unitPrice = unitPrice.setScale(SCALE, RoundingMode.HALF_UP);
        this.quantity = quantity;
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Factory method creating an OrderItem snapshot from a {@link MenuItem} and quantity.
     *
     * @param menuItem the menu item to snapshot; must not be null
     * @param quantity number of units; must be &gt; 0
     * @return a new immutable {@link OrderItem} snapshot
     */
    public static OrderItem from(MenuItem menuItem, int quantity) {
        if (menuItem == null) {
            throw new InvalidInputException("MenuItem must not be null.");
        }
        return new OrderItem(menuItem.getId(), menuItem.getName(), menuItem.getPrice(), quantity);
    }

    public String getMenuItemId() {
        return menuItemId;
    }

    public String getItemName() {
        return itemName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    @Override
    public String toString() {
        return String.format("OrderItem{id='%s', name='%s', unitPrice=$%s, qty=%d, subtotal=$%s}",
                menuItemId, itemName, unitPrice.toPlainString(), quantity, subtotal.toPlainString());
    }
}
