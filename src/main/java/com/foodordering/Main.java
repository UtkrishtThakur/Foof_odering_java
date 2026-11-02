package com.foodordering;

import com.foodordering.console.ConsoleApplication;
import com.foodordering.discount.Discountable;
import com.foodordering.discount.FlatDiscount;
import com.foodordering.discount.PercentageDiscount;
import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.exception.OrderNotFoundException;
import com.foodordering.exception.PaymentFailedException;
import com.foodordering.model.Cart;
import com.foodordering.model.CartItem;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;
import com.foodordering.model.Order;
import com.foodordering.model.OrderItem;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;
import com.foodordering.payment.CashPayment;
import com.foodordering.payment.OnlinePayment;
import com.foodordering.payment.Payment;
import com.foodordering.persistence.CsvMenuStorage;
import com.foodordering.persistence.CsvOrderStorage;
import com.foodordering.persistence.CsvUserStorage;
import com.foodordering.persistence.PersistenceConfig;
import com.foodordering.persistence.PersistenceException;
import com.foodordering.repository.MenuRepository;
import com.foodordering.repository.OrderRepository;
import com.foodordering.repository.UserRepository;
import com.foodordering.service.AuthService;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * Integrated Java Food Ordering System entry point.
 *
 * <p>Execution Modes:
 * <ul>
 *   <li><strong>Standard Interactive Mode (Default):</strong> Initializes persistent CSV storage,
 *       bootstraps the administrative authority, and launches the interactive Customer &amp;
 *       Administrator console UI ({@link ConsoleApplication}).</li>
 *   <li><strong>Automated Demonstration Mode (Optional):</strong> Activated when the {@code --demo}
 *       CLI flag or {@code -Dfoodordering.demo=true} JVM property is supplied. Runs programmatic
 *       demonstrations of Parts 2 through 6 without requiring interactive input.</li>
 * </ul>
 * </p>
 */
public class Main {

    public static void main(String[] args) {

        // ------------------------------------------------------------------
        // 1. Resolve persistence directory and wire repositories & services
        // ------------------------------------------------------------------
        Path dataDir = PersistenceConfig.resolveDataDir();

        CsvMenuStorage menuStorage = new CsvMenuStorage(dataDir.resolve("menu_items.csv"));
        CsvOrderStorage orderStorage = new CsvOrderStorage(dataDir.resolve("orders.csv"), dataDir.resolve("order_items.csv"));
        CsvUserStorage userStorage = new CsvUserStorage(dataDir.resolve("users.csv"));

        MenuRepository menuRepository = new MenuRepository(menuStorage);
        OrderRepository orderRepository = new OrderRepository(orderStorage);
        UserRepository userRepository = new UserRepository(userStorage);

        MenuService menuService = new MenuService(menuRepository);
        CartService cartService = new CartService(menuService);
        OrderService orderService = new OrderService(orderRepository, menuService, cartService);
        AuthService authService = new AuthService(userRepository);

        // ------------------------------------------------------------------
        // 2. Evaluate demo mode trigger
        // ------------------------------------------------------------------
        boolean isDemo = isDemoModeRequested(args);

        if (isDemo) {
            banner("Executing Automated Demonstration Mode (--demo)");
            System.out.println("  Data directory : " + dataDir.toAbsolutePath());

            // In headless demo mode, provision a dedicated demonstration administrator if absent
            if (!authService.hasAdministrator()) {
                authService.registerAdmin("admin", "Demo Administrator", "admin@foodordering.com",
                        "DemoAdmin@123", "ADM-001");
            }

            System.out.printf("  Loaded records : %d menu item(s), %d order(s), %d user(s)%n",
                    menuRepository.count(), orderRepository.count(), userRepository.count());

            // Run automated demonstration sequences (Parts 2-6)
            runMenuDemo(menuService);
            runCartDemo(menuService, cartService);
            runDiscountAndPaymentDemo(menuService, cartService);
            runOrderDemo(orderService, cartService, menuService);
            runPersistenceDemo(dataDir, menuService, orderService, cartService);
            return;
        }

        // ------------------------------------------------------------------
        // 4. Standard Interactive Console Mode (Default)
        // ------------------------------------------------------------------
        seedDefaultMenuIfEmpty(menuService);

        ConsoleApplication app = new ConsoleApplication(menuService, cartService, orderService, authService);
        app.start();
    }

    private static boolean isDemoModeRequested(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if ("--demo".equalsIgnoreCase(arg) || "-demo".equalsIgnoreCase(arg)) {
                    return true;
                }
            }
        }
        String demoProp = System.getProperty("foodordering.demo");
        if (demoProp != null && (demoProp.equalsIgnoreCase("true") || demoProp.equals("1"))) {
            return true;
        }
        return PersistenceConfig.isDemoSeedEnabled();
    }

    private static void seedDefaultMenuIfEmpty(MenuService menuService) {
        if (menuService.listAll().isEmpty()) {
            menuService.addItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("8.99"), 20, true);
            menuService.addItem("M002", "Pepperoni Pizza", "Pizza", new BigDecimal("10.49"), 15, true);
            menuService.addItem("M003", "Classic Burger", "Burgers", new BigDecimal("6.99"), 30, true);
            menuService.addItem("M004", "Veggie Burger", "Burgers", new BigDecimal("6.49"), 12, true);
            menuService.addItem("M005", "Caesar Salad", "Salads", new BigDecimal("5.49"), 8, true);
            menuService.addItem("M006", "Chocolate Lava Cake", "Desserts", new BigDecimal("4.99"), 10, true);
        }
    }

    // =========================================================================
    // Part 2 — Menu demo
    // =========================================================================

    private static void runMenuDemo(MenuService menuService) {
        banner("Part 2 — Menu Management");
        section("Adding menu items");
        if (menuService.listAll().isEmpty()) {
            menuService.addItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("8.99"), 20, true);
            menuService.addItem("M002", "Pepperoni Pizza", "Pizza", new BigDecimal("10.49"), 15, true);
            menuService.addItem("M003", "Classic Burger", "Burgers", new BigDecimal("6.99"), 30, true);
            menuService.addItem("M004", "Veggie Burger", "Burgers", new BigDecimal("6.49"), 12, true);
            menuService.addItem("M005", "Caesar Salad", "Salads", new BigDecimal("5.49"), 8, true);
            menuService.addItem("M006", "Chocolate Lava Cake", "Desserts", new BigDecimal("4.99"), 0, false);
            System.out.println("6 items added.");
        } else {
            System.out.println("Menu items already present. Skipping creation.");
        }

        section("All menu items");
        printItems(menuService.listAll());

        section("Search: \"pizza\"");
        printItems(menuService.searchByName("pizza"));

        section("Category filter: \"Burgers\"");
        printItems(menuService.filterByCategory("Burgers"));

        section("Available items only");
        printItems(menuService.listAvailable());

        section("Restock Lava Cake (M006) +10, reduce Margherita (M001) -3");
        menuService.restockItem("M006", 10);
        menuService.reduceStock("M001", 3);
        printItem(menuService.findById("M006"));
        printItem(menuService.findById("M001"));

        section("Menu management error cases");
        tryAction("Duplicate ID M001",
                () -> menuService.addItem("M001", "X", "Y", new BigDecimal("1"), 1, true));
        tryAction("Lookup non-existent M999",
                () -> menuService.findById("M999"));
        tryAction("Over-reduce Caesar Salad by 500",
                () -> menuService.reduceStock("M005", 500));
        tryAction("Negative restock amount",
                () -> menuService.restockItem("M001", -5));
        tryAction("Blank item name",
                () -> menuService.addItem("M007", "  ", "Drinks", new BigDecimal("2"), 5, true));
        tryAction("Negative price",
                () -> menuService.addItem("M007", "Soda", "Drinks", new BigDecimal("-0.50"), 5, true));

        section("Price and name update");
        menuService.updatePrice("M003", new BigDecimal("7.49"));
        menuService.updateName("M003", "Classic Beef Burger");
        menuService.updateAvailability("M006", true);
        printItem(menuService.findById("M003"));
        printItem(menuService.findById("M006"));
    }

    // =========================================================================
    // Part 3 — Cart demo
    // =========================================================================

    private static void runCartDemo(MenuService menuService, CartService cartService) {
        banner("Part 3 — Shopping Cart Management");
        section("1. Create customer and cart");
        Customer alice = new Customer("C001", "Alice", "alice@example.com",
                "hashed_password", "12 Baker Street");
        Cart cart = cartService.createCart(alice);
        System.out.println("Customer : " + alice.getName() + " (" + alice.getUserId() + ")");
        System.out.println("Cart created — empty: " + cart.isEmpty());

        section("2. Add two different items to cart");
        cartService.addItem(cart, "M001", 2);
        cartService.addItem(cart, "M003", 1);
        printCart(cartService.viewCart(cart));

        section("3. Add 1 more Margherita Pizza (M001) — quantities should merge");
        cartService.addItem(cart, "M001", 1);
        printCart(cartService.viewCart(cart));

        section("4. Full cart display");
        printCartDetailed(cart);

        section("5. Update Classic Beef Burger (M003) quantity to 3");
        cartService.updateQuantity(cart, "M003", 3);
        printCart(cartService.viewCart(cart));

        section("6. Remove Margherita Pizza (M001) from cart");
        cartService.removeItem(cart, "M001");
        printCart(cartService.viewCart(cart));

        section("7. Cart subtotal");
        BigDecimal subtotal = cartService.calculateSubtotal(cart);
        System.out.printf("  Subtotal: $%s%n", subtotal.toPlainString());

        section("8. Clear cart");
        cartService.clearCart(cart);
        System.out.println("  Cart cleared. Empty: " + cart.isEmpty());

        section("9. Cart contents after clear");
        Collection<CartItem> items = cartService.viewCart(cart);
        System.out.println("  Lines in cart: " + items.size());
        System.out.printf("  Subtotal: $%s%n", cartService.calculateSubtotal(cart).toPlainString());

        section("10. Invalid cart operations");
        tryAction("Add M001 with quantity 0", () -> cartService.addItem(cart, "M001", 0));
        tryAction("Add M001 with quantity -3", () -> cartService.addItem(cart, "M001", -3));
        menuService.updateAvailability("M006", false);
        tryAction("Add unavailable item M006", () -> cartService.addItem(cart, "M006", 1));
        menuService.updateAvailability("M006", true);
        tryAction("Add 100 x Caesar Salad (exceeding stock)", () -> cartService.addItem(cart, "M005", 100));
        tryAction("Remove M999 not in cart", () -> cartService.removeItem(cart, "M999"));
        tryAction("Update quantity of M001 not in cart", () -> cartService.updateQuantity(cart, "M001", 5));
    }

    // =========================================================================
    // Part 4 — Discounts and Payment Simulation demo
    // =========================================================================

    private static void runDiscountAndPaymentDemo(MenuService menuService, CartService cartService) {
        banner("Part 4 — Discounts and Payment Simulation");
        section("1. Create customer and cart");
        Customer bob = new Customer("C002", "Bob Smith", "bob@example.com",
                "hashed_pwd_bob", "42 Elm Street, Springfield");
        Cart cart = cartService.createCart(bob);
        cartService.addItem(cart, "M001", 1);
        cartService.addItem(cart, "M002", 1);
        printCart(cartService.viewCart(cart));

        section("3. Calculate original cart subtotal");
        BigDecimal originalSubtotal = cartService.calculateSubtotal(cart);
        System.out.printf("  Original Cart Subtotal: $%s%n", originalSubtotal.toPlainString());

        section("4. Apply a percentage discount (10%)");
        Discountable percentageDiscount = new PercentageDiscount(new BigDecimal("10"));
        BigDecimal amountAfterPercentage = percentageDiscount.applyDiscount(originalSubtotal);
        BigDecimal percentageSavings = originalSubtotal.subtract(amountAfterPercentage);
        System.out.printf("  Discount Type   : %s%n", percentageDiscount);
        System.out.printf("  Original Amount : $%s%n", originalSubtotal.toPlainString());
        System.out.printf("  Discount Amount : -$%s%n", percentageSavings.toPlainString());
        System.out.printf("  Resulting Amount: $%s%n", amountAfterPercentage.toPlainString());

        section("5. Apply a flat discount ($5.00)");
        Discountable flatDiscount = new FlatDiscount(new BigDecimal("5.00"));
        BigDecimal amountAfterFlat = flatDiscount.applyDiscount(originalSubtotal);
        BigDecimal flatSavings = originalSubtotal.subtract(amountAfterFlat);
        System.out.printf("  Discount Type   : %s%n", flatDiscount);
        System.out.printf("  Original Amount : $%s%n", originalSubtotal.toPlainString());
        System.out.printf("  Discount Amount : -$%s%n", flatSavings.toPlainString());
        System.out.printf("  Resulting Amount: $%s%n", amountAfterFlat.toPlainString());

        section("7. Simulate a successful cash payment");
        Payment cashPayment = new CashPayment(amountAfterPercentage);
        processAndReportPayment(cashPayment, "Cash on delivery (10% discounted amount)");

        section("8. Simulate a successful online payment");
        Payment onlineSuccessPayment = new OnlinePayment(amountAfterFlat, true);
        processAndReportPayment(onlineSuccessPayment, "Online card payment ($5.00 flat discount, simulated success)");

        section("9. Simulate a failed online payment");
        Payment onlineFailedPayment = new OnlinePayment(amountAfterFlat, false);
        processAndReportPayment(onlineFailedPayment, "Online UPI payment ($5.00 flat discount, simulated decline)");
    }

    private static void processAndReportPayment(Payment payment, String description) {
        System.out.printf("  > Payment Type : %s%n", payment.getClass().getSimpleName());
        System.out.printf("    Description  : %s%n", description);
        System.out.printf("    Amount       : $%s%n", payment.getAmount().toPlainString());
        PaymentStatus result = payment.processPayment();
        System.out.printf("    Status after : %s (isSuccessful = %b)%n", result, payment.isSuccessful());
    }

    // =========================================================================
    // Part 5 — Order Management & Lifecycle demo
    // =========================================================================

    private static void runOrderDemo(OrderService orderService, CartService cartService, MenuService menuService) {
        banner("Part 5 — Order Management & Lifecycle");
        Customer carol = new Customer("C003", "Carol Danvers", "carol@example.com",
                "hashed_pwd_carol", "77 Marvel Way, Metropolis");
        Cart cart1 = cartService.createCart(carol);
        cartService.addItem(cart1, "M001", 2);
        cartService.addItem(cart1, "M003", 1);
        BigDecimal subtotal1 = cartService.calculateSubtotal(cart1);

        Discountable discount10 = new PercentageDiscount(new BigDecimal("10"));
        BigDecimal finalTotal1 = discount10.applyDiscount(subtotal1);
        Payment cashPayment = new CashPayment(finalTotal1);

        Order order1 = orderService.placeOrder(carol, cart1, discount10, cashPayment);
        printOrderDetailed(order1);

        section("5. Order Status Lifecycle transitions");
        orderService.updateOrderStatus(order1.getOrderId(), OrderStatus.PREPARING);
        orderService.updateOrderStatus(order1.getOrderId(), OrderStatus.READY);
        orderService.updateOrderStatus(order1.getOrderId(), OrderStatus.DELIVERED);
        System.out.printf("  Order status reached: %s%n", orderService.findById(order1.getOrderId()).getStatus());
    }

    // =========================================================================
    // Part 6 — CSV Persistence & Data Recovery demo
    // =========================================================================

    private static void runPersistenceDemo(Path dataDir, MenuService menuService,
                                           OrderService orderService, CartService cartService) {
        banner("Part 6 — CSV Persistence & Data Recovery");
        Path menuFile = dataDir.resolve("menu_items.csv");
        Path ordersFile = dataDir.resolve("orders.csv");
        Path orderItemsFile = dataDir.resolve("order_items.csv");
        Path usersFile = dataDir.resolve("users.csv");

        System.out.println("  Menu items file : " + menuFile.toAbsolutePath() + " (Exists: " + Files.exists(menuFile) + ")");
        System.out.println("  Orders file     : " + ordersFile.toAbsolutePath() + " (Exists: " + Files.exists(ordersFile) + ")");
        System.out.println("  Order items file: " + orderItemsFile.toAbsolutePath() + " (Exists: " + Files.exists(orderItemsFile) + ")");
        System.out.println("  Users file      : " + usersFile.toAbsolutePath() + " (Exists: " + Files.exists(usersFile) + ")");
    }

    // =========================================================================
    // Console formatting helpers
    // =========================================================================

    private static void banner(String title) {
        System.out.println();
        System.out.println("=".repeat(56));
        System.out.printf("  %-52s%n", title);
        System.out.println("=".repeat(56));
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("--- " + title + " ---");
    }

    private static void printItems(List<MenuItem> items) {
        if (items.isEmpty()) {
            System.out.println("  (no items)");
            return;
        }
        for (MenuItem item : items) {
            printItem(item);
        }
    }

    private static void printItem(MenuItem item) {
        System.out.printf("  [%s] %-22s | %-8s | $%6s | stock: %3d | available: %s%n",
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getPrice().toPlainString(),
                item.getStockQuantity(),
                item.isAvailable() ? "yes" : "no");
    }

    private static void printCart(Collection<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            System.out.println("  (cart is empty)");
            return;
        }
        for (CartItem ci : cartItems) {
            System.out.printf("  [%s] %-22s | qty: %3d | unit: $%s | subtotal: $%s%n",
                    ci.getMenuItem().getId(),
                    ci.getMenuItem().getName(),
                    ci.getQuantity(),
                    ci.getMenuItem().getPrice().toPlainString(),
                    ci.getSubtotal().toPlainString());
        }
    }

    private static void printCartDetailed(Cart cart) {
        System.out.println("  Customer ID : " + cart.getCustomerId());
        System.out.println("  Items       : " + cart.lineCount());
        printCart(cart.getItems().values());
        System.out.printf("  Subtotal    : $%s%n", cart.calculateSubtotal().toPlainString());
    }

    private static void printOrderDetailed(Order order) {
        System.out.printf("  Order ID      : %s%n", order.getOrderId());
        System.out.printf("  Customer ID   : %s%n", order.getCustomerId());
        System.out.printf("  Created At    : %s%n", order.getCreatedAt());
        System.out.printf("  Order Status  : %s%n", order.getStatus());
        System.out.printf("  Payment Status: %s%n", order.getPaymentStatus());
        System.out.printf("  Final Total   : $%s%n", order.getFinalTotal().toPlainString());
    }

    private static void tryAction(String description, Runnable action) {
        System.out.print("  > " + description + " ... ");
        try {
            action.run();
            System.out.println("OK (unexpected success — check logic)");
        } catch (DuplicateItemException | ItemNotFoundException | OrderNotFoundException
                 | InsufficientStockException | InvalidInputException | PaymentFailedException
                 | PersistenceException e) {
            System.out.println("Caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("Caught IllegalArgumentException: " + e.getMessage());
        }
    }
}
