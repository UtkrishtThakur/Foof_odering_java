package com.foodordering.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single customer's in-progress shopping cart.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Cart items are stored in a {@link LinkedHashMap} keyed by menu item ID
 *       so that insertion order is preserved and lookup is O(1).</li>
 *   <li>Adding an item that is already present <em>merges</em> the quantities
 *       rather than creating a duplicate entry.</li>
 *   <li>All collection-returning methods provide unmodifiable views; callers
 *       must go through this class's own mutating methods.</li>
 *   <li>The cart is bound to a {@link Customer} via the customer's ID so that
 *       it can be associated with an order later without creating a circular
 *       object reference.</li>
 *   <li>Stock validation is <em>not</em> performed here — that is the
 *       responsibility of {@link com.foodordering.service.CartService}.</li>
 * </ul>
 */
public class Cart {

    private final String customerId;
    // Key: menu item ID — preserves insertion order
    private final Map<String, CartItem> items = new LinkedHashMap<>();

    /**
     * Constructs an empty cart owned by the given customer.
     *
     * @param customer the owning customer — must not be null
     * @throws IllegalArgumentException if {@code customer} is null
     */
    public Cart(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer must not be null.");
        }
        this.customerId = customer.getUserId();
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /** Returns the ID of the customer who owns this cart. */
    public String getCustomerId() {
        return customerId;
    }

    // -------------------------------------------------------------------------
    // Mutating operations — called by CartService only
    // -------------------------------------------------------------------------

    /**
     * Adds a new cart line for the given item and quantity.
     * If the item is already present, its quantity is <em>increased</em>
     * by {@code quantity}; no duplicate entry is created.
     *
     * <p>Callers are responsible for validating stock availability before
     * invoking this method.
     *
     * @param menuItem the item to add — must not be null
     * @param quantity units to add — must be &gt; 0
     * @throws IllegalArgumentException if {@code menuItem} is null or
     *                                  {@code quantity} is not positive
     */
    public void addItem(MenuItem menuItem, int quantity) {
        if (menuItem == null) {
            throw new IllegalArgumentException("MenuItem must not be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        CartItem existing = items.get(menuItem.getId());
        if (existing != null) {
            // Merge: increase quantity of the existing cart line
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            items.put(menuItem.getId(), new CartItem(menuItem, quantity));
        }
    }

    /**
     * Replaces the quantity for an existing cart line.
     * The item <em>must</em> already be present in the cart.
     *
     * <p>Callers are responsible for stock validation before calling this.
     *
     * @param menuItemId the ID of the cart item to update
     * @param quantity   the new quantity — must be &gt; 0
     * @throws IllegalArgumentException if {@code quantity} is not positive or
     *                                  the item is not in the cart
     */
    public void updateQuantity(String menuItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        CartItem cartItem = items.get(menuItemId);
        if (cartItem == null) {
            throw new IllegalArgumentException(
                    "Item with ID '" + menuItemId + "' is not in the cart.");
        }
        cartItem.setQuantity(quantity);
    }

    /**
     * Removes the cart line for the given menu item ID.
     *
     * @param menuItemId the ID of the cart item to remove
     * @throws IllegalArgumentException if the item is not in the cart
     */
    public void removeItem(String menuItemId) {
        if (!items.containsKey(menuItemId)) {
            throw new IllegalArgumentException(
                    "Item with ID '" + menuItemId + "' is not in the cart.");
        }
        items.remove(menuItemId);
    }

    /** Removes every item from the cart. */
    public void clear() {
        items.clear();
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Returns whether an item with the given menu item ID is currently in the cart.
     *
     * @param menuItemId the ID to check
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean containsItem(String menuItemId) {
        return items.containsKey(menuItemId);
    }

    /**
     * Returns an unmodifiable view of the cart's items keyed by menu item ID.
     * Callers must not attempt to cast the result to a mutable type.
     *
     * @return unmodifiable map of cart items
     */
    public Map<String, CartItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /** Returns {@code true} if the cart has no items. */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Returns the number of distinct menu items currently in the cart. */
    public int lineCount() {
        return items.size();
    }

    /**
     * Calculates the cart subtotal by summing each line's subtotal.
     * Returns {@link BigDecimal#ZERO} for an empty cart.
     * No discounts, fees, or taxes are applied.
     *
     * @return subtotal as a {@link BigDecimal}; never null
     */
    public BigDecimal calculateSubtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : items.values()) {
            total = total.add(cartItem.getSubtotal());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Display
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("Cart{customerId='%s', lines=%d, subtotal=%s}",
                customerId, items.size(), calculateSubtotal().toPlainString());
    }
}
