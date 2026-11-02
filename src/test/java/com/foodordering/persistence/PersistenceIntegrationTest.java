package com.foodordering.persistence;

import com.foodordering.discount.Discountable;
import com.foodordering.discount.PercentageDiscount;
import com.foodordering.model.Cart;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;
import com.foodordering.model.Order;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;
import com.foodordering.payment.CashPayment;
import com.foodordering.payment.Payment;
import com.foodordering.repository.MenuRepository;
import com.foodordering.repository.OrderRepository;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("End-to-End CSV Persistence & Recovery Integration Tests")
class PersistenceIntegrationTest {

    @Test
    @DisplayName("Should persist full lifecycle and restore state across simulated application restart")
    void shouldPersistFullLifecycleAcrossRestart(@TempDir Path tempDir) {
        Path menuFile = tempDir.resolve("menu_items.csv");
        Path ordersFile = tempDir.resolve("orders.csv");
        Path orderItemsFile = tempDir.resolve("order_items.csv");

        // =====================================================================
        // Phase 1: First Application Session
        // =====================================================================
        CsvMenuStorage session1MenuStorage = new CsvMenuStorage(menuFile);
        CsvOrderStorage session1OrderStorage = new CsvOrderStorage(ordersFile, orderItemsFile);

        MenuRepository menuRepo1 = new MenuRepository(session1MenuStorage);
        OrderRepository orderRepo1 = new OrderRepository(session1OrderStorage);

        MenuService menuService1 = new MenuService(menuRepo1);
        CartService cartService1 = new CartService(menuService1);
        OrderService orderService1 = new OrderService(orderRepo1, menuService1, cartService1);

        // 1. Add menu items
        menuService1.addItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("10.00"), 20, true);
        menuService1.addItem("M002", "Spicy Chicken Burger", "Burgers", new BigDecimal("8.50"), 15, true);

        // Verify CSV file exists and has content
        assertTrue(Files.exists(menuFile));
        assertEquals(2, menuRepo1.count());

        // 2. Customer creates cart and places order
        Customer customer = new Customer("C001", "Bruce Wayne", "bruce@example.com", "hash", "Wayne Manor");
        Cart cart = cartService1.createCart(customer);
        cartService1.addItem(cart, "M001", 2); // 2 x $10.00 = $20.00
        cartService1.addItem(cart, "M002", 1); // 1 x $8.50 = $8.50
        // Subtotal: $28.50

        Discountable discount10 = new PercentageDiscount(new BigDecimal("10")); // $2.85 off -> $25.65
        BigDecimal discountedTotal = discount10.applyDiscount(new BigDecimal("28.50"));
        Payment cash = new CashPayment(discountedTotal);

        Order placedOrder = orderService1.placeOrder(customer, cart, discount10, cash);
        String orderId = placedOrder.getOrderId();

        // Verify orders files exist
        assertTrue(Files.exists(ordersFile));
        assertTrue(Files.exists(orderItemsFile));

        // Verify stock deducted in Session 1
        assertEquals(18, menuService1.findById("M001").getStockQuantity());
        assertEquals(14, menuService1.findById("M002").getStockQuantity());

        // Advance order status
        orderService1.updateOrderStatus(orderId, OrderStatus.PREPARING);

        // =====================================================================
        // Phase 2: Simulate Application Restart (Fresh instances pointing to disk)
        // =====================================================================
        CsvMenuStorage session2MenuStorage = new CsvMenuStorage(menuFile);
        CsvOrderStorage session2OrderStorage = new CsvOrderStorage(ordersFile, orderItemsFile);

        MenuRepository menuRepo2 = new MenuRepository(session2MenuStorage);
        OrderRepository orderRepo2 = new OrderRepository(session2OrderStorage);

        MenuService menuService2 = new MenuService(menuRepo2);
        CartService cartService2 = new CartService(menuService2);
        OrderService orderService2 = new OrderService(orderRepo2, menuService2, cartService2);

        // 1. Verify Menu Items Restored
        assertEquals(2, menuRepo2.count());
        MenuItem restoredM001 = menuService2.findById("M001");
        assertEquals("Margherita Pizza", restoredM001.getName());
        assertEquals(new BigDecimal("10.00"), restoredM001.getPrice());
        assertEquals(18, restoredM001.getStockQuantity()); // Stock reduction persisted!

        MenuItem restoredM002 = menuService2.findById("M002");
        assertEquals("Spicy Chicken Burger", restoredM002.getName());
        assertEquals(14, restoredM002.getStockQuantity());

        // 2. Verify Orders Restored
        assertEquals(1, orderRepo2.count());
        Order restoredOrder = orderService2.findById(orderId);
        assertNotNull(restoredOrder);
        assertEquals(OrderStatus.PREPARING, restoredOrder.getStatus()); // Updated status persisted!
        assertEquals(PaymentStatus.PAID, restoredOrder.getPaymentStatus());
        assertEquals(new BigDecimal("28.50"), restoredOrder.getSubtotal());
        assertEquals(new BigDecimal("2.85"), restoredOrder.getDiscountAmount());
        assertEquals(new BigDecimal("25.65"), restoredOrder.getFinalTotal());

        // 3. Verify Historical Item Snapshot Independence
        assertEquals(2, restoredOrder.getItems().size());
        assertEquals(new BigDecimal("10.00"), restoredOrder.getItems().get(0).getUnitPrice());

        // Update live menu price in Session 2
        menuService2.updatePrice("M001", new BigDecimal("99.99"));
        assertEquals(new BigDecimal("99.99"), menuService2.findById("M001").getPrice());

        // Verify historical order still has $10.00
        assertEquals(new BigDecimal("10.00"), orderService2.findById(orderId).getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("25.65"), orderService2.findById(orderId).getFinalTotal());

        // 4. Update status in Session 2 to DELIVERED and verify persistence
        orderService2.updateOrderStatus(orderId, OrderStatus.READY);
        orderService2.updateOrderStatus(orderId, OrderStatus.DELIVERED);

        // =====================================================================
        // Phase 3: Simulate Another Restart
        // =====================================================================
        MenuRepository menuRepo3 = new MenuRepository(new CsvMenuStorage(menuFile));
        OrderRepository orderRepo3 = new OrderRepository(new CsvOrderStorage(ordersFile, orderItemsFile));

        Order orderSession3 = orderRepo3.findById(orderId);
        assertEquals(OrderStatus.DELIVERED, orderSession3.getStatus());
        assertEquals(new BigDecimal("99.99"), menuRepo3.findById("M001").getPrice());
    }

    @Test
    @DisplayName("Should rollback in-memory state when CSV write fails")
    void shouldRollbackInMemoryWhenWriteFails(@TempDir Path tempDir) {
        Path readOnlyFile = tempDir.resolve("menu_items.csv");
        // Create an unwriteable directory to simulate write failure
        Path invalidDirFile = tempDir.resolve("non_existent_dir").resolve("forbidden").resolve("menu.csv");

        CsvMenuStorage failingStorage = new CsvMenuStorage(invalidDirFile) {
            @Override
            public void saveAll(java.util.Collection<MenuItem> items) {
                throw new PersistenceException("Simulated I/O failure", invalidDirFile);
            }
        };

        MenuRepository repo = new MenuRepository(failingStorage);
        MenuItem item = new MenuItem("M001", "Pizza", "Pizza", new BigDecimal("10.00"), 5, true);

        // Add should fail and rollback in-memory
        assertThrows(PersistenceException.class, () -> repo.add(item));
        assertEquals(0, repo.count());
        assertFalse(repo.existsById("M001"));
    }
}
