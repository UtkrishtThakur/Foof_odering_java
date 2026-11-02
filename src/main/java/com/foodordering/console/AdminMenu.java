package com.foodordering.console;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.exception.OrderNotFoundException;
import com.foodordering.model.Admin;
import com.foodordering.model.MenuItem;
import com.foodordering.model.Order;
import com.foodordering.model.OrderItem;
import com.foodordering.model.OrderStatus;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Interactive console menu and workflow controller for authenticated administrators.
 */
public class AdminMenu {

    private final Admin admin;
    private final MenuService menuService;
    private final OrderService orderService;
    private final ConsoleInput input;
    private final PrintStream out;

    public AdminMenu(Admin admin,
                     MenuService menuService,
                     OrderService orderService,
                     ConsoleInput input,
                     PrintStream out) {
        this.admin = admin;
        this.menuService = menuService;
        this.orderService = orderService;
        this.input = input;
        this.out = (out != null) ? out : System.out;
    }

    /**
     * Executes the admin interactive loop until the administrator logs out.
     */
    public void run() {
        boolean active = true;
        while (active) {
            displayMenu();
            int choice = input.readIntInRange("  Select an option [1-12]: ", 1, 12);
            out.println();

            switch (choice) {
                case 1 -> addMenuItem();
                case 2 -> viewAllMenuItems();
                case 3 -> searchMenuItems();
                case 4 -> updateMenuItem();
                case 5 -> removeMenuItem();
                case 6 -> restockInventory();
                case 7 -> updateAvailability();
                case 8 -> viewAllOrders();
                case 9 -> findOrderById();
                case 10 -> filterOrdersByStatus();
                case 11 -> updateOrderStatus();
                case 12 -> {
                    out.printf("  Logging out Administrator %s. Session ended.%n", admin.getName());
                    active = false;
                }
            }
            if (active && choice != 12) {
                input.readLine("\n  Press [Enter] to return to admin dashboard...");
            }
        }
    }

    private void displayMenu() {
        out.println();
        out.println("========================================================");
        out.printf("  ADMINISTRATOR DASHBOARD — Logged in: %s (%s)%n", admin.getName(), admin.getUserId());
        out.printf("  Role: ADMINISTRATOR | Menu Items: %d | Total Orders: %d%n",
                menuService.listAll().size(), orderService.listAll().size());
        out.println("========================================================");
        out.println("  1.  Add New Menu Item");
        out.println("  2.  View All Menu Items");
        out.println("  3.  Search Menu Items");
        out.println("  4.  Update Menu Item (Name, Category, Price)");
        out.println("  5.  Remove Menu Item");
        out.println("  6.  Restock Item Inventory");
        out.println("  7.  Update Item Availability");
        out.println("  8.  View All Customer Orders");
        out.println("  9.  Find Order by ID");
        out.println("  10. Filter Orders by Status");
        out.println("  11. Update Order Lifecycle Status");
        out.println("  12. Logout");
        out.println("========================================================");
    }

    private void addMenuItem() {
        out.println("--- Add New Menu Item ---");
        String id = input.readRequiredLine("  Enter Menu Item ID (e.g. M010): ");
        String name = input.readRequiredLine("  Enter Item Name: ");
        String category = input.readRequiredLine("  Enter Category: ");
        BigDecimal price = input.readNonNegativeBigDecimal("  Enter Unit Price: $");
        int stock = input.readIntInRange("  Enter Initial Stock Quantity (>= 0): ", 0, Integer.MAX_VALUE);
        boolean available = input.readBoolean("  Make item available immediately? (y/n): ");

        try {
            MenuItem created = menuService.addItem(id, name, category, price, stock, available);
            out.printf("  [Success] Menu item '%s' (ID: %s) added successfully.%n",
                    created.getName(), created.getId());
        } catch (DuplicateItemException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void viewAllMenuItems() {
        out.println("--- All Menu Items (Admin View) ---");
        List<MenuItem> items = menuService.listAll();
        printMenuItemsAdmin(items);
    }

    private void searchMenuItems() {
        out.println("--- Search Menu Items ---");
        String keyword = input.readRequiredLine("  Enter search keyword: ");
        try {
            List<MenuItem> matches = menuService.searchByName(keyword);
            printMenuItemsAdmin(matches);
        } catch (InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void updateMenuItem() {
        out.println("--- Update Menu Item Details ---");
        String id = input.readRequiredLine("  Enter Item ID to update: ");
        try {
            MenuItem item = menuService.findById(id);
            out.printf("  Target Item: %s (Current: Name='%s', Category='%s', Price=$%s)%n",
                    item.getId(), item.getName(), item.getCategory(), item.getPrice().toPlainString());

            out.println("  Choose attribute to update:");
            out.println("    1. Update Name");
            out.println("    2. Update Category");
            out.println("    3. Update Price");
            int field = input.readIntInRange("  Select option [1-3]: ", 1, 3);

            switch (field) {
                case 1 -> {
                    String newName = input.readRequiredLine("  Enter new name: ");
                    menuService.updateName(id, newName);
                    out.println("  [Success] Name updated successfully.");
                }
                case 2 -> {
                    String newCat = input.readRequiredLine("  Enter new category: ");
                    menuService.updateCategory(id, newCat);
                    out.println("  [Success] Category updated successfully.");
                }
                case 3 -> {
                    BigDecimal newPrice = input.readNonNegativeBigDecimal("  Enter new unit price: $");
                    menuService.updatePrice(id, newPrice);
                    out.println("  [Success] Price updated successfully.");
                }
            }
        } catch (ItemNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void removeMenuItem() {
        out.println("--- Remove Menu Item ---");
        String id = input.readRequiredLine("  Enter Item ID to remove: ");
        try {
            MenuItem item = menuService.findById(id);
            boolean confirm = input.readBoolean(String.format(
                    "  Confirm removal of '%s' (ID: %s)? (y/n): ", item.getName(), item.getId()));
            if (confirm) {
                menuService.removeItem(id);
                out.println("  [Success] Menu item removed.");
            } else {
                out.println("  Removal cancelled.");
            }
        } catch (ItemNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void restockInventory() {
        out.println("--- Restock Inventory ---");
        String id = input.readRequiredLine("  Enter Item ID: ");
        try {
            MenuItem item = menuService.findById(id);
            out.printf("  Current stock for '%s': %d unit(s)%n", item.getName(), item.getStockQuantity());
            int amount = input.readPositiveInt("  Enter quantity to add (> 0): ");
            menuService.restockItem(id, amount);
            out.printf("  [Success] Added %d units. New stock: %d%n",
                    amount, menuService.findById(id).getStockQuantity());
        } catch (ItemNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void updateAvailability() {
        out.println("--- Update Item Availability ---");
        String id = input.readRequiredLine("  Enter Item ID: ");
        try {
            MenuItem item = menuService.findById(id);
            out.printf("  Current availability for '%s': %b%n", item.getName(), item.isAvailable());
            boolean avail = input.readBoolean("  Set available to customers? (y/n): ");
            menuService.updateAvailability(id, avail);
            out.printf("  [Success] Availability set to: %b%n", avail);
        } catch (ItemNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void viewAllOrders() {
        out.println("--- All Customer Orders ---");
        List<Order> orders = orderService.listAll();
        if (orders.isEmpty()) {
            out.println("  No orders have been recorded in the system yet.");
            return;
        }

        printOrderSummaryTable(orders);
    }

    private void findOrderById() {
        out.println("--- Find Order by ID ---");
        String id = input.readRequiredLine("  Enter Order ID: ");
        try {
            Order order = orderService.findById(id);
            printDetailedOrder(order);
        } catch (OrderNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void filterOrdersByStatus() {
        out.println("--- Filter Orders by Lifecycle Status ---");
        out.println("  Available Statuses: 1. PENDING, 2. CONFIRMED, 3. PREPARING, 4. READY, 5. DELIVERED, 6. CANCELLED");
        int opt = input.readIntInRange("  Select status [1-6]: ", 1, 6);

        OrderStatus status = switch (opt) {
            case 1 -> OrderStatus.PENDING;
            case 2 -> OrderStatus.CONFIRMED;
            case 3 -> OrderStatus.PREPARING;
            case 4 -> OrderStatus.READY;
            case 5 -> OrderStatus.DELIVERED;
            case 6 -> OrderStatus.CANCELLED;
            default -> throw new IllegalStateException();
        };

        List<Order> matches = orderService.findByStatus(status);
        out.printf("  Orders with status [%s]: %d%n", status, matches.size());
        if (!matches.isEmpty()) {
            printOrderSummaryTable(matches);
        }
    }

    private void updateOrderStatus() {
        out.println("--- Update Order Lifecycle Status ---");
        String id = input.readRequiredLine("  Enter Order ID: ");
        try {
            Order order = orderService.findById(id);
            out.printf("  Order ID: %s | Customer: %s | Current Status: %s%n",
                    order.getOrderId(), order.getCustomerId(), order.getStatus());

            if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
                out.printf("  [Note] Order is in terminal state '%s'. No further transitions permitted.%n",
                        order.getStatus());
                return;
            }

            out.println("  Choose new status:");
            out.println("    1. CONFIRMED");
            out.println("    2. PREPARING");
            out.println("    3. READY");
            out.println("    4. DELIVERED");
            out.println("    5. CANCELLED");
            int opt = input.readIntInRange("  Select target status [1-5]: ", 1, 5);

            OrderStatus target = switch (opt) {
                case 1 -> OrderStatus.CONFIRMED;
                case 2 -> OrderStatus.PREPARING;
                case 3 -> OrderStatus.READY;
                case 4 -> OrderStatus.DELIVERED;
                case 5 -> OrderStatus.CANCELLED;
                default -> throw new IllegalStateException();
            };

            orderService.updateOrderStatus(id, target);
            out.printf("  [Success] Order '%s' status transitioned from %s to %s.%n",
                    id, order.getStatus(), target);
        } catch (OrderNotFoundException | InvalidInputException e) {
            out.printf("  [Error] %s%n", e.getMessage());
        }
    }

    private void printMenuItemsAdmin(List<MenuItem> items) {
        if (items.isEmpty()) {
            out.println("  (No items found)");
            return;
        }
        out.println("  ---------------------------------------------------------------------------------------------");
        out.printf("  %-6s | %-24s | %-12s | %-8s | %-7s | %-9s%n",
                "ID", "Name", "Category", "Price", "Stock", "Available");
        out.println("  ---------------------------------------------------------------------------------------------");
        for (MenuItem item : items) {
            out.printf("  %-6s | %-24s | %-12s | $%7s | %5d | %-9s%n",
                    item.getId(),
                    item.getName(),
                    item.getCategory(),
                    item.getPrice().toPlainString(),
                    item.getStockQuantity(),
                    item.isAvailable() ? "YES" : "NO (Hidden)");
        }
        out.println("  ---------------------------------------------------------------------------------------------");
    }

    private void printOrderSummaryTable(List<Order> orders) {
        out.println("  ---------------------------------------------------------------------------------------------------------");
        out.printf("  %-12s | %-10s | %-19s | %-11s | %-8s | %-10s%n",
                "Order ID", "Customer", "Date / Time", "Status", "Items", "Total Paid");
        out.println("  ---------------------------------------------------------------------------------------------------------");
        for (Order o : orders) {
            out.printf("  %-12s | %-10s | %-19s | %-11s | %-8d | $%s%n",
                    o.getOrderId(),
                    o.getCustomerId(),
                    o.getCreatedAt().toString().replace('T', ' ').substring(0, Math.min(19, o.getCreatedAt().toString().length())),
                    o.getStatus(),
                    o.getItems().size(),
                    o.getFinalTotal().toPlainString());
        }
        out.println("  ---------------------------------------------------------------------------------------------------------");
    }

    private void printDetailedOrder(Order order) {
        out.println("========================================================");
        out.printf("  Order ID         : %s%n", order.getOrderId());
        out.printf("  Customer ID      : %s%n", order.getCustomerId());
        out.printf("  Created Timestamp: %s%n", order.getCreatedAt());
        out.printf("  Lifecycle Status : %s%n", order.getStatus());
        out.printf("  Payment Status   : %s%n", order.getPaymentStatus());
        if (order.getTransactionReference() != null) {
            out.printf("  Payment Txn Ref  : %s%n", order.getTransactionReference());
        }
        out.println("  Purchased Snapshot Items:");
        for (OrderItem item : order.getItems()) {
            out.printf("    - [%s] %-22s | unit: $%s | qty: %2d | subtotal: $%s%n",
                    item.getMenuItemId(), item.getItemName(),
                    item.getUnitPrice().toPlainString(), item.getQuantity(),
                    item.getSubtotal().toPlainString());
        }
        out.printf("  Subtotal         : $%s%n", order.getSubtotal().toPlainString());
        out.printf("  Discount Deducted: -$%s%n", order.getDiscountAmount().toPlainString());
        out.printf("  Final Total      : $%s%n", order.getFinalTotal().toPlainString());
        out.println("========================================================");
    }
}
