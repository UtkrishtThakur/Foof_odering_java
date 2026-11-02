package com.foodordering.service;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.model.MenuItem;
import com.foodordering.repository.MenuRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business-logic layer for menu management.
 *
 * <p>This class owns all validation and rules that sit above raw data
 * storage. It delegates storage concerns to {@link MenuRepository} and
 * is the single entry point for anything the UI or other services need
 * to do with menu items.
 *
 * <p>Separation of concerns:
 * <ul>
 *   <li>{@link MenuRepository} — stores and retrieves data.</li>
 *   <li>{@link MenuService}    — enforces business rules.</li>
 *   <li>UI / Main             — presents results and handles user input.</li>
 * </ul>
 */
public class MenuService {

    private final MenuRepository menuRepository;

    /**
     * Constructs a MenuService backed by the given repository.
     *
     * @param menuRepository the repository to delegate storage to — must not be null
     */
    public MenuService(MenuRepository menuRepository) {
        if (menuRepository == null) {
            throw new IllegalArgumentException("MenuRepository must not be null.");
        }
        this.menuRepository = menuRepository;
    }

    // -------------------------------------------------------------------------
    // Add
    // -------------------------------------------------------------------------

    /**
     * Creates and stores a new menu item.
     *
     * @param id            unique identifier — must not be null or blank
     * @param name          display name — must not be null or blank
     * @param category      menu category — must not be null or blank
     * @param price         unit price — must not be null and must be &gt;= 0
     * @param stockQuantity initial stock — must be &gt;= 0
     * @param available     whether the item is immediately visible to customers
     * @return the newly created {@link MenuItem}
     * @throws InvalidInputException  if any input fails validation
     * @throws DuplicateItemException if an item with the given ID already exists
     */
    public MenuItem addItem(String id, String name, String category,
                            BigDecimal price, int stockQuantity, boolean available) {
        validateItemFields(id, name, category, price, stockQuantity);

        MenuItem item = new MenuItem(id, name, category, price, stockQuantity, available);
        menuRepository.add(item);
        return item;
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Updates the name of an existing menu item.
     *
     * @param id      item ID
     * @param newName new name — must not be null or blank
     * @throws ItemNotFoundException if no item with that ID exists
     * @throws InvalidInputException if {@code newName} is blank
     */
    public void updateName(String id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new InvalidInputException("Item name must not be null or blank.");
        }
        MenuItem item = requireItem(id);
        item.setName(newName.strip());
        menuRepository.update(item);
    }

    /**
     * Updates the category of an existing menu item.
     *
     * @param id          item ID
     * @param newCategory new category — must not be null or blank
     * @throws ItemNotFoundException if no item with that ID exists
     * @throws InvalidInputException if {@code newCategory} is blank
     */
    public void updateCategory(String id, String newCategory) {
        if (newCategory == null || newCategory.isBlank()) {
            throw new InvalidInputException("Category must not be null or blank.");
        }
        MenuItem item = requireItem(id);
        item.setCategory(newCategory.strip());
        menuRepository.update(item);
    }

    /**
     * Updates the price of an existing menu item.
     *
     * @param id       item ID
     * @param newPrice new price — must not be null and must be &gt;= 0
     * @throws ItemNotFoundException if no item with that ID exists
     * @throws InvalidInputException if {@code newPrice} is null or negative
     */
    public void updatePrice(String id, BigDecimal newPrice) {
        if (newPrice == null) {
            throw new InvalidInputException("Price must not be null.");
        }
        if (newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Price must not be negative.");
        }
        MenuItem item = requireItem(id);
        item.setPrice(newPrice);
        menuRepository.update(item);
    }

    /**
     * Sets the stock quantity of an existing menu item directly.
     * Prefer {@link #restockItem(String, int)} for normal replenishment.
     *
     * @param id       item ID
     * @param newStock new stock level — must be &gt;= 0
     * @throws ItemNotFoundException if no item with that ID exists
     * @throws InvalidInputException if {@code newStock} is negative
     */
    public void updateStock(String id, int newStock) {
        if (newStock < 0) {
            throw new InvalidInputException("Stock quantity must not be negative.");
        }
        MenuItem item = requireItem(id);
        item.setStockQuantity(newStock);
        menuRepository.update(item);
    }

    /**
     * Updates the availability flag of an existing menu item.
     *
     * @param id        item ID
     * @param available {@code true} to make the item visible to customers
     * @throws ItemNotFoundException if no item with that ID exists
     */
    public void updateAvailability(String id, boolean available) {
        MenuItem item = requireItem(id);
        item.setAvailable(available);
        menuRepository.update(item);
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    /**
     * Permanently removes an item from the menu.
     *
     * @param id item ID to remove
     * @throws ItemNotFoundException if no item with that ID exists
     */
    public void removeItem(String id) {
        // Delegates — repository will throw ItemNotFoundException if absent
        menuRepository.remove(id);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Finds a menu item by its unique ID.
     *
     * @param id item ID
     * @return the matching {@link MenuItem}
     * @throws ItemNotFoundException if no item with that ID exists
     */
    public MenuItem findById(String id) {
        return menuRepository.findById(id);
    }

    /**
     * Returns all menu items in insertion order.
     *
     * @return unmodifiable list of all items
     */
    public List<MenuItem> listAll() {
        return menuRepository.findAll();
    }

    /**
     * Searches for items whose name contains the given keyword
     * (case-insensitive).
     *
     * @param keyword the search term
     * @return list of matching items (may be empty)
     * @throws InvalidInputException if {@code keyword} is null or blank
     */
    public List<MenuItem> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new InvalidInputException("Search keyword must not be null or blank.");
        }
        return menuRepository.searchByName(keyword);
    }

    /**
     * Returns all items that belong to the given category (case-insensitive).
     *
     * @param category the category to filter by
     * @return list of matching items (may be empty)
     * @throws InvalidInputException if {@code category} is null or blank
     */
    public List<MenuItem> filterByCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new InvalidInputException("Category must not be null or blank.");
        }
        return menuRepository.filterByCategory(category);
    }

    /**
     * Returns only items that are currently marked as available.
     *
     * @return list of available items (may be empty)
     */
    public List<MenuItem> listAvailable() {
        return menuRepository.findAvailable();
    }

    // -------------------------------------------------------------------------
    // Stock operations
    // -------------------------------------------------------------------------

    /**
     * Adds a positive quantity to the stock of an existing item.
     *
     * @param id     item ID
     * @param amount units to add — must be &gt; 0
     * @throws ItemNotFoundException if no item with that ID exists
     * @throws InvalidInputException if {@code amount} is not positive
     */
    public void restockItem(String id, int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("Restock amount must be a positive number.");
        }
        MenuItem item = requireItem(id);
        item.addStock(amount);
        menuRepository.update(item);
    }

    /**
     * Reduces the stock of an existing item by the given amount.
     * Stock must never fall below zero.
     *
     * @param id     item ID
     * @param amount units to deduct — must be &gt; 0
     * @throws ItemNotFoundException    if no item with that ID exists
     * @throws InvalidInputException    if {@code amount} is not positive
     * @throws InsufficientStockException if there is not enough stock
     */
    public void reduceStock(String id, int amount) {
        if (amount <= 0) {
            throw new InvalidInputException("Amount to reduce must be a positive number.");
        }
        MenuItem item = requireItem(id);
        try {
            item.reduceStock(amount); // model enforces the zero-floor invariant
        } catch (IllegalStateException ex) {
            // Translate model-level exception to a named domain exception
            throw new InsufficientStockException(ex.getMessage());
        }
        menuRepository.update(item);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Retrieves an item by ID or throws {@link ItemNotFoundException}.
     * Centralises the lookup so every method avoids repeating the check.
     */
    private MenuItem requireItem(String id) {
        // Let repository throw ItemNotFoundException if not found
        return menuRepository.findById(id);
    }

    /**
     * Validates the common fields used when creating a new menu item.
     * Throws {@link InvalidInputException} on the first violation found.
     */
    private void validateItemFields(String id, String name, String category,
                                    BigDecimal price, int stockQuantity) {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("Item ID must not be null or blank.");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Item name must not be null or blank.");
        }
        if (category == null || category.isBlank()) {
            throw new InvalidInputException("Category must not be null or blank.");
        }
        if (price == null) {
            throw new InvalidInputException("Price must not be null.");
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Price must not be negative.");
        }
        if (stockQuantity < 0) {
            throw new InvalidInputException("Stock quantity must not be negative.");
        }
    }
}
