package com.foodordering.repository;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.model.User;
import com.foodordering.persistence.CsvUserStorage;
import com.foodordering.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for {@link User} entities (both {@link Customer} and {@link Admin})
 * with optional CSV file persistence.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Stores and retrieves users keyed by their unique user ID.</li>
 *   <li>Enforces uniqueness of user ID and email address.</li>
 *   <li>Provides query methods by ID, email, and role.</li>
 *   <li>Coordinates in-memory state with persistent CSV storage via {@link CsvUserStorage}.</li>
 *   <li>Performs in-memory rollback if persistence operations fail.</li>
 * </ul>
 * </p>
 */
public class UserRepository {

    private final Map<String, User> store = new LinkedHashMap<>();
    private final CsvUserStorage storage;

    /**
     * Constructs an in-memory-only UserRepository without file persistence.
     */
    public UserRepository() {
        this(null);
    }

    /**
     * Constructs a UserRepository backed by the specified CSV storage.
     * Automatically loads existing users from disk on startup if available.
     *
     * @param storage the CSV user storage (may be null for in-memory mode)
     */
    public UserRepository(CsvUserStorage storage) {
        this.storage = storage;
        if (this.storage != null) {
            loadFromStorage();
        }
    }

    /**
     * Reloads all users from the backing CSV storage into the in-memory map.
     */
    public void loadFromStorage() {
        if (this.storage != null) {
            this.store.clear();
            List<User> loaded = this.storage.load();
            for (User user : loaded) {
                this.store.put(user.getUserId(), user);
            }
        }
    }

    public CsvUserStorage getStorage() {
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
     * Saves a new user into the repository and persists the change.
     *
     * @param user user entity to store; must not be null
     * @return the saved user
     * @throws InvalidInputException  if user is null
     * @throws DuplicateItemException if user ID or email already exists
     * @throws PersistenceException   if file persistence fails (in-memory change is rolled back)
     */
    public User save(User user) {
        if (user == null) {
            throw new InvalidInputException("User must not be null.");
        }
        if (store.containsKey(user.getUserId())) {
            throw new DuplicateItemException(
                    "A user with ID '" + user.getUserId() + "' already exists.");
        }
        if (existsByEmail(user.getEmail())) {
            throw new DuplicateItemException(
                    "A user with email '" + user.getEmail() + "' already exists.");
        }

        store.put(user.getUserId(), user);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.remove(user.getUserId()); // Rollback in-memory
            throw ex;
        }
        return user;
    }

    /**
     * Replaces an existing user record and persists the change.
     *
     * @param user the updated user instance
     * @throws InvalidInputException if user is null or does not exist
     * @throws PersistenceException  if file persistence fails
     */
    public void update(User user) {
        if (user == null) {
            throw new InvalidInputException("User must not be null.");
        }
        if (!store.containsKey(user.getUserId())) {
            throw new InvalidInputException(
                    "Cannot update: no user found with ID '" + user.getUserId() + "'.");
        }

        User previous = store.get(user.getUserId());
        store.put(user.getUserId(), user);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.put(previous.getUserId(), previous); // Rollback in-memory
            throw ex;
        }
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Finds a user by unique ID.
     *
     * @param userId user ID
     * @return optional containing the user if found
     */
    public Optional<User> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(userId.strip()));
    }

    /**
     * Finds a user by email address (case-insensitive).
     *
     * @param email email address
     * @return optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String searchEmail = email.strip().toLowerCase();
        for (User user : store.values()) {
            if (user.getEmail().equalsIgnoreCase(searchEmail)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether a user exists with the specified ID.
     *
     * @param userId user ID
     * @return {@code true} if present
     */
    public boolean existsById(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return store.containsKey(userId.strip());
    }

    /**
     * Returns whether a user exists with the specified email (case-insensitive).
     *
     * @param email email address
     * @return {@code true} if present
     */
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    /**
     * Returns an unmodifiable list of all stored users.
     *
     * @return list of all users
     */
    public List<User> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    /**
     * Returns all registered customers.
     *
     * @return list of customers
     */
    public List<Customer> findCustomers() {
        List<Customer> customers = new ArrayList<>();
        for (User user : store.values()) {
            if (user instanceof Customer customer) {
                customers.add(customer);
            }
        }
        return Collections.unmodifiableList(customers);
    }

    /**
     * Returns all registered administrators.
     *
     * @return list of administrators
     */
    public List<Admin> findAdmins() {
        List<Admin> admins = new ArrayList<>();
        for (User user : store.values()) {
            if (user instanceof Admin admin) {
                admins.add(admin);
            }
        }
        return Collections.unmodifiableList(admins);
    }

    /**
     * Returns the total number of registered users.
     *
     * @return total count
     */
    public int count() {
        return store.size();
    }

    /**
     * Clears all users from in-memory store (for testing).
     */
    public void clear() {
        store.clear();
    }
}
