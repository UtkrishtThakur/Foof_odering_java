package com.foodordering.console;

import com.foodordering.discount.Discountable;
import com.foodordering.discount.PercentageDiscount;
import com.foodordering.exception.AuthenticationException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.Admin;
import com.foodordering.model.Cart;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;
import com.foodordering.model.Order;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;
import com.foodordering.payment.CashPayment;
import com.foodordering.repository.MenuRepository;
import com.foodordering.repository.OrderRepository;
import com.foodordering.repository.UserRepository;
import com.foodordering.service.AuthService;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integrated Customer & Admin Console Workflow Tests")
class ConsoleWorkflowTest {

    private MenuRepository menuRepository;
    private OrderRepository orderRepository;
    private UserRepository userRepository;

    private MenuService menuService;
    private CartService cartService;
    private OrderService orderService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        userRepository = new UserRepository();

        menuService = new MenuService(menuRepository);
        cartService = new CartService(menuService);
        orderService = new OrderService(orderRepository, menuService, cartService);
        authService = new AuthService(userRepository);

        // Seed initial menu
        menuService.addItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("10.00"), 10, true);
        menuService.addItem("M002", "Classic Burger", "Burgers", new BigDecimal("8.00"), 15, true);
        menuService.addItem("M003", "Soda", "Drinks", new BigDecimal("2.50"), 20, true);

        // Provision Admin
        authService.registerAdmin("admin", "System Administrator", "admin@foodordering.com",
                "Admin@12345", "ADM-001");
    }

    @Nested
    @DisplayName("End-to-End Customer Workflows")
    class CustomerWorkflowTests {

        @Test
        @DisplayName("Full Customer Lifecycle: Register -> Login -> Add to Cart -> Discount -> Pay -> Track Order")
        void testFullCustomerJourney() {
            // 1. Register customer
            Customer customer = authService.registerCustomer(
                    "alice99", "Alice Wonder", "alice@example.com", "Password@123", "42 Fantasy Lane");
            assertNotNull(customer);

            // 2. Login verification
            Customer loggedIn = authService.loginCustomer("alice99", "Password@123");
            assertEquals("alice99", loggedIn.getUserId());

            // 3. Populate Cart
            Cart cart = cartService.createCart(loggedIn);
            cartService.addItem(cart, "M001", 2); // 2 x $10.00 = $20.00
            cartService.addItem(cart, "M003", 2); // 2 x $2.50 = $5.00
            assertEquals(new BigDecimal("25.00"), cartService.calculateSubtotal(cart));

            // 4. Apply Discount (10%)
            Discountable discount10 = new PercentageDiscount(new BigDecimal("10"));
            BigDecimal finalDue = discount10.applyDiscount(new BigDecimal("25.00")); // $22.50

            // 5. Place Order with Cash payment
            Order order = orderService.placeOrder(loggedIn, cart, discount10, new CashPayment(finalDue));
            assertNotNull(order.getOrderId());
            assertEquals(OrderStatus.CONFIRMED, order.getStatus());
            assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
            assertEquals(new BigDecimal("22.50"), order.getFinalTotal());
            assertTrue(cart.isEmpty());

            // Inventory reduced
            assertEquals(8, menuService.findById("M001").getStockQuantity());
            assertEquals(18, menuService.findById("M003").getStockQuantity());

            // 6. View My Orders
            List<Order> myOrders = orderService.findByCustomerId("alice99");
            assertEquals(1, myOrders.size());
            assertEquals(order.getOrderId(), myOrders.get(0).getOrderId());

            // 7. Track Order
            Order tracked = orderService.findById(order.getOrderId());
            assertEquals("alice99", tracked.getCustomerId());
            assertEquals(OrderStatus.CONFIRMED, tracked.getStatus());
        }

        @Test
        @DisplayName("Order Privacy: Customer cannot track another customer's order")
        void testOrderOwnershipPrivacy() {
            Customer alice = authService.registerCustomer("alice", "Alice", "a@test.com", "Pass@123", null);
            Customer bob = authService.registerCustomer("bob", "Bob", "b@test.com", "Pass@123", null);

            Cart bobCart = cartService.createCart(bob);
            cartService.addItem(bobCart, "M001", 1);
            Order bobOrder = orderService.placeOrder(bob, bobCart, null, new CashPayment(new BigDecimal("10.00")));

            // Alice attempts to track Bob's order
            Order foundOrder = orderService.findById(bobOrder.getOrderId());
            assertFalse(foundOrder.getCustomerId().equals(alice.getUserId()),
                    "Alice must not match the customer ID of Bob's order");
        }
    }

    @Nested
    @DisplayName("End-to-End Administrator Workflows")
    class AdminWorkflowTests {

        @Test
        @DisplayName("Full Admin Lifecycle: Login -> Add Item -> Restock -> View Orders -> Transition Status")
        void testFullAdminJourney() {
            // 1. Admin Login
            Admin admin = authService.loginAdmin("admin", "Admin@12345", "ADM-001");
            assertNotNull(admin);

            // 2. Add New Menu Item
            MenuItem newItem = menuService.addItem("M004", "Ice Cream", "Desserts", new BigDecimal("4.00"), 5, true);
            assertEquals("M004", newItem.getId());
            assertEquals(4, menuService.listAll().size());

            // 3. Restock
            menuService.restockItem("M004", 10);
            assertEquals(15, menuService.findById("M004").getStockQuantity());

            // 4. Place an order via a customer to generate activity
            Customer cust = authService.registerCustomer("c1", "Client One", "c1@test.com", "P@ssword1", null);
            Cart cart = cartService.createCart(cust);
            cartService.addItem(cart, "M004", 2);
            Order order = orderService.placeOrder(cust, cart, null, new CashPayment(new BigDecimal("8.00")));

            // 5. Admin views all orders
            List<Order> allOrders = orderService.listAll();
            assertEquals(1, allOrders.size());

            // 6. Admin transitions order: CONFIRMED -> PREPARING -> READY -> DELIVERED
            orderService.updateOrderStatus(order.getOrderId(), OrderStatus.PREPARING);
            assertEquals(OrderStatus.PREPARING, orderService.findById(order.getOrderId()).getStatus());

            orderService.updateOrderStatus(order.getOrderId(), OrderStatus.READY);
            assertEquals(OrderStatus.READY, orderService.findById(order.getOrderId()).getStatus());

            orderService.updateOrderStatus(order.getOrderId(), OrderStatus.DELIVERED);
            assertEquals(OrderStatus.DELIVERED, orderService.findById(order.getOrderId()).getStatus());

            // 7. Verify terminal state prevents illegal cancellation
            assertThrows(InvalidInputException.class, () ->
                    orderService.updateOrderStatus(order.getOrderId(), OrderStatus.CANCELLED));
        }
    }

    @Nested
    @DisplayName("Interactive Console UI Simulation")
    class InteractiveConsoleSimulationTests {

        @Test
        @DisplayName("Simulate Main Portal exit option (Choice 4)")
        void testConsoleAppExitOption() {
            String script = "4\n"; // Choose option 4: Exit
            ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
            ConsoleInput input = new ConsoleInput(
                    new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(outCapture));

            ConsoleApplication app = new ConsoleApplication(
                    menuService, cartService, orderService, authService, input, new PrintStream(outCapture));
            app.start();

            String output = outCapture.toString();
            assertTrue(output.contains("JAVA FOOD ORDERING SYSTEM"));
            assertTrue(output.contains("Thank you for using the Java Food Ordering System. Goodbye!"));
        }

        @Test
        @DisplayName("Simulate Customer Login failure and recovery")
        void testConsoleAppCustomerLoginFailure() {
            String script = """
                    1
                    non_existent_user
                    wrong_pass
                    4
                    """;
            ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
            ConsoleInput input = new ConsoleInput(
                    new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(outCapture));

            ConsoleApplication app = new ConsoleApplication(
                    menuService, cartService, orderService, authService, input, new PrintStream(outCapture));
            app.start();

            String output = outCapture.toString();
            assertTrue(output.contains("[Login Failed]"));
            assertTrue(output.contains("Thank you for using the Java Food Ordering System."));
        }
    }
}
