package com.foodordering.persistence;

import com.foodordering.model.Order;
import com.foodordering.model.OrderItem;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CSV Order Storage Tests")
class CsvOrderStorageTest {

    @Test
    @DisplayName("Should return empty list when neither orders nor items files exist")
    void shouldReturnEmptyListWhenFilesDoNotExist(@TempDir Path tempDir) {
        CsvOrderStorage storage = new CsvOrderStorage(tempDir);
        assertTrue(storage.load().isEmpty());
    }

    @Test
    @DisplayName("Should save and reload orders with historical item snapshots accurately")
    void shouldSaveAndReloadOrdersAccurately(@TempDir Path tempDir) {
        CsvOrderStorage storage = new CsvOrderStorage(tempDir);

        OrderItem item1 = new OrderItem("M001", "Margherita Pizza", new BigDecimal("8.99"), 2); // $17.98
        OrderItem item2 = new OrderItem("M003", "Classic \"Beef\", Burger", new BigDecimal("7.49"), 1); // $7.49
        // Subtotal: $25.47, Discount: $2.55, Final Total: $22.92

        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 27, 10, 30, 0);
        Order order = new Order("ORD-TEST-001", "C001", List.of(item1, item2),
                new BigDecimal("2.55"), OrderStatus.CONFIRMED, PaymentStatus.PAID, timestamp, "TXN-SIM-999");

        storage.saveAll(List.of(order));

        assertTrue(Files.exists(storage.getOrdersFilePath()));
        assertTrue(Files.exists(storage.getOrderItemsFilePath()));

        List<Order> loadedOrders = storage.load();
        assertEquals(1, loadedOrders.size());

        Order loaded = loadedOrders.get(0);
        assertEquals("ORD-TEST-001", loaded.getOrderId());
        assertEquals("C001", loaded.getCustomerId());
        assertEquals(OrderStatus.CONFIRMED, loaded.getStatus());
        assertEquals(PaymentStatus.PAID, loaded.getPaymentStatus());
        assertEquals(new BigDecimal("25.47"), loaded.getSubtotal());
        assertEquals(new BigDecimal("2.55"), loaded.getDiscountAmount());
        assertEquals(new BigDecimal("22.92"), loaded.getFinalTotal());
        assertEquals(timestamp, loaded.getCreatedAt());
        assertEquals("TXN-SIM-999", loaded.getTransactionReference());

        // Verify items
        assertEquals(2, loaded.getItems().size());

        OrderItem loadedItem1 = loaded.getItems().get(0);
        assertEquals("M001", loadedItem1.getMenuItemId());
        assertEquals("Margherita Pizza", loadedItem1.getItemName());
        assertEquals(new BigDecimal("8.99"), loadedItem1.getUnitPrice());
        assertEquals(2, loadedItem1.getQuantity());
        assertEquals(new BigDecimal("17.98"), loadedItem1.getSubtotal());

        OrderItem loadedItem2 = loaded.getItems().get(1);
        assertEquals("M003", loadedItem2.getMenuItemId());
        assertEquals("Classic \"Beef\", Burger", loadedItem2.getItemName());
        assertEquals(new BigDecimal("7.49"), loadedItem2.getUnitPrice());
        assertEquals(1, loadedItem2.getQuantity());
        assertEquals(new BigDecimal("7.49"), loadedItem2.getSubtotal());
    }

    @Test
    @DisplayName("Should detect orphaned order items and throw PersistenceException")
    void shouldDetectOrphanedItems(@TempDir Path tempDir) throws IOException {
        CsvOrderStorage storage = new CsvOrderStorage(tempDir);

        // Valid orders file with one order
        String ordersContent = """
                order_id,customer_id,status,payment_status,subtotal,discount_amount,final_total,created_at,transaction_reference
                ORD-1,C001,CONFIRMED,PAID,10.00,0.00,10.00,2026-10-27T10:00:00,none
                """;
        Files.writeString(storage.getOrdersFilePath(), ordersContent);

        // Order items file containing an item for ORD-1 AND an item for unknown ORD-999
        String itemsContent = """
                order_id,menu_item_id,item_name,unit_price,quantity,subtotal
                ORD-1,M001,Pizza,10.00,1,10.00
                ORD-999,M002,Burger,5.00,1,5.00
                """;
        Files.writeString(storage.getOrderItemsFilePath(), itemsContent);

        assertThrows(PersistenceException.class, storage::load);
    }

    @Test
    @DisplayName("Should throw PersistenceException when orders.csv has an order without items")
    void shouldRejectOrderWithoutItems(@TempDir Path tempDir) throws IOException {
        CsvOrderStorage storage = new CsvOrderStorage(tempDir);

        String ordersContent = """
                order_id,customer_id,status,payment_status,subtotal,discount_amount,final_total,created_at,transaction_reference
                ORD-EMPTY,C001,CONFIRMED,PAID,10.00,0.00,10.00,2026-10-27T10:00:00,none
                """;
        Files.writeString(storage.getOrdersFilePath(), ordersContent);

        // Items file has header only
        String itemsContent = "order_id,menu_item_id,item_name,unit_price,quantity,subtotal\n";
        Files.writeString(storage.getOrderItemsFilePath(), itemsContent);

        assertThrows(PersistenceException.class, storage::load);
    }
}
