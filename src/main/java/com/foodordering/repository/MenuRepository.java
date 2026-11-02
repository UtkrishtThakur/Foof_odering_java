package com.foodordering.repository;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.model.MenuItem;
import com.foodordering.persistence.CsvMenuStorage;
import com.foodordering.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for {@link MenuItem} objects with optional CSV file persistence.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Storing and retrieving menu items keyed by their unique ID.</li>
 *   <li>Enforcing ID uniqueness.</li>
 *   <li>Providing query operations (by ID, by name, by category, by availability).</li>
 *   <li>Coordinating in-memory state with persistent CSV storage via {@link CsvMenuStorage}.</li>
 *   <li>Performing in-memory rollback if persistence operations fail.</li>
 * </ul>
 * </p>
 */
public class MenuRepository {

    // LinkedHashMap preserves insertion order for predictable display
    private final Map<String, MenuItem> store = new LinkedHashMap<>();
    private final CsvMenuStorage storage;

    /**
     * Constructs an in-memory-only MenuRepository without file persistence.
     */
    public MenuRepository() {
        this(null);
    }

    /**
     * Constructs a MenuRepository backed by the specified CSV storage.
     * Automatically loads existing menu items from disk on startup if available.
     *
     * @param storage the CSV menu storage (may be null for in-memory mode)
     */
    public MenuRepository(CsvMenuStorage storage) {
        this.storage = storage;
        if (this.storage != null) {
            loadFromStorage();
        }
    }

    /**
     * Reloads all menu items from the backing CSV storage into the in-memory map.
     */
    public void loadFromStorage() {
        if (this.storage != null) {
            this.store.clear();
            List<MenuItem> loaded = this.storage.load();
            for (MenuItem item : loaded) {
                this.store.put(item.getId(), item);
            }
        }
    }

    /**
     * Returns the backing CSV storage, or {@code null} if running in memory only.
     *
     * @return backing storage
     */
    public CsvMenuStorage getStorage() {
        return storage;
    }

    private void persist() throws PersistenceException {
        if (storage != null) {
            storage.saveAll(store.values());
        }
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Adds a new menu item to the repository and persists the change.
     *
     * @param item the item to add — must not be null
     * @throws IllegalArgumentException if {@code item} is null
     * @throws DuplicateItemException   if an item with the same ID already exists
     * @throws PersistenceException     if file writing fails (in-memory change is rolled back)
     */
    public void add(MenuItem item) {
        if (item == null) {
            throw new IllegalArgumentException("MenuItem must not be null.");
        }
        if (store.containsKey(item.getId())) {
            throw new DuplicateItemException(
                    "A menu item with ID '" + item.getId() + "' already exists.");
        }
        store.put(item.getId(), item);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.remove(item.getId()); // Rollback in-memory
            throw ex;
        }
    }

    /**
     * Replaces the stored item with the given item (same ID required) and persists the change.
     *
     * @param item the updated item — its ID must already exist
     * @throws IllegalArgumentException if {@code item} is null
     * @throws ItemNotFoundException    if no item with that ID exists
     * @throws PersistenceException     if file writing fails (in-memory change is rolled back)
     */
    public void update(MenuItem item) {
        if (item == null) {
            throw new IllegalArgumentException("MenuItem must not be null.");
        }
        if (!store.containsKey(item.getId())) {
            throw new ItemNotFoundException(
                    "Cannot update: no menu item with ID '" + item.getId() + "' found.");
        }
        MenuItem previous = store.get(item.getId());
        store.put(item.getId(), item);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.put(previous.getId(), previous); // Rollback in-memory
            throw ex;
        }
    }

    /**
     * Removes the item with the given ID from the repository and persists the removal.
     *
     * @param id the item ID to remove — must not be null or blank
     * @throws IllegalArgumentException if {@code id} is null or blank
     * @throws ItemNotFoundException    if no item with that ID exists
     * @throws PersistenceException     if file writing fails (in-memory change is rolled back)
     */
    public void remove(String id) {
        validateId(id);
        if (!store.containsKey(id)) {
            throw new ItemNotFoundException(
                    "Cannot remove: no menu item with ID '" + id + "' found.");
        }
        MenuItem previous = store.remove(id);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.put(id, previous); // Rollback in-memory
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Returns the item with the given ID.
     *
     * @param id the item ID — must not be null or blank
     * @return the matching {@link MenuItem}
     * @throws IllegalArgumentException if {@code id} is null or blank
     * @throws ItemNotFoundException    if no item with that ID exists
     */
    public MenuItem findById(String id) {
        validateId(id);
        MenuItem item = store.get(id);
        if (item == null) {
            throw new ItemNotFoundException(
                    "No menu item found with ID '" + id + "'.");
        }
        return item;
    }

    /**
     * Returns an unmodifiable view of all items, preserving insertion order.
     *
     * @return unmodifiable list of all {@link MenuItem} objects
     */
    public List<MenuItem> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    /**
     * Returns items whose name contains the given keyword,
     * case-insensitively.
     *
     * @param keyword the search term — must not be null or blank
     * @return list of matching items (may be empty, never null)
     * @throws IllegalArgumentException if {@code keyword} is null or blank
     */
    public List<MenuItem> searchByName(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Search keyword must not be null or blank.");
        }
        String lower = keyword.strip().toLowerCase();
        List<MenuItem> results = new ArrayList<>();
        for (MenuItem item : store.values()) {
            if (item.getName().toLowerCase().contains(lower)) {
                results.add(item);
            }
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns items belonging to the given category, case-insensitively.
     *
     * @param category the category to filter by — must not be null or blank
     * @return list of matching items (may be empty, never null)
     * @throws IllegalArgumentException if {@code category} is null or blank
     */
    public List<MenuItem> filterByCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category must not be null or blank.");
        }
        String lower = category.strip().toLowerCase();
        List<MenuItem> results = new ArrayList<>();
        for (MenuItem item : store.values()) {
            if (item.getCategory().toLowerCase().equals(lower)) {
                results.add(item);
            }
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns only items whose {@code available} flag is {@code true}.
     *
     * @return unmodifiable list of available items (may be empty, never null)
     */
    public List<MenuItem> findAvailable() {
        List<MenuItem> results = new ArrayList<>();
        for (MenuItem item : store.values()) {
            if (item.isAvailable()) {
                results.add(item);
            }
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Returns {@code true} if an item with the given ID already exists.
     *
     * @param id item ID to check
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean existsById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return store.containsKey(id);
    }

    /**
     * Returns the total number of items currently stored.
     *
     * @return item count
     */
    public int count() {
        return store.size();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Validates that an ID argument is non-null and non-blank. */
    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item ID must not be null or blank.");
        }
    }
}
