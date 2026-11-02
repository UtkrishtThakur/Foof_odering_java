package com.foodordering.console;

import com.foodordering.discount.Discountable;
import com.foodordering.discount.FlatDiscount;
import com.foodordering.discount.PercentageDiscount;
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
import com.foodordering.payment.CashPayment;
import com.foodordering.payment.OnlinePayment;
import com.foodordering.payment.Payment;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * Interactive console menu and workflow controller for authenticated customers.
 */
public class CustomerMenu {

    private final Customer customer;
    private final Cart cart;
    private final MenuService menuService;
    private final CartService cartService;
    private final OrderService orderService;
    private final ConsoleInput input;
    private final PrintStream out;

    public CustomerMenu(Customer customer,
                        MenuService menuService,
                        CartService cartService,
                        OrderService orderService,
                        ConsoleInput input,
                        PrintStream out) {
        this.customer = customer;
        this.menuService = menuService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.cart = cartService.createCart(customer);
        this.input = input;
        this.out = (out != null) ? out : System.out;
    }

    /**
     * Executes the customer interactive loop until the customer chooses to log out.
     */
    public void run() {
        boolean active = true;
        while (active) {
            displayMenu();
            int choice = input.readIntInRange("  Select an option [1-12]: ", 1, 12);
            out.println();

            switch (choice) {
                case 1 -> viewAvailableMenu();
                case 2 -> searchMenu();
                case 3 -> filterByCategory();
                case 4 -> addToCart();
                case 5 -> viewCart();
                case 6 -> updateCartQuantity();
                case 7 -> removeFromCart();
                case 8 -> clearCart();
                case 9 -> checkout();
                case 10 -> viewMyOrders();
                case 11 -> trackOrder();
                case 12 -> {
                    out.printf("  Logging out %s. Goodbye!%n", customer.getName());
                    active = false;
                }
            }
            if (active && choice != 12) {
                input.readLine("\n  Press [Enter] to return to customer dashboard...");
            }
        }
    }

    private void displayMenu() {
        out.println();
        out.println("========================================================");
        out.printf("  CUSTOMER DASHBOARD — Logged in as: %s (%s)%n", customer.getName(), customer.getUserId());
        if (customer.getDeliveryAddress() != null) {
            out.printf("  Delivery Address  : %s%n", customer.getDeliveryAddress());
        }
        out.printf("  Active Cart Items : %d item(s)%n", cart.lineCount());
        out.println("========================================================");
        out.println("  1.  View Available Menu Items");
        out.println("  2.  Search Menu by Keyword");
        out.println("  3.  Filter Menu by Category");
        out.println("  4.  Add Item to Cart");
        out.println("  5.  View Cart & Subtotal");
        out.println("  6.  Update Cart Item Quantity");
        out.println("  7.  Remove Item from Cart");
        out.println("  8.  Clear Cart");
        out.println("  9.  Checkout & Place Order");
        out.println("  10. View My Order History");
        out.println("  11. Track Order Status");
        out.println("  12. Logout");
        out.println("========================================================");
    }

    private void viewAvailableMenu() {
        out.println("--- Available Menu Items ---");
        List<MenuItem> items = menuService.listAvailable();
        printMenuItems(items);
    }

    private void searchMenu() {
        out.println("--- Search Menu by Keyword ---");
        String keyword = input.readRequiredLine("  Enter search keyword: ");
        try {
            List<MenuItem> matches = menuService.searchByName(keyword);
            printMenuItems(matches);
        } catch (InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void filterByCategory() {
        out.println("--- Filter Menu by Category ---");
        String category = input.readRequiredLine("  Enter category name: ");
        try {
            List<MenuItem> matches = menuService.filterByCategory(category);
            printMenuItems(matches);
        } catch (InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void addToCart() {
        out.println("--- Add Item to Cart ---");
        String itemId = input.readRequiredLine("  Enter Menu Item ID: ");
        int quantity = input.readPositiveInt("  Enter Quantity: ");
        try {
            cartService.addItem(cart, itemId, quantity);
            MenuItem item = menuService.findById(itemId);
            out.printf("  [Success] Added %d x '%s' to your cart.%n", quantity, item.getName());
        } catch (ItemNotFoundException | InvalidInputException | InsufficientStockException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void viewCart() {
        out.println("--- Your Shopping Cart ---");
        if (cart.isEmpty()) {
            out.println("  Your cart is currently empty.");
            return;
        }

        printCartItems(cartService.viewCart(cart));
        BigDecimal subtotal = cartService.calculateSubtotal(cart);
        out.printf("  Cart Subtotal: $%s%n", subtotal.toPlainString());
    }

    private void updateCartQuantity() {
        out.println("--- Update Item Quantity in Cart ---");
        if (cart.isEmpty()) {
            out.println("  Your cart is empty. Nothing to update.");
            return;
        }

        String itemId = input.readRequiredLine("  Enter Menu Item ID to update: ");
        int newQty = input.readPositiveInt("  Enter new quantity: ");
        try {
            cartService.updateQuantity(cart, itemId, newQty);
            out.println("  [Success] Cart updated successfully.");
        } catch (ItemNotFoundException | InvalidInputException | InsufficientStockException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void removeFromCart() {
        out.println("--- Remove Item from Cart ---");
        if (cart.isEmpty()) {
            out.println("  Your cart is empty. Nothing to remove.");
            return;
        }

        String itemId = input.readRequiredLine("  Enter Menu Item ID to remove: ");
        try {
            cartService.removeItem(cart, itemId);
            out.println("  [Success] Item removed from cart.");
        } catch (ItemNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void clearCart() {
        out.println("--- Clear Shopping Cart ---");
        if (cart.isEmpty()) {
            out.println("  Your cart is already empty.");
            return;
        }

        boolean confirm = input.readBoolean("  Are you sure you want to clear your cart? (y/n): ");
        if (confirm) {
            cartService.clearCart(cart);
            out.println("  [Success] Cart cleared.");
        } else {
            out.println("  Cart clear cancelled.");
        }
    }

    private void checkout() {
        out.println("--- Checkout & Place Order ---");
        if (cart.isEmpty()) {
            out.println("  [Error] Your cart is empty. Add items before checking out.");
            return;
        }

        // 1. Display cart preview
        printCartItems(cartService.viewCart(cart));
        BigDecimal subtotal = cartService.calculateSubtotal(cart);
        out.printf("  Order Subtotal: $%s%n", subtotal.toPlainString());

        // 2. Select Discount Strategy
        out.println();
        out.println("  Discount Options:");
        out.println("    1. No Discount");
        out.println("    2. Percentage Discount (%)");
        out.println("    3. Flat Amount Discount ($)");
        int discountChoice = input.readIntInRange("  Select discount option [1-3]: ", 1, 3);

        Discountable discount = null;
        if (discountChoice == 2) {
            BigDecimal percent = input.readNonNegativeBigDecimal("  Enter discount percentage (0 - 100): ");
            try {
                discount = new PercentageDiscount(percent);
            } catch (InvalidInputException e) {
                out.printf("  [Error] %s (Proceeding without discount)%n", e.getMessage());
            }
        } else if (discountChoice == 3) {
            BigDecimal flat = input.readNonNegativeBigDecimal("  Enter flat discount amount: $");
            try {
                discount = new FlatDiscount(flat);
            } catch (InvalidInputException e) {
                out.printf("  [Error] %s (Proceeding without discount)%n", e.getMessage());
            }
        }

        BigDecimal finalTotal = subtotal;
        BigDecimal discountSavings = BigDecimal.ZERO;
        if (discount != null) {
            finalTotal = discount.applyDiscount(subtotal);
            discountSavings = subtotal.subtract(finalTotal).setScale(2, RoundingMode.HALF_UP);
            out.printf("  Discount Applied : %s%n", discount);
            out.printf("  Discount Savings : -$%s%n", discountSavings.toPlainString());
        }
        out.printf("  Final Total Due  : $%s%n", finalTotal.toPlainString());

        // 3. Select Payment Method
        out.println();
        out.println("  Payment Methods:");
        out.println("    1. Cash on Delivery (COD)");
        out.println("    2. Simulated Online Payment (Card / UPI)");
        int paymentChoice = input.readIntInRange("  Select payment method [1-2]: ", 1, 2);

        Payment payment;
        if (paymentChoice == 1) {
            payment = new CashPayment(finalTotal);
        } else {
            boolean simulateSuccess = input.readBoolean("  Simulate successful payment approval? (y=success, n=decline): ");
            payment = new OnlinePayment(finalTotal, simulateSuccess);
        }

        // 4. Confirm and process order
        boolean confirm = input.readBoolean("\n  Confirm and submit order? (y/n): ");
        if (!confirm) {
            out.println("  Order submission cancelled. Cart remains untouched.");
            return;
        }

        try {
            Order order = orderService.placeOrder(customer, cart, discount, payment);
            out.println();
            out.println("========================================================");
            out.println("  ORDER PLACED SUCCESSFULLY!");
            out.println("========================================================");
            printOrderReceipt(order);
        } catch (InsufficientStockException e) {
            out.printf("  [Checkout Failed] Insufficient stock: %s%n", e.getMessage());
            out.println("  Your cart contents have been preserved.");
        } catch (PaymentFailedException e) {
            out.printf("  [Checkout Failed] Payment declined: %s%n", e.getMessage());
            out.println("  Your cart contents and stock have been preserved.");
        } catch (InvalidInputException e) {
            out.printf("  [Checkout Failed] Validation error: %s%n", e.getMessage());
        } catch (Exception e) {
            out.printf("  [Checkout Failed] System error: %s%n", e.getMessage());
        }
    }

    private void viewMyOrders() {
        out.println("--- My Order History ---");
        List<Order> orders = orderService.findByCustomerId(customer.getUserId());
        if (orders.isEmpty()) {
            out.println("  You have not placed any orders yet.");
            return;
        }

        out.println("  ---------------------------------------------------------------------------------------------------");
        out.printf("  %-12s | %-19s | %-12s | %-10s | %-8s | %-10s%n",
                "Order ID", "Date / Time", "Status", "Payment", "Items", "Final Total");
        out.println("  ---------------------------------------------------------------------------------------------------");
        for (Order o : orders) {
            out.printf("  %-12s | %-19s | %-12s | %-10s | %-8d | $%s%n",
                    o.getOrderId(),
                    o.getCreatedAt().toString().replace('T', ' ').substring(0, Math.min(19, o.getCreatedAt().toString().length())),
                    o.getStatus(),
                    o.getPaymentStatus(),
                    o.getItems().size(),
                    o.getFinalTotal().toPlainString());
        }
        out.println("  ---------------------------------------------------------------------------------------------------");
    }

    private void trackOrder() {
        out.println("--- Track Order Status ---");
        String orderId = input.readRequiredLine("  Enter Order ID (e.g. ORD-...): ");

        try {
            Order order = orderService.findById(orderId);

            // Strict Role & Ownership Verification: Customer cannot inspect other users' orders
            if (!order.getCustomerId().equalsIgnoreCase(customer.getUserId())) {
                out.println("  [Access Denied] No order with that ID belongs to your account.");
                return;
            }

            printOrderReceipt(order);
        } catch (OrderNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void printMenuItems(List<MenuItem> items) {
        if (items.isEmpty()) {
            out.println("  (No matching menu items found)");
            return;
        }
        out.println("  -----------------------------------------------------------------------------------");
        out.printf("  %-6s | %-24s | %-12s | %-8s | %-7s%n", "ID", "Name", "Category", "Price", "Stock");
        out.println("  -----------------------------------------------------------------------------------");
        for (MenuItem item : items) {
            out.printf("  %-6s | %-24s | %-12s | $%7s | %5d%n",
                    item.getId(),
                    item.getName(),
                    item.getCategory(),
                    item.getPrice().toPlainString(),
                    item.getStockQuantity());
        }
        out.println("  -----------------------------------------------------------------------------------");
    }

    private void printCartItems(Collection<CartItem> items) {
        out.println("  -----------------------------------------------------------------------------------");
        out.printf("  %-6s | %-24s | %-8s | %-5s | %-8s%n", "ID", "Name", "Unit Price", "Qty", "Subtotal");
        out.println("  -----------------------------------------------------------------------------------");
        for (CartItem ci : items) {
            out.printf("  %-6s | %-24s | $%9s | %5d | $%8s%n",
                    ci.getMenuItem().getId(),
                    ci.getMenuItem().getName(),
                    ci.getMenuItem().getPrice().toPlainString(),
                    ci.getQuantity(),
                    ci.getSubtotal().toPlainString());
        }
        out.println("  -----------------------------------------------------------------------------------");
    }

    private void printOrderReceipt(Order order) {
        out.printf("  Order ID         : %s%n", order.getOrderId());
        out.printf("  Customer ID      : %s%n", order.getCustomerId());
        out.printf("  Order Timestamp  : %s%n", order.getCreatedAt());
        out.printf("  Current Status   : %s%n", order.getStatus());
        out.printf("  Payment Status   : %s%n", order.getPaymentStatus());
        if (order.getTransactionReference() != null) {
            out.printf("  Transaction Ref  : %s%n", order.getTransactionReference());
        }
        out.println("  Items Purchased  :");
        for (OrderItem item : order.getItems()) {
            out.printf("    - [%s] %-22s | Qty: %2d | Unit: $%s | Subtotal: $%s%n",
                    item.getMenuItemId(), item.getItemName(),
                    item.getQuantity(), item.getUnitPrice().toPlainString(),
                    item.getSubtotal().toPlainString());
        }
        out.printf("  Subtotal         : $%s%n", order.getSubtotal().toPlainString());
        out.printf("  Discount Deducted: -$%s%n", order.getDiscountAmount().toPlainString());
        out.printf("  Total Paid       : $%s%n", order.getFinalTotal().toPlainString());
    }
}
