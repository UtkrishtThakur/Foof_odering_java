package com.foodordering.console;

import com.foodordering.exception.AuthenticationException;
import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.Admin;
import com.foodordering.model.Customer;
import com.foodordering.service.AuthService;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.OrderService;

import java.io.PrintStream;

/**
 * Top-level console application controller managing application startup,
 * initial first-run administrator provisioning, main authentication portal,
 * and delegation to customer and administrator dashboards.
 */
public class ConsoleApplication {

    private final MenuService menuService;
    private final CartService cartService;
    private final OrderService orderService;
    private final AuthService authService;
    private final ConsoleInput input;
    private final PrintStream out;

    public ConsoleApplication(MenuService menuService,
                              CartService cartService,
                              OrderService orderService,
                              AuthService authService) {
        this(menuService, cartService, orderService, authService, new ConsoleInput(), System.out);
    }

    public ConsoleApplication(MenuService menuService,
                              CartService cartService,
                              OrderService orderService,
                              AuthService authService,
                              ConsoleInput input,
                              PrintStream out) {
        this.menuService = menuService;
        this.cartService = cartService;
        this.orderService = orderService;
        this.authService = authService;
        this.input = input;
        this.out = (out != null) ? out : System.out;
    }

    /**
     * Starts the interactive main console application loop.
     */
    public void start() {
        banner();

        // 1. Check for configured bootstrap via environment or system properties
        authService.bootstrapAdminFromConfig();

        // 2. If still no administrator exists, run the interactive first-run provisioning wizard
        if (!authService.hasAdministrator()) {
            promptFirstRunAdminSetup();
        }

        // 3. Main interactive loop
        boolean running = true;
        while (running) {
            try {
                displayMainMenu();
                int choice = input.readIntInRange("  Enter your selection [1-4]: ", 1, 4);
                out.println();

                switch (choice) {
                    case 1 -> handleCustomerLogin();
                    case 2 -> handleCustomerRegistration();
                    case 3 -> handleAdminLogin();
                    case 4 -> {
                        out.println("  Thank you for using the Java Food Ordering System. Goodbye!");
                        running = false;
                    }
                }
            } catch (IllegalStateException e) {
                // Occurs when console input stream ends (e.g. non-interactive piping / EOF)
                out.printf("%n  Console input stream terminated: %s%n", e.getMessage());
                running = false;
            } catch (Exception e) {
                out.printf("  [System Error] %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
                out.println("  Returning to main menu...");
            }
        }
    }

    private void banner() {
        out.println();
        out.println("========================================================");
        out.println("           JAVA FOOD ORDERING SYSTEM (PART 8)           ");
        out.println("         Interactive Customer & Admin Console UI        ");
        out.println("========================================================");
    }

    private void promptFirstRunAdminSetup() {
        out.println();
        out.println("********************************************************");
        out.println("       FIRST-RUN ADMINISTRATOR PROVISIONING WIZARD       ");
        out.println("  No existing administrator was detected in storage.   ");
        out.println("  Please provision the initial system administrator.    ");
        out.println("********************************************************");

        boolean provisioned = false;
        while (!provisioned) {
            try {
                String userId = input.readRequiredLine("  Choose Administrator ID (no spaces): ");
                String name = input.readRequiredLine("  Enter Full Display Name: ");
                String email = input.readRequiredLine("  Enter Email Address: ");

                String password = input.readRequiredLine("  Enter Strong Password (min 8 chars): ");
                String confirmPassword = input.readRequiredLine("  Confirm Password: ");
                if (!password.equals(confirmPassword)) {
                    out.println("  [Error] Passwords do not match. Please restart setup.");
                    continue;
                }

                String code = input.readRequiredLine("  Enter Administrator Security Code (min 4 chars): ");
                String confirmCode = input.readRequiredLine("  Confirm Security Code: ");
                if (!code.equals(confirmCode)) {
                    out.println("  [Error] Security access codes do not match. Please restart setup.");
                    continue;
                }

                Admin admin = authService.registerAdmin(userId, name, email, password, code);
                out.println();
                out.printf("  [Success] Administrator '%s' provisioned and persisted successfully.%n", admin.getUserId());
                out.println("  Please keep your credentials secure. You may now log in.");
                out.println("********************************************************");
                provisioned = true;
            } catch (InvalidInputException | DuplicateItemException e) {
                out.printf("  [Setup Failed] %s%n", e.getMessage());
                boolean retry = input.readBoolean("  Would you like to try again? (y/n): ");
                if (!retry) {
                    out.println("  Administrator setup cancelled. System will operate in customer-only mode until provisioned.");
                    break;
                }
            }
        }
    }

    private void displayMainMenu() {
        out.println();
        out.println("------------------ [ MAIN PORTAL ] ------------------");
        out.println("  1. Customer Login");
        out.println("  2. Customer Registration (New Account)");
        out.println("  3. Administrator Login");
        out.println("  4. Exit Application");
        out.println("-----------------------------------------------------");
    }

    private void handleCustomerLogin() {
        out.println("--- Customer Authentication ---");
        String identifier = input.readRequiredLine("  Enter User ID or Email: ");
        String password = input.readRequiredLine("  Enter Password: ");

        try {
            Customer customer = authService.loginCustomer(identifier, password);
            out.printf("  [Success] Welcome back, %s!%n", customer.getName());
            CustomerMenu customerMenu = new CustomerMenu(
                    customer, menuService, cartService, orderService, input, out);
            customerMenu.run();
        } catch (AuthenticationException e) {
            out.printf("  [Login Failed] %s%n", e.getMessage());
        }
    }

    private void handleCustomerRegistration() {
        out.println("--- New Customer Account Registration ---");
        String userId = input.readRequiredLine("  Choose User ID (no spaces): ");
        String name = input.readRequiredLine("  Enter Full Display Name: ");
        String email = input.readRequiredLine("  Enter Email Address: ");
        String password = input.readRequiredLine("  Enter Password (min 6 chars): ");
        String address = input.readLine("  Enter Delivery Address (optional, press Enter to skip): ");

        try {
            Customer customer = authService.registerCustomer(userId, name, email, password, address);
            out.println();
            out.printf("  [Success] Account created successfully for %s (%s).%n", customer.getName(), customer.getUserId());
            boolean enterNow = input.readBoolean("  Log in to Customer Dashboard now? (y/n): ");
            if (enterNow) {
                CustomerMenu customerMenu = new CustomerMenu(
                        customer, menuService, cartService, orderService, input, out);
                customerMenu.run();
            }
        } catch (DuplicateItemException | InvalidInputException e) {
            out.printf("  [Registration Failed] %s%n", e.getMessage());
        }
    }

    private void handleAdminLogin() {
        out.println("--- Administrator Authentication ---");
        String identifier = input.readRequiredLine("  Enter Admin ID or Email: ");
        String password = input.readRequiredLine("  Enter Admin Password: ");
        String adminCode = input.readRequiredLine("  Enter Admin Access Code: ");

        try {
            Admin admin = authService.loginAdmin(identifier, password, adminCode);
            out.printf("  [Success] Administrator session initialized for: %s%n", admin.getName());
            AdminMenu adminMenu = new AdminMenu(admin, menuService, orderService, input, out);
            adminMenu.run();
        } catch (AuthenticationException e) {
            out.printf("  [Admin Login Failed] %s%n", e.getMessage());
        }
    }
}
