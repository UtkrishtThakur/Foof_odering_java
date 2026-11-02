package com.foodordering.repository;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.OrderNotFoundException;
import com.foodordering.model.Order;
import com.foodordering.model.OrderStatus;
import com.foodordering.persistence.CsvOrderStorage;
import com.foodordering.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for {@link Order} records with optional CSV file persistence.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Stores and retrieves orders keyed by unique order ID.</li>
 *   <li>Preserves order insertion sequence using a {@link LinkedHashMap}.</li>
 *   <li>Enforces ID uniqueness on save.</li>
 *   <li>Provides query operations by ID, customer ID, and lifecycle status.</li>
 *   <li>Returns safe, unmodifiable collection views or defensive copies.</li>
 *   <li>Coordinates in-memory state with persistent CSV storage via {@link CsvOrderStorage}.</li>
 *   <li>Performs in-memory rollback if persistence operations fail.</li>
 * </ul>
 * </p>
 */
public class OrderRepository {

    private final Map<String, Order> store = new LinkedHashMap<>();
    private final CsvOrderStorage storage;

    /**
     * Constructs an in-memory-only OrderRepository without persistent storage.
     */
    public OrderRepository() {
        this(null);
    }

    /**
     * Constructs an OrderRepository backed by the specified CSV storage.
     * Automatically loads existing orders and snapshots from disk on startup if available.
     *
     * @param storage the CSV order storage (may be null for in-memory mode)
     */
    public OrderRepository(CsvOrderStorage storage) {
        this.storage = storage;
        if (this.storage != null) {
            loadFromStorage();
        }
    }

    /**
     * Reloads all orders and snapshots from the backing CSV storage into the in-memory map.
     */
    public void loadFromStorage() {
        if (this.storage != null) {
            this.store.clear();
            List<Order> loaded = this.storage.load();
            for (Order order : loaded) {
                this.store.put(order.getOrderId(), order);
            }
        }
    }

    /**
     * Returns the backing CSV storage, or {@code null} if running in memory only.
     *
     * @return backing storage
     */
    public CsvOrderStorage getStorage() {
        return storage;
    }

    private void persist() throws PersistenceException {
        if (storage != null) {
            storage.saveAll(store.values());
        }
    }

    /**
     * Saves a new order into the repository and persists it to CSV.
     *
     * @param order the order to store; must not be null
     * @return the saved {@link Order}
     * @throws InvalidInputException  if {@code order} is null
     * @throws DuplicateItemException if an order with the same ID already exists
     * @throws PersistenceException     if file writing fails (in-memory change is rolled back)
     */
    public Order save(Order order) {
        if (order == null) {
            throw new InvalidInputException("Order must not be null.");
        }
        if (store.containsKey(order.getOrderId())) {
            throw new DuplicateItemException(
                    "An order with ID '" + order.getOrderId() + "' already exists.");
        }
        store.put(order.getOrderId(), order);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.remove(order.getOrderId()); // Rollback in-memory
            throw ex;
        }
        return order;
    }

    /**
     * Replaces the stored order with the updated order instance and persists the update.
     *
     * @param order the updated order; its ID must already exist
     * @throws InvalidInputException  if {@code order} is null
     * @throws OrderNotFoundException if no order with that ID exists
     * @throws PersistenceException     if file writing fails (in-memory change is rolled back)
     */
    public void update(Order order) {
        if (order == null) {
            throw new InvalidInputException("Order must not be null.");
        }
        if (!store.containsKey(order.getOrderId())) {
            throw new OrderNotFoundException(
                    "Cannot update: no order found with ID '" + order.getOrderId() + "'.");
        }
        Order previous = store.get(order.getOrderId());
        store.put(order.getOrderId(), order);
        try {
            persist();
        } catch (PersistenceException ex) {
            store.put(previous.getOrderId(), previous); // Rollback in-memory
            throw ex;
        }
    }

    /**
     * Finds an order by its unique ID.
     *
     * @param orderId the order ID; must not be null or blank
     * @return the matching {@link Order}
     * @throws InvalidInputException  if {@code orderId} is null or blank
     * @throws OrderNotFoundException if no order with that ID exists
     */
    public Order findById(String orderId) {
        validateId(orderId);
        Order order = store.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException("No order found with ID '" + orderId + "'.");
        }
        return order;
    }

    /**
     * Finds an optional order by its ID without throwing if not found.
     *
     * @param orderId the order ID
     * @return {@link Optional} containing the order if present
     */
    public Optional<Order> findOptionalById(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(orderId));
    }

    /**
     * Returns all orders placed by the specified customer, preserving insertion order.
     *
     * @param customerId the customer ID; must not be null or blank
     * @return unmodifiable list of matching orders (empty if none found)
     * @throws InvalidInputException if {@code customerId} is null or blank
     */
    public List<Order> findByCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidInputException("Customer ID must not be null or blank.");
        }
        List<Order> customerOrders = new ArrayList<>();
        for (Order order : store.values()) {
            if (order.getCustomerId().equalsIgnoreCase(customerId.strip())) {
                customerOrders.add(order);
            }
        }
        return Collections.unmodifiableList(customerOrders);
    }

    /**
     * Returns all orders currently matching the specified lifecycle status.
     *
     * @param status the order status filter; must not be null
     * @return unmodifiable list of matching orders
     * @throws InvalidInputException if {@code status} is null
     */
    public List<Order> findByStatus(OrderStatus status) {
        if (status == null) {
            throw new InvalidInputException("Order status must not be null.");
        }
        List<Order> matchingOrders = new ArrayList<>();
        for (Order order : store.values()) {
            if (order.getStatus() == status) {
                matchingOrders.add(order);
            }
        }
        return Collections.unmodifiableList(matchingOrders);
    }

    /**
     * Returns an unmodifiable list of all stored orders in insertion sequence.
     *
     * @return unmodifiable list of all orders
     */
    public List<Order> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    /**
     * Returns whether an order exists with the given ID.
     *
     * @param orderId the order ID
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean existsById(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        return store.containsKey(orderId);
    }

    /**
     * Returns the total count of stored orders.
     *
     * @return order count
     */
    public int count() {
        return store.size();
    }

    /**
     * Clears all stored orders (useful for testing).
     */
    public void clear() {
        store.clear();
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new InvalidInputException("Order ID must not be null or blank.");
        }
    }
}
