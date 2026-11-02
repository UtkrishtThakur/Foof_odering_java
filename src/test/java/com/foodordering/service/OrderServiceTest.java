package com.foodordering.service;

import com.foodordering.discount.Discountable;
import com.foodordering.discount.FlatDiscount;
import com.foodordering.discount.PercentageDiscount;
import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
import com.foodordering.exception.OrderNotFoundException;
import com.foodordering.exception.PaymentFailedException;
import com.foodordering.model.Cart;
import com.foodordering.model.Customer;
import com.foodordering.model.MenuItem;
import com.foodordering.model.Order;
import com.foodordering.model.OrderItem;
import com.foodordering.model.OrderStatus;
import com.foodordering.model.PaymentStatus;
import com.foodordering.payment.CashPayment;
import com.foodordering.payment.OnlinePayment;
import com.foodordering.payment.Payment;
import com.foodordering.repository.MenuRepository;
import com.foodordering.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Management & Lifecycle Tests")
class OrderServiceTest {

    private MenuRepository menuRepository;
    private MenuService menuService;
    private CartService cartService;
    private OrderRepository orderRepository;
    private OrderService orderService;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        menuRepository = new MenuRepository();
        menuService = new MenuService(menuRepository);
        cartService = new CartService(menuService);
        orderRepository = new OrderRepository();
        orderService = new OrderService(orderRepository, menuService, cartService);

        testCustomer = new Customer("C100", "Diana Prince", "diana@example.com",
                "pwd_hash", "Themyscira Way");

        // Seed menu
        menuService.addItem("M001", "Margherita Pizza", "Pizza", new BigDecimal("10.00"), 10, true);
        menuService.addItem("M002", "Veggie Burger", "Burgers", new BigDecimal("8.00"), 5, true);
        menuService.addItem("M003", "Soda", "Drinks", new BigDecimal("2.50"), 20, true);
    }

    @Nested
    @DisplayName("OrderItem Snapshot Tests")
    class OrderItemTests {

        @Test
        @DisplayName("Should create immutable snapshot correctly")
        void shouldCreateSnapshot() {
            OrderItem item = new OrderItem("M001", "Margherita Pizza", new BigDecimal("10.00"), 3);

            assertEquals("M001", item.getMenuItemId());
            assertEquals("Margherita Pizza", item.getItemName());
            assertEquals(new BigDecimal("10.00"), item.getUnitPrice());
            assertEquals(3, item.getQuantity());
            assertEquals(new BigDecimal("30.00"), item.getSubtotal());
        }

        @Test
        @DisplayName("Should reject invalid OrderItem fields")
        void shouldRejectInvalidFields() {
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("", "Item", new BigDecimal("10.00"), 1));
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("M001", "", new BigDecimal("10.00"), 1));
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("M001", "Item", null, 1));
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("M001", "Item", new BigDecimal("-1.00"), 1));
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("M001", "Item", new BigDecimal("10.00"), 0));
            assertThrows(InvalidInputException.class,
                    () -> new OrderItem("M001", "Item", new BigDecimal("10.00"), -5));
        }
    }

    @Nested
    @DisplayName("Order Model Tests")
    class OrderModelTests {

        @Test
        @DisplayName("Should validate totals and cap discount at subtotal")
        void shouldCalculateTotalsCorrectly() {
            OrderItem item1 = new OrderItem("M001", "Pizza", new BigDecimal("10.00"), 2); // $20.00
            OrderItem item2 = new OrderItem("M002", "Burger", new BigDecimal("8.00"), 1);  // $8.00

            Order order = new Order("C100", List.of(item1, item2), new BigDecimal("5.00"),
                    OrderStatus.CONFIRMED, PaymentStatus.PAID, "TXN-123");

            assertEquals(new BigDecimal("28.00"), order.getSubtotal());
            assertEquals(new BigDecimal("5.00"), order.getDiscountAmount());
            assertEquals(new BigDecimal("23.00"), order.getFinalTotal());
            assertEquals(2, order.lineCount());
        }

        @Test
        @DisplayName("Should cap discount when discount exceeds subtotal")
        void shouldCapDiscountAtSubtotal() {
            OrderItem item = new OrderItem("M001", "Pizza", new BigDecimal("10.00"), 1); // $10.00

            Order order = new Order("C100", List.of(item), new BigDecimal("15.00"),
                    OrderStatus.CONFIRMED, PaymentStatus.PAID, null);

            assertEquals(new BigDecimal("10.00"), order.getSubtotal());
            assertEquals(new BigDecimal("10.00"), order.getDiscountAmount());
            assertEquals(new BigDecimal("0.00"), order.getFinalTotal());
        }

        @Test
        @DisplayName("Should reject empty order items list")
        void shouldRejectEmptyItems() {
            assertThrows(InvalidInputException.class,
                    () -> new Order("C100", List.of(), BigDecimal.ZERO,
                            OrderStatus.CONFIRMED, PaymentStatus.PAID, null));
        }
    }

    @Nested
    @DisplayName("Order Placement Workflow Tests")
    class OrderPlacementTests {

        @Test
        @DisplayName("Should successfully place order with Cash payment and discount")
        void shouldPlaceOrderWithCashPayment() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M001", 2); // $20.00
            cartService.addItem(cart, "M002", 1); // $8.00
            // Subtotal: $28.00

            Discountable discount10 = new PercentageDiscount(new BigDecimal("10")); // 10% off -> $25.20
            BigDecimal finalTotal = discount10.applyDiscount(new BigDecimal("28.00"));
            Payment cashPayment = new CashPayment(finalTotal);

            Order order = orderService.placeOrder(testCustomer, cart, discount10, cashPayment);

            assertNotNull(order.getOrderId());
            assertEquals(OrderStatus.CONFIRMED, order.getStatus());
            assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
            assertEquals(new BigDecimal("28.00"), order.getSubtotal());
            assertEquals(new BigDecimal("2.80"), order.getDiscountAmount());
            assertEquals(new BigDecimal("25.20"), order.getFinalTotal());

            // Cart should be cleared
            assertTrue(cart.isEmpty());

            // Stock should be deducted
            assertEquals(8, menuService.findById("M001").getStockQuantity());
            assertEquals(4, menuService.findById("M002").getStockQuantity());

            // Order must be saved in repository
            assertNotNull(orderRepository.findById(order.getOrderId()));
        }

        @Test
        @DisplayName("Should successfully place order with Online payment and capture transaction reference")
        void shouldPlaceOrderWithOnlinePayment() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M003", 2); // $5.00

            Payment onlinePayment = new OnlinePayment(new BigDecimal("5.00"), true);
            Order order = orderService.placeOrder(testCustomer, cart, null, onlinePayment);

            assertEquals(OrderStatus.CONFIRMED, order.getStatus());
            assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
            assertNotNull(order.getTransactionReference());
            assertTrue(order.getTransactionReference().startsWith("TXN-SIM-"));
            assertTrue(cart.isEmpty());
        }

        @Test
        @DisplayName("Should preserve cart and stock when payment fails")
        void shouldPreserveStateOnPaymentFailure() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M001", 3); // 3 pizzas

            int initialStock = menuService.findById("M001").getStockQuantity();
            Payment failingPayment = new OnlinePayment(new BigDecimal("30.00"), false); // configured to fail

            assertThrows(PaymentFailedException.class,
                    () -> orderService.placeOrder(testCustomer, cart, null, failingPayment));

            // Cart must NOT be cleared
            assertEquals(1, cart.lineCount());
            assertEquals(3, cart.getItems().get("M001").getQuantity());

            // Inventory must NOT be deducted
            assertEquals(initialStock, menuService.findById("M001").getStockQuantity());

            // Order repository must have no orders
            assertEquals(0, orderRepository.count());
        }

        @Test
        @DisplayName("Should preserve historical price even after live menu price update")
        void shouldPreserveHistoricalPriceSnapshot() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M001", 1); // $10.00

            Order order = orderService.placeOrder(testCustomer, cart, null, new CashPayment(new BigDecimal("10.00")));
            assertEquals(new BigDecimal("10.00"), order.getFinalTotal());

            // Mutate live menu price
            menuService.updatePrice("M001", new BigDecimal("25.00"));
            assertEquals(new BigDecimal("25.00"), menuService.findById("M001").getPrice());

            // Historical order price must be untouched
            OrderItem historicalItem = order.getItems().get(0);
            assertEquals(new BigDecimal("10.00"), historicalItem.getUnitPrice());
            assertEquals(new BigDecimal("10.00"), order.getFinalTotal());
        }

        @Test
        @DisplayName("Should reject order when payment amount does not match calculated total")
        void shouldRejectPaymentAmountMismatch() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M001", 1); // $10.00

            Payment wrongAmountPayment = new CashPayment(new BigDecimal("5.00"));

            assertThrows(InvalidInputException.class,
                    () -> orderService.placeOrder(testCustomer, cart, null, wrongAmountPayment));
        }

        @Test
        @DisplayName("Should reject order when menu item has insufficient stock")
        void shouldRejectInsufficientStock() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M002", 5); // 5 in stock

            // Reduce stock behind the scenes
            menuService.reduceStock("M002", 2); // now only 3 in stock

            assertThrows(InsufficientStockException.class,
                    () -> orderService.placeOrder(testCustomer, cart, null, new CashPayment(new BigDecimal("40.00"))));
        }

        @Test
        @DisplayName("Should reject order for empty cart")
        void shouldRejectEmptyCart() {
            Cart emptyCart = cartService.createCart(testCustomer);
            assertThrows(InvalidInputException.class,
                    () -> orderService.placeOrder(testCustomer, emptyCart, null, new CashPayment(BigDecimal.ZERO)));
        }
    }

    @Nested
    @DisplayName("Order Status Lifecycle Tests")
    class LifecycleTests {

        private Order sampleOrder;

        @BeforeEach
        void createOrder() {
            Cart cart = cartService.createCart(testCustomer);
            cartService.addItem(cart, "M001", 1);
            sampleOrder = orderService.placeOrder(testCustomer, cart, null, new CashPayment(new BigDecimal("10.00")));
        }

        @Test
        @DisplayName("Should follow normal lifecycle: CONFIRMED -> PREPARING -> READY -> DELIVERED")
        void shouldFollowNormalLifecycle() {
            assertEquals(OrderStatus.CONFIRMED, sampleOrder.getStatus());

            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.PREPARING);
            assertEquals(OrderStatus.PREPARING, orderService.findById(sampleOrder.getOrderId()).getStatus());

            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.READY);
            assertEquals(OrderStatus.READY, orderService.findById(sampleOrder.getOrderId()).getStatus());

            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.DELIVERED);
            assertEquals(OrderStatus.DELIVERED, orderService.findById(sampleOrder.getOrderId()).getStatus());
        }

        @Test
        @DisplayName("Should allow cancellation from CONFIRMED")
        void shouldAllowCancellationFromConfirmed() {
            orderService.cancelOrder(sampleOrder.getOrderId());
            assertEquals(OrderStatus.CANCELLED, orderService.findById(sampleOrder.getOrderId()).getStatus());
        }

        @Test
        @DisplayName("Should allow cancellation from PREPARING and READY")
        void shouldAllowCancellationFromPreparingAndReady() {
            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.PREPARING);
            orderService.cancelOrder(sampleOrder.getOrderId());
            assertEquals(OrderStatus.CANCELLED, orderService.findById(sampleOrder.getOrderId()).getStatus());
        }

        @Test
        @DisplayName("Should reject transitions from terminal state DELIVERED")
        void shouldRejectTransitionsFromDelivered() {
            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.PREPARING);
            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.READY);
            orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.DELIVERED);

            assertThrows(InvalidInputException.class,
                    () -> orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.CANCELLED));
            assertThrows(InvalidInputException.class,
                    () -> orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.CONFIRMED));
        }

        @Test
        @DisplayName("Should reject transitions from terminal state CANCELLED")
        void shouldRejectTransitionsFromCancelled() {
            orderService.cancelOrder(sampleOrder.getOrderId());

            assertThrows(InvalidInputException.class,
                    () -> orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.CONFIRMED));
            assertThrows(InvalidInputException.class,
                    () -> orderService.updateOrderStatus(sampleOrder.getOrderId(), OrderStatus.DELIVERED));
        }

        @Test
        @DisplayName("Should throw OrderNotFoundException for non-existent order ID")
        void shouldThrowForNonExistentOrder() {
            assertThrows(OrderNotFoundException.class,
                    () -> orderService.findById("ORD-NON-EXISTENT"));
        }
    }
}
