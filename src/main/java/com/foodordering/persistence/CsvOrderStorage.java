package com.foodordering.persistence;

import com.foodordering.model.Order;
import com.foodordering.model.OrderItem;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles CSV serialization and deserialization for {@link Order} records and their associated
 * historical {@link OrderItem} snapshots across two relational CSV files:
 * <ul>
 *   <li>{@code orders.csv}: order headers, financial totals, lifecycle statuses, timestamps, and payment refs.</li>
 *   <li>{@code order_items.csv}: purchased item snapshots linked to orders by {@code order_id}.</li>
 * </ul>
 */
public class CsvOrderStorage {

    public static final List<String> ORDERS_HEADER = List.of(
            "order_id", "customer_id", "status", "payment_status",
            "subtotal", "discount_amount", "final_total", "created_at", "transaction_reference");

    public static final List<String> ORDER_ITEMS_HEADER = List.of(
            "order_id", "menu_item_id", "item_name", "unit_price", "quantity", "subtotal");

    private final Path ordersFilePath;
    private final Path orderItemsFilePath;

    /**
     * Constructs a CsvOrderStorage with explicit paths for orders and items files.
     *
     * @param ordersFilePath     path to orders CSV
     * @param orderItemsFilePath path to order items CSV
     */
    public CsvOrderStorage(Path ordersFilePath, Path orderItemsFilePath) {
        if (ordersFilePath == null) {
            throw new PersistenceException("Orders CSV file path must not be null.");
        }
        if (orderItemsFilePath == null) {
            throw new PersistenceException("Order items CSV file path must not be null.");
        }
        this.ordersFilePath = ordersFilePath;
        this.orderItemsFilePath = orderItemsFilePath;
    }

    /**
     * Convenience constructor resolving {@code orders.csv} and {@code order_items.csv} within a directory.
     *
     * @param dataDir storage directory path
     */
    public CsvOrderStorage(Path dataDir) {
        this(dataDir.resolve("orders.csv"), dataDir.resolve("order_items.csv"));
    }

    public Path getOrdersFilePath() {
        return ordersFilePath;
    }

    public Path getOrderItemsFilePath() {
        return orderItemsFilePath;
    }

    /**
     * Loads and reconstructs all orders and their historical item snapshots from disk.
     *
     * @return unmodifiable list of reconstructed {@link Order} objects
     * @throws PersistenceException if files are malformed, missing required items, or inconsistent
     */
    public List<Order> load() throws PersistenceException {
        boolean ordersExist = Files.exists(ordersFilePath);
        boolean itemsExist = Files.exists(orderItemsFilePath);

        if (!ordersExist && !itemsExist) {
            return Collections.emptyList();
        }

        // 1. Load order item snapshots grouped by order_id
        Map<String, List<OrderItem>> itemsByOrderId = new LinkedHashMap<>();
        if (itemsExist) {
            List<List<String>> itemRows = readCsvFile(orderItemsFilePath);
            if (itemRows.size() > 1) {
                validateHeader(itemRows.get(0), ORDER_ITEMS_HEADER, orderItemsFilePath);
                for (int i = 1; i < itemRows.size(); i++) {
                    List<String> row = itemRows.get(i);
                    int rowNum = i + 1;
                    if (row.size() != ORDER_ITEMS_HEADER.size()) {
                        throw new PersistenceException(String.format(
                                "Malformed order item record at row %d in %s: expected %d columns but found %d.",
                                rowNum, orderItemsFilePath.getFileName(), ORDER_ITEMS_HEADER.size(), row.size()),
                                orderItemsFilePath);
                    }

                    String orderId = row.get(0).strip();
                    String menuItemId = row.get(1).strip();
                    String itemName = row.get(2);
                    String priceStr = row.get(3).strip();
                    String qtyStr = row.get(4).strip();

                    if (orderId.isBlank()) {
                        throw new PersistenceException("Order ID cannot be blank at row " + rowNum, orderItemsFilePath);
                    }

                    BigDecimal unitPrice;
                    try {
                        unitPrice = new BigDecimal(priceStr);
                    } catch (NumberFormatException e) {
                        throw new PersistenceException("Invalid price at row " + rowNum + ": " + priceStr, e, orderItemsFilePath);
                    }

                    int quantity;
                    try {
                        quantity = Integer.parseInt(qtyStr);
                    } catch (NumberFormatException e) {
                        throw new PersistenceException("Invalid quantity at row " + rowNum + ": " + qtyStr, e, orderItemsFilePath);
                    }

                    OrderItem orderItem = new OrderItem(menuItemId, itemName, unitPrice, quantity);
                    itemsByOrderId.computeIfAbsent(orderId, k -> new ArrayList<>()).add(orderItem);
                }
            }
        }

        // 2. Load order headers and construct full Order objects
        List<Order> orders = new ArrayList<>();
        Set<String> seenOrderIds = new HashSet<>();

        if (ordersExist) {
            List<List<String>> orderRows = readCsvFile(ordersFilePath);
            if (orderRows.size() > 1) {
                validateHeader(orderRows.get(0), ORDERS_HEADER, ordersFilePath);
                for (int i = 1; i < orderRows.size(); i++) {
                    List<String> row = orderRows.get(i);
                    int rowNum = i + 1;
                    if (row.size() != ORDERS_HEADER.size()) {
                        throw new PersistenceException(String.format(
                                "Malformed order record at row %d in %s: expected %d columns but found %d.",
                                rowNum, ordersFilePath.getFileName(), ORDERS_HEADER.size(), row.size()),
                                ordersFilePath);
                    }

                    String orderId = row.get(0).strip();
                    String customerId = row.get(1).strip();
                    String statusStr = row.get(2).strip();
                    String paymentStatusStr = row.get(3).strip();
                    String discountStr = row.get(5).strip();
                    String createdAtStr = row.get(7).strip();
                    String txnRefStr = row.get(8).strip();

                    if (orderId.isBlank()) {
                        throw new PersistenceException("Order ID cannot be blank at row " + rowNum, ordersFilePath);
                    }
                    if (!seenOrderIds.add(orderId)) {
                        throw new PersistenceException("Duplicate Order ID '" + orderId + "' at row " + rowNum, ordersFilePath);
                    }

                    OrderStatus status;
                    try {
                        status = OrderStatus.valueOf(statusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new PersistenceException("Invalid OrderStatus '" + statusStr + "' at row " + rowNum, e, ordersFilePath);
                    }

                    PaymentStatus paymentStatus;
                    try {
                        paymentStatus = PaymentStatus.valueOf(paymentStatusStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new PersistenceException("Invalid PaymentStatus '" + paymentStatusStr + "' at row " + rowNum, e, ordersFilePath);
                    }

                    BigDecimal discountAmount;
                    try {
                        discountAmount = new BigDecimal(discountStr);
                    } catch (NumberFormatException e) {
                        throw new PersistenceException("Invalid discount amount at row " + rowNum + ": " + discountStr, e, ordersFilePath);
                    }

                    LocalDateTime createdAt;
                    try {
                        createdAt = LocalDateTime.parse(createdAtStr);
                    } catch (DateTimeParseException e) {
                        throw new PersistenceException("Invalid creation timestamp at row " + rowNum + ": " + createdAtStr, e, ordersFilePath);
                    }

                    String txnRef = (txnRefStr.isEmpty() || txnRefStr.equalsIgnoreCase("none")) ? null : txnRefStr;

                    List<OrderItem> items = itemsByOrderId.get(orderId);
                    if (items == null || items.isEmpty()) {
                        throw new PersistenceException(String.format(
                                "Order '%s' in %s has no corresponding item records in %s.",
                                orderId, ordersFilePath.getFileName(), orderItemsFilePath.getFileName()),
                                ordersFilePath);
                    }

                    Order order = new Order(
                            orderId,
                            customerId,
                            items,
                            discountAmount,
                            status,
                            paymentStatus,
                            createdAt,
                            txnRef
                    );
                    orders.add(order);
                }
            }
        }

        // 3. Detect orphaned items (items belonging to an unknown order)
        for (String itemOrderId : itemsByOrderId.keySet()) {
            if (!seenOrderIds.contains(itemOrderId)) {
                throw new PersistenceException(String.format(
                        "Found orphaned order items for non-existent order ID '%s' in %s.",
                        itemOrderId, orderItemsFilePath.getFileName()), orderItemsFilePath);
            }
        }

        return Collections.unmodifiableList(orders);
    }

    /**
     * Atomically serializes and writes all orders and their item snapshots to disk.
     *
     * @param orders collection of {@link Order} records to persist
     * @throws PersistenceException if writing fails
     */
    public void saveAll(Collection<Order> orders) throws PersistenceException {
        List<List<String>> orderRows = new ArrayList<>();
        List<List<String>> itemRows = new ArrayList<>();

        if (orders != null) {
            for (Order order : orders) {
                orderRows.add(List.of(
                        order.getOrderId(),
                        order.getCustomerId(),
                        order.getStatus().name(),
                        order.getPaymentStatus().name(),
                        order.getSubtotal().toPlainString(),
                        order.getDiscountAmount().toPlainString(),
                        order.getFinalTotal().toPlainString(),
                        order.getCreatedAt().toString(),
                        order.getTransactionReference() != null ? order.getTransactionReference() : "none"
                ));

                for (OrderItem item : order.getItems()) {
                    itemRows.add(List.of(
                            order.getOrderId(),
                            item.getMenuItemId(),
                            item.getItemName(),
                            item.getUnitPrice().toPlainString(),
                            String.valueOf(item.getQuantity()),
                            item.getSubtotal().toPlainString()
                    ));
                }
            }
        }

        // Write order items first, then orders
        CsvUtils.writeRecordsSafely(orderItemsFilePath, ORDER_ITEMS_HEADER, itemRows);
        CsvUtils.writeRecordsSafely(ordersFilePath, ORDERS_HEADER, orderRows);
    }

    private List<List<String>> readCsvFile(Path path) throws PersistenceException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return CsvUtils.readRecords(reader);
        } catch (IOException e) {
            throw new PersistenceException("Failed to read CSV file: " + path, e, path);
        }
    }

    private void validateHeader(List<String> header, List<String> expected, Path file) {
        if (header.size() != expected.size()) {
            throw new PersistenceException("Invalid header length in " + file.getFileName() + ": " + header, file);
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equalsIgnoreCase(header.get(i).strip())) {
                throw new PersistenceException(String.format(
                        "Header mismatch in %s at column %d: expected '%s' but found '%s'.",
                        file.getFileName(), i, expected.get(i), header.get(i)), file);
            }
        }
    }
}
