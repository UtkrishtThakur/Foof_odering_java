package com.foodordering.service;

import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.model.Cart;
import com.foodordering.model.CartItem;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Business-logic layer for shopping cart management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validating all inputs before delegating mutations to {@link Cart}.</li>
 *   <li>Verifying menu-item existence, availability, and sufficient stock
 *       via {@link MenuService} — never duplicating that logic.</li>
 *   <li>Ensuring cart invariants (positive quantities, no phantom entries) are
 *       upheld without touching menu inventory.</li>
 * </ul>
 *
 * <p>What this service does <em>not</em> do:
 * <ul>
 *   <li>It does <em>not</em> reduce or reserve stock — that happens at checkout.</li>
 *   <li>It does <em>not</em> apply discounts, taxes, or delivery fees.</li>
 *   <li>It does <em>not</em> persist data — storage is a later part.</li>
 * </ul>
 *
 * <p>Separation of concerns:
 * <pre>
 *   Cart      — pure data container, lightweight invariant checks only
 *   CartService — all business rules and cross-object validation
 *   MenuService — single source of truth for menu items and stock
 * </pre>
 */
public class CartService {

    private final MenuService menuService;

    /**
     * Constructs a CartService that delegates menu lookups to the given service.
     *
     * @param menuService the menu service to use — must not be null
     * @throws IllegalArgumentException if {@code menuService} is null
     */
    public CartService(MenuService menuService) {
        if (menuService == null) {
            throw new IllegalArgumentException("MenuService must not be null.");
        }
        this.menuService = menuService;
    }

    // -------------------------------------------------------------------------
    // Cart lifecycle
    // -------------------------------------------------------------------------

    /**
     * Creates a new, empty cart for the given customer.
     *
     * @param customer the owning customer — must not be null
     * @return a fresh, empty {@link Cart}
     * @throws InvalidInputException if {@code customer} is null
     */
    public Cart createCart(Customer customer) {
        if (customer == null) {
            throw new InvalidInputException("Customer must not be null.");
        }
        return new Cart(customer);
    }

    // -------------------------------------------------------------------------
    // Add
    // -------------------------------------------------------------------------

    /**
     * Adds the specified quantity of a menu item to the cart.
     *
     * <p>If the item is already in the cart, the quantities are merged.
     * The combined quantity is validated against current stock before any
     * change is made.
     *
     * @param cart       the customer's cart — must not be null
     * @param menuItemId the ID of the menu item to add — must not be null or blank
     * @param quantity   number of units to add — must be &gt; 0
     * @throws InvalidInputException      if inputs are null, blank, or non-positive
     * @throws ItemNotFoundException      if no menu item with {@code menuItemId} exists
     * @throws InvalidInputException      if the menu item is currently unavailable
     * @throws InsufficientStockException if the combined cart quantity would exceed stock
     */
    public void addItem(Cart cart, String menuItemId, int quantity) {
        requireCart(cart);
        requirePositiveQuantity(quantity);

        MenuItem menuItem = requireAvailableMenuItem(menuItemId);

        // Determine the total quantity that will be in the cart after this operation
        int alreadyInCart = 0;
        if (cart.containsItem(menuItemId)) {
            alreadyInCart = cart.getItems().get(menuItemId).getQuantity();
        }
        int totalRequested = alreadyInCart + quantity;
        requireSufficientStock(menuItem, totalRequested);

        cart.addItem(menuItem, quantity);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Replaces the quantity of an item already in the cart.
     *
     * <p>The item is re-validated against the menu on every update so that
     * stale entries (e.g., item removed from menu between adds) are caught.
     *
     * @param cart       the customer's cart — must not be null
     * @param menuItemId the ID of the cart item to update — must not be null or blank
     * @param quantity   the new quantity — must be &gt; 0
     * @throws InvalidInputException      if inputs are invalid
     * @throws ItemNotFoundException      if the item is not in the cart, or no longer on the menu
     * @throws InvalidInputException      if the menu item is currently unavailable
     * @throws InsufficientStockException if {@code quantity} exceeds available stock
     */
    public void updateQuantity(Cart cart, String menuItemId, int quantity) {
        requireCart(cart);
        requirePositiveQuantity(quantity);

        // Item must already be in the cart
        requireItemInCart(cart, menuItemId);

        // Item must still be on the menu and available
        MenuItem menuItem = requireAvailableMenuItem(menuItemId);

        requireSufficientStock(menuItem, quantity);

        cart.updateQuantity(menuItemId, quantity);
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    /**
     * Removes an item from the cart entirely.
     * Menu inventory is not affected.
     *
     * @param cart       the customer's cart — must not be null
     * @param menuItemId the ID of the cart item to remove — must not be null or blank
     * @throws InvalidInputException if inputs are invalid
     * @throws ItemNotFoundException if the item is not currently in the cart
     */
    public void removeItem(Cart cart, String menuItemId) {
        requireCart(cart);
        if (menuItemId == null || menuItemId.isBlank()) {
            throw new InvalidInputException("Menu item ID must not be null or blank.");
        }
        requireItemInCart(cart, menuItemId);
        cart.removeItem(menuItemId);
    }

    // -------------------------------------------------------------------------
    // View
    // -------------------------------------------------------------------------

    /**
     * Returns the collection of cart items for display purposes.
     * The returned collection is unmodifiable.
     *
     * @param cart the cart to inspect — must not be null
     * @return unmodifiable collection of {@link CartItem} objects
     * @throws InvalidInputException if {@code cart} is null
     */
    public Collection<CartItem> viewCart(Cart cart) {
        requireCart(cart);
        return cart.getItems().values(); // already unmodifiable via Cart.getItems()
    }

    // -------------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------------

    /**
     * Removes all items from the cart.
     * Menu inventory is not affected.
     *
     * @param cart the cart to clear — must not be null
     * @throws InvalidInputException if {@code cart} is null
     */
    public void clearCart(Cart cart) {
        requireCart(cart);
        cart.clear();
    }

    // -------------------------------------------------------------------------
    // Subtotal
    // -------------------------------------------------------------------------

    /**
     * Calculates and returns the cart subtotal (sum of all line subtotals).
     * Returns {@link BigDecimal#ZERO} for an empty cart.
     * No discounts, fees, or taxes are applied.
     *
     * @param cart the cart to evaluate — must not be null
     * @return subtotal as a {@link BigDecimal}; never null
     * @throws InvalidInputException if {@code cart} is null
     */
    public BigDecimal calculateSubtotal(Cart cart) {
        requireCart(cart);
        return cart.calculateSubtotal();
    }

    // -------------------------------------------------------------------------
    // Internal guard methods — keep each rule in one place
    // -------------------------------------------------------------------------

    /** Ensures the cart argument is non-null. */
    private void requireCart(Cart cart) {
        if (cart == null) {
            throw new InvalidInputException("Cart must not be null.");
        }
    }

    /** Ensures the quantity is strictly positive. */
    private void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InvalidInputException("Quantity must be a positive number.");
        }
    }

    /**
     * Retrieves the menu item by ID and ensures it is both present and available.
     *
     * @throws ItemNotFoundException if no menu item with that ID exists
     * @throws InvalidInputException if the item exists but is currently unavailable
     */
    private MenuItem requireAvailableMenuItem(String menuItemId) {
        if (menuItemId == null || menuItemId.isBlank()) {
            throw new InvalidInputException("Menu item ID must not be null or blank.");
        }
        // MenuService.findById throws ItemNotFoundException if absent
        MenuItem menuItem = menuService.findById(menuItemId);
        if (!menuItem.isAvailable()) {
            throw new InvalidInputException(
                    "Menu item '" + menuItem.getName() + "' (ID: " + menuItemId
                    + ") is not currently available.");
        }
        return menuItem;
    }

    /**
     * Ensures the item identified by {@code menuItemId} is already in the cart.
     *
     * @throws ItemNotFoundException if the item is absent from the cart
     */
    private void requireItemInCart(Cart cart, String menuItemId) {
        if (!cart.containsItem(menuItemId)) {
            throw new ItemNotFoundException(
                    "Item with ID '" + menuItemId + "' is not in the cart.");
        }
    }

    /**
     * Ensures that {@code requestedQty} does not exceed the item's current stock.
     *
     * @throws InsufficientStockException if stock is insufficient
     */
    private void requireSufficientStock(MenuItem menuItem, int requestedQty) {
        if (requestedQty > menuItem.getStockQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for '" + menuItem.getName()
                    + "'. Available: " + menuItem.getStockQuantity()
                    + ", requested: " + requestedQty + ".");
        }
    }
}
