package com.foodordering.service;

import com.foodordering.exception.DuplicateItemException;
import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.ItemNotFoundException;
import com.foodordering.model.Cart;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;
import com.foodordering.repository.MenuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MenuService and CartService Invariant & Business Rule Tests")
class MenuAndCartServiceTest {

    private MenuRepository menuRepository;
    private MenuService menuService;
    private CartService cartService;
    private Customer customer;

    @BeforeEach
    void setUp() {
        menuRepository = new MenuRepository();
        menuService = new MenuService(menuRepository);
        cartService = new CartService(menuService);
        customer = new Customer("C01", "Bob Customer", "bob@example.com", "hash", "Street 1");
    }

    @Nested
    @DisplayName("MenuService Tests")
    class MenuServiceTests {

        @Test
        @DisplayName("Should add menu items and prevent duplicate IDs")
        void shouldAddItemsAndPreventDuplicates() {
            menuService.addItem("M01", "Burger", "FastFood", new BigDecimal("5.99"), 10, true);
            assertEquals(1, menuService.listAll().size());

            assertThrows(DuplicateItemException.class, () ->
                    menuService.addItem("M01", "Duplicate Burger", "FastFood", new BigDecimal("6.99"), 5, true));
        }

        @Test
        @DisplayName("Should validate item creation parameters")
        void shouldValidateCreationParams() {
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("", "Name", "Cat", new BigDecimal("1.00"), 5, true));
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("M02", "", "Cat", new BigDecimal("1.00"), 5, true));
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("M02", "Name", "", new BigDecimal("1.00"), 5, true));
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("M02", "Name", "Cat", null, 5, true));
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("M02", "Name", "Cat", new BigDecimal("-1.00"), 5, true));
            assertThrows(InvalidInputException.class, () ->
                    menuService.addItem("M02", "Name", "Cat", new BigDecimal("1.00"), -1, true));
        }

        @Test
        @DisplayName("Should update item name, category, and price")
        void shouldUpdateAttributes() {
            menuService.addItem("M01", "Burger", "FastFood", new BigDecimal("5.99"), 10, true);

            menuService.updateName("M01", "Deluxe Burger");
            assertEquals("Deluxe Burger", menuService.findById("M01").getName());

            menuService.updateCategory("M01", "Gourmet");
            assertEquals("Gourmet", menuService.findById("M01").getCategory());

            menuService.updatePrice("M01", new BigDecimal("7.50"));
            assertEquals(new BigDecimal("7.50"), menuService.findById("M01").getPrice());
        }

        @Test
        @DisplayName("Should restock and reduce inventory with invariant checks")
        void shouldManageStockCorrectly() {
            menuService.addItem("M01", "Burger", "FastFood", new BigDecimal("5.99"), 10, true);

            menuService.restockItem("M01", 5);
            assertEquals(15, menuService.findById("M01").getStockQuantity());

            menuService.reduceStock("M01", 3);
            assertEquals(12, menuService.findById("M01").getStockQuantity());

            // Reduce more than available stock
            assertThrows(InsufficientStockException.class, () -> menuService.reduceStock("M01", 50));
            // Non-positive restock amount
            assertThrows(InvalidInputException.class, () -> menuService.restockItem("M01", 0));
        }

        @Test
        @DisplayName("Should search by name, filter by category, and list available items")
        void shouldSearchAndFilter() {
            menuService.addItem("M01", "Margherita Pizza", "Pizza", new BigDecimal("8.00"), 5, true);
            menuService.addItem("M02", "Pepperoni Pizza", "Pizza", new BigDecimal("10.00"), 5, false); // unavailable
            menuService.addItem("M03", "Veggie Burger", "Burgers", new BigDecimal("6.00"), 5, true);

            List<MenuItem> pizzas = menuService.searchByName("pizza");
            assertEquals(2, pizzas.size());

            List<MenuItem> burgers = menuService.filterByCategory("Burgers");
            assertEquals(1, burgers.size());

            List<MenuItem> available = menuService.listAvailable();
            assertEquals(2, available.size()); // M01 and M03 only
        }

        @Test
        @DisplayName("Should remove menu items")
        void shouldRemoveItem() {
            menuService.addItem("M01", "Burger", "FastFood", new BigDecimal("5.99"), 10, true);
            menuService.removeItem("M01");

            assertEquals(0, menuService.listAll().size());
            assertThrows(ItemNotFoundException.class, () -> menuService.findById("M01"));
        }
    }

    @Nested
    @DisplayName("CartService Tests")
    class CartServiceTests {

        @BeforeEach
        void seedMenu() {
            menuService.addItem("M01", "Pizza", "Pizza", new BigDecimal("10.00"), 5, true);
            menuService.addItem("M02", "Hidden Cake", "Dessert", new BigDecimal("4.00"), 10, false); // unavailable
        }

        @Test
        @DisplayName("Should add item to cart, merge quantities, and compute subtotal")
        void shouldAddItemAndMergeQuantities() {
            Cart cart = cartService.createCart(customer);
            assertTrue(cart.isEmpty());

            cartService.addItem(cart, "M01", 2); // 2 x 10.00 = 20.00
            assertEquals(1, cart.lineCount());
            assertEquals(new BigDecimal("20.00"), cartService.calculateSubtotal(cart));

            // Merge quantity
            cartService.addItem(cart, "M01", 1); // 3 x 10.00 = 30.00
            assertEquals(1, cart.lineCount());
            assertEquals(3, cart.getItems().get("M01").getQuantity());
            assertEquals(new BigDecimal("30.00"), cartService.calculateSubtotal(cart));
        }

        @Test
        @DisplayName("Should reject adding unavailable item or exceeding available stock")
        void shouldRejectUnavailableOrExcessStock() {
            Cart cart = cartService.createCart(customer);

            // Unavailable item
            assertThrows(InvalidInputException.class, () -> cartService.addItem(cart, "M02", 1));

            // Exceeds stock (stock is 5)
            assertThrows(InsufficientStockException.class, () -> cartService.addItem(cart, "M01", 6));

            // Exceeds stock via merge
            cartService.addItem(cart, "M01", 3);
            assertThrows(InsufficientStockException.class, () -> cartService.addItem(cart, "M01", 3)); // total 6 > 5
        }

        @Test
        @DisplayName("Should update item quantity and remove item")
        void shouldUpdateAndRemoveCartItem() {
            Cart cart = cartService.createCart(customer);
            cartService.addItem(cart, "M01", 2);

            cartService.updateQuantity(cart, "M01", 4);
            assertEquals(4, cart.getItems().get("M01").getQuantity());
            assertEquals(new BigDecimal("40.00"), cartService.calculateSubtotal(cart));

            cartService.removeItem(cart, "M01");
            assertTrue(cart.isEmpty());
            assertEquals(new BigDecimal("0.00"), cartService.calculateSubtotal(cart));
        }

        @Test
        @DisplayName("Should clear all cart contents")
        void shouldClearCart() {
            Cart cart = cartService.createCart(customer);
            cartService.addItem(cart, "M01", 2);
            assertFalse(cart.isEmpty());

            cartService.clearCart(cart);
            assertTrue(cart.isEmpty());
        }
    }
}
