package com.foodordering.service;

import com.foodordering.discount.Discountable;
import com.foodordering.exception.InsufficientStockException;
import com.foodordering.exception.InvalidInputException;
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
import com.foodordering.payment.OnlinePayment;
import com.foodordering.payment.Payment;
import com.foodordering.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Service layer orchestrating order placement, checkout validation, inventory deduction,
 * and order lifecycle status transitions.
 *
 * <p>Consistency & Transactional Guarantees:
 * <ul>
 *   <li><strong>Snapshot Integrity:</strong> Line item prices and names are re-fetched from the
 *       authoritative {@link MenuService} at order placement time and stored as immutable
 *       {@link OrderItem} snapshots. Stored prices in the cart are not blindly trusted.</li>
 *   <li><strong>Deferred Deduction:</strong> Menu inventory is never modified before payment
 *       processing succeeds. A failed payment leaves cart and stock completely untouched.</li>
 *   <li><strong>Atomic-Style Compensation:</strong> If an inventory deduction or repository save fails
 *       mid-flight, already-deducted inventory is rolled back via {@link MenuService#restockItem(String, int)}.</li>
 *   <li><strong>Cart Preservation on Failure:</strong> The customer's cart is cleared only after
 *       the order is successfully created, paid, and persisted.</li>
 *   <li><strong>Architectural Limitation Note:</strong> In-memory repositories and standard CSV file
 *       operations cannot guarantee full multi-resource ACID guarantees in catastrophic crash
 *       scenarios; the service provides best-effort application-level consistency and compensation.</li>
 * </ul>
 * </p>
 */
public class OrderService {

    private static final int SCALE = 2;

    private final OrderRepository orderRepository;
    private final MenuService menuService;
    private final CartService cartService;

    /**
     * Constructs an OrderService wired with required repositories and services.
     *
     * @param orderRepository order storage repository; must not be null
     * @param menuService     menu and inventory service; must not be null
     * @param cartService     shopping cart service; must not be null
     */
    public OrderService(OrderRepository orderRepository, MenuService menuService, CartService cartService) {
        if (orderRepository == null) {
            throw new InvalidInputException("OrderRepository must not be null.");
        }
        if (menuService == null) {
            throw new InvalidInputException("MenuService must not be null.");
        }
        if (cartService == null) {
            throw new InvalidInputException("CartService must not be null.");
        }
        this.orderRepository = orderRepository;
        this.menuService = menuService;
        this.cartService = cartService;
    }

    // -------------------------------------------------------------------------
    // Order Placement Workflow
    // -------------------------------------------------------------------------

    /**
     * Executes the controlled order placement workflow.
     *
     * <ol>
     *   <li>Validates customer, cart, and payment method.</li>
     *   <li>Rejects an empty cart.</li>
     *   <li>Reads current cart lines and revalidates item availability and stock via {@link MenuService}.</li>
     *   <li>Constructs immutable {@link OrderItem} snapshots using current menu prices and quantities.</li>
     *   <li>Calculates subtotal from snapshot prices.</li>
     *   <li>Applies discount strategy (if provided) and verifies final total is non-negative and $\le$ subtotal.</li>
     *   <li>Ensures payment amount matches the calculated final total.</li>
     *   <li>Processes payment.</li>
     *   <li>If payment succeeds: deducts inventory, creates/saves order, and clears the cart.</li>
     *   <li>If payment fails: leaves inventory and cart untouched, and throws {@link PaymentFailedException}.</li>
     * </ol>
     *
     * @param customer customer placing the order; must not be null
     * @param cart     customer's shopping cart; must not be null or empty
     * @param discount optional discount strategy; may be null
     * @param payment  payment method configured for the exact order total; must not be null
     * @return the successfully placed and confirmed {@link Order}
     * @throws InvalidInputException      if inputs or payment amount do not match calculated total
     * @throws InsufficientStockException if requested quantity exceeds current menu stock
     * @throws PaymentFailedException     if payment processing is declined/failed
     */
    public Order placeOrder(Customer customer, Cart cart, Discountable discount, Payment payment) {
        // 1. Validate inputs
        if (customer == null) {
            throw new InvalidInputException("Customer must not be null.");
        }
        if (cart == null) {
            throw new InvalidInputException("Cart must not be null.");
        }
        if (payment == null) {
            throw new InvalidInputException("Payment method must not be null.");
        }
        if (!customer.getUserId().equals(cart.getCustomerId())) {
            throw new InvalidInputException(String.format(
                    "Cart customer ID '%s' does not match placing customer ID '%s'.",
                    cart.getCustomerId(), customer.getUserId()));
        }

        // 2. Reject empty cart
        if (cart.isEmpty()) {
            throw new InvalidInputException("Cannot place an order with an empty cart.");
        }

        // 3. Read current cart lines
        Collection<CartItem> cartItems = cart.getItems().values();

        // 4 & 5. Revalidate availability, stock, and live prices via MenuService
        List<OrderItem> orderSnapshots = new ArrayList<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        for (CartItem line : cartItems) {
            String itemId = line.getMenuItem().getId();
            int qty = line.getQuantity();

            // Re-fetch authoritative live menu item (throws ItemNotFoundException if removed)
            MenuItem liveItem = menuService.findById(itemId);

            if (!liveItem.isAvailable()) {
                throw new InvalidInputException(String.format(
                        "Menu item '%s' (ID: %s) is currently marked unavailable.",
                        liveItem.getName(), itemId));
            }

            if (liveItem.getStockQuantity() < qty) {
                throw new InsufficientStockException(String.format(
                        "Insufficient stock for '%s' (ID: %s). Available: %d, requested: %d.",
                        liveItem.getName(), itemId, liveItem.getStockQuantity(), qty));
            }

            // 6. Create immutable historical OrderItem snapshot using live price
            OrderItem snapshot = new OrderItem(liveItem.getId(), liveItem.getName(), liveItem.getPrice(), qty);
            orderSnapshots.add(snapshot);

            // 7. Accumulate subtotal from snapshot line totals
            calculatedSubtotal = calculatedSubtotal.add(snapshot.getSubtotal());
        }

        calculatedSubtotal = calculatedSubtotal.setScale(SCALE, RoundingMode.HALF_UP);

        // 8. Apply discount strategy if supplied
        BigDecimal finalTotal = calculatedSubtotal;
        BigDecimal discountDeducted = BigDecimal.ZERO;

        if (discount != null) {
            finalTotal = discount.applyDiscount(calculatedSubtotal);
            discountDeducted = calculatedSubtotal.subtract(finalTotal).setScale(SCALE, RoundingMode.HALF_UP);
        }

        // 9. Validate final total range: 0 <= finalTotal <= subtotal
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0 || finalTotal.compareTo(calculatedSubtotal) > 0) {
            throw new InvalidInputException(String.format(
                    "Discounted final total ($%s) must be between 0 and subtotal ($%s).",
                    finalTotal.toPlainString(), calculatedSubtotal.toPlainString()));
        }

        // 10. Ensure payment amount matches calculated final total
        if (payment.getAmount().compareTo(finalTotal) != 0) {
            throw new InvalidInputException(String.format(
                    "Payment amount ($%s) does not match calculated final order total ($%s).",
                    payment.getAmount().toPlainString(), finalTotal.toPlainString()));
        }

        // 11. Process payment
        PaymentStatus paymentResult = payment.processPayment();

        // 13. Handle payment failure
        if (paymentResult != PaymentStatus.PAID) {
            // Do not deduct stock, do not save order, do not clear cart
            throw new PaymentFailedException(String.format(
                    "Payment of $%s failed or was declined. Cart contents and inventory have been preserved.",
                    finalTotal.toPlainString()));
        }

        // 12. Payment succeeded: Deduct inventory with rollback compensation
        List<OrderItem> successfullyDeducted = new ArrayList<>();
        try {
            for (OrderItem snapshot : orderSnapshots) {
                menuService.reduceStock(snapshot.getMenuItemId(), snapshot.getQuantity());
                successfullyDeducted.add(snapshot);
            }
        } catch (Exception stockException) {
            // Compensate/rollback any inventory already reduced in this attempt
            rollbackInventory(successfullyDeducted);
            throw stockException;
        }

        // Capture transaction reference if available
        String transactionRef = null;
        if (payment instanceof OnlinePayment onlinePayment) {
            transactionRef = onlinePayment.getTransactionReference();
        }

        // Create and save Order
        Order order = new Order(
                customer.getUserId(),
                orderSnapshots,
                discountDeducted,
                OrderStatus.CONFIRMED,
                PaymentStatus.PAID,
                transactionRef
        );

        try {
            orderRepository.save(order);
        } catch (Exception repoException) {
            // Compensate inventory if repository persistence fails
            rollbackInventory(successfullyDeducted);
            throw repoException;
        }

        // Clear cart only after confirmed order creation
        cartService.clearCart(cart);

        return order;
    }

    /**
     * Overload accepting a payment factory that constructs a {@link Payment} configured
     * for the exact calculated order total.
     *
     * @param customer       customer placing order; must not be null
     * @param cart           customer cart; must not be null or empty
     * @param discount       optional discount strategy (may be null)
     * @param paymentFactory factory creating a payment for the calculated amount
     * @return the placed and confirmed {@link Order}
     */
    public Order placeOrder(Customer customer, Cart cart, Discountable discount,
                            Function<BigDecimal, Payment> paymentFactory) {
        if (paymentFactory == null) {
            throw new InvalidInputException("Payment factory must not be null.");
        }
        // Calculate total in advance to invoke the factory with exact amount
        BigDecimal subtotal = calculatePreviewSubtotal(cart);
        BigDecimal finalTotal = (discount != null) ? discount.applyDiscount(subtotal) : subtotal;
        Payment payment = paymentFactory.apply(finalTotal);
        return placeOrder(customer, cart, discount, payment);
    }

    // -------------------------------------------------------------------------
    // Order Status Lifecycle Management
    // -------------------------------------------------------------------------

    /**
     * Updates an order's lifecycle status following permitted business state transitions:
     * <ul>
     *   <li>{@code PENDING} &rarr; {@code CONFIRMED} or {@code CANCELLED}</li>
     *   <li>{@code CONFIRMED} &rarr; {@code PREPARING} or {@code CANCELLED}</li>
     *   <li>{@code PREPARING} &rarr; {@code READY} or {@code CANCELLED}</li>
     *   <li>{@code READY} &rarr; {@code DELIVERED} or {@code CANCELLED}</li>
     *   <li>{@code DELIVERED} &rarr; Terminal (no transitions permitted)</li>
     *   <li>{@code CANCELLED} &rarr; Terminal (no transitions permitted)</li>
     * </ul>
     *
     * @param orderId   unique ID of the order; must not be null or blank
     * @param newStatus desired new lifecycle status; must not be null
     * @throws InvalidInputException  if transition is illegal or order is in a terminal state
     * @throws OrderNotFoundException if order does not exist
     */
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidInputException("Order ID must not be null or blank.");
        }
        if (newStatus == null) {
            throw new InvalidInputException("New order status must not be null.");
        }

        Order order = orderRepository.findById(orderId);
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return; // idempotent
        }

        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new InvalidInputException(String.format(
                    "Invalid order status transition for order '%s': cannot transition from %s to %s.",
                    orderId, currentStatus, newStatus));
        }

        order.setStatus(newStatus);
        orderRepository.update(order);
    }

    /**
     * Cancels an order if its current lifecycle status permits cancellation.
     *
     * <p><strong>Cancellation Policy:</strong> Orders in {@code PENDING}, {@code CONFIRMED},
     * {@code PREPARING}, or {@code READY} states may be cancelled. Orders that have already
     * been {@code DELIVERED} cannot be cancelled. Automatic gateway refunds and automated
     * inventory restoration are not executed as part of this simulation stage.</p>
     *
     * @param orderId unique ID of the order to cancel
     * @throws InvalidInputException if the order is already delivered or cancelled
     */
    public void cancelOrder(String orderId) {
        updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }

    /**
     * Evaluates whether a lifecycle status transition is permitted.
     */
    private boolean isValidStatusTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.READY || next == OrderStatus.CANCELLED;
            case READY -> next == OrderStatus.DELIVERED || next == OrderStatus.CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Query Operations
    // -------------------------------------------------------------------------

    public Order findById(String orderId) {
        return orderRepository.findById(orderId);
    }

    public List<Order> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public List<Order> listAll() {
        return orderRepository.findAll();
    }

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    /**
     * Rollback helper to replenish previously deducted inventory during partial failure.
     */
    private void rollbackInventory(List<OrderItem> itemsToRollback) {
        for (OrderItem item : itemsToRollback) {
            try {
                menuService.restockItem(item.getMenuItemId(), item.getQuantity());
            } catch (Exception ex) {
                // Best-effort compensation in non-distributed environment
            }
        }
    }

    /**
     * Calculates preview subtotal using current live menu prices.
     */
    private BigDecimal calculatePreviewSubtotal(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cart.getItems().values()) {
            MenuItem live = menuService.findById(item.getMenuItem().getId());
            BigDecimal itemTotal = live.getPrice().setScale(SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            total = total.add(itemTotal);
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
