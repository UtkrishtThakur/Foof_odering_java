package com.foodordering.model;

import com.foodordering.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a customer order in the food ordering system.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Contains an unmodifiable collection of immutable {@link OrderItem} snapshots.</li>
 *   <li>Calculates totals consistently using {@link BigDecimal}:
 *     <ul>
 *       <li>{@code subtotal} = sum of all line item subtotals.</li>
 *       <li>{@code finalTotal} = {@code subtotal} − {@code discountAmount}.</li>
 *       <li>{@code discountAmount} = {@code subtotal} − {@code finalTotal}.</li>
 *     </ul>
 *   </li>
 *   <li>Tracks lifecycle status via {@link OrderStatus} and payment state via {@link PaymentStatus}.</li>
 *   <li>Stores creation timestamp and optional payment transaction reference.</li>
 * </ul>
 * </p>
 */
public class Order {

    private static final int SCALE = 2;

    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private final BigDecimal subtotal;
    private final BigDecimal discountAmount;
    private final BigDecimal finalTotal;
    private final LocalDateTime createdAt;
    private final String transactionReference;

    private OrderStatus status;
    private PaymentStatus paymentStatus;

    /**
     * Constructs a validated Order instance.
     *
     * @param orderId              unique order ID (if null or blank, auto-generates a UUID-based ID)
     * @param customerId           unique ID of the placing customer; must not be null or blank
     * @param items                collection of order item snapshots; must not be null or empty
     * @param discountAmount       monetary discount applied; must be non-null and non-negative
     * @param status               initial order lifecycle status; must not be null
     * @param paymentStatus        initial payment status; must not be null
     * @param createdAt            order creation timestamp (if null, defaults to {@code LocalDateTime.now()})
     * @param transactionReference optional transaction reference string (may be null)
     * @throws InvalidInputException if any validation rule is violated
     */
    public Order(String orderId,
                 String customerId,
                 List<OrderItem> items,
                 BigDecimal discountAmount,
                 OrderStatus status,
                 PaymentStatus paymentStatus,
                 LocalDateTime createdAt,
                 String transactionReference) {

        if (customerId == null || customerId.isBlank()) {
            throw new InvalidInputException("Customer ID must not be null or blank.");
        }
        if (items == null || items.isEmpty()) {
            throw new InvalidInputException("Order must contain at least one order item.");
        }
        if (discountAmount == null) {
            throw new InvalidInputException("Discount amount must not be null.");
        }
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException(
                    "Discount amount must not be negative: " + discountAmount.toPlainString());
        }
        if (status == null) {
            throw new InvalidInputException("Order status must not be null.");
        }
        if (paymentStatus == null) {
            throw new InvalidInputException("Payment status must not be null.");
        }

        this.orderId = (orderId == null || orderId.isBlank())
                ? "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : orderId.strip();
        this.customerId = customerId.strip();
        this.items = Collections.unmodifiableList(new ArrayList<>(items));

        // Calculate subtotal by summing each OrderItem's line subtotal
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;
        for (OrderItem item : this.items) {
            if (item == null) {
                throw new InvalidInputException("Order item collection must not contain null elements.");
            }
            calculatedSubtotal = calculatedSubtotal.add(item.getSubtotal());
        }
        this.subtotal = calculatedSubtotal.setScale(SCALE, RoundingMode.HALF_UP);

        // Cap discount amount at subtotal to prevent negative final total
        BigDecimal scaledDiscount = discountAmount.setScale(SCALE, RoundingMode.HALF_UP);
        if (scaledDiscount.compareTo(this.subtotal) > 0) {
            scaledDiscount = this.subtotal;
        }
        this.discountAmount = scaledDiscount;

        // Final total = subtotal - discountAmount
        this.finalTotal = this.subtotal.subtract(this.discountAmount).setScale(SCALE, RoundingMode.HALF_UP);

        this.status = status;
        this.paymentStatus = paymentStatus;
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now();
        this.transactionReference = transactionReference;
    }

    /**
     * Convenience constructor with automatic timestamp and ID generation.
     */
    public Order(String customerId,
                 List<OrderItem> items,
                 BigDecimal discountAmount,
                 OrderStatus status,
                 PaymentStatus paymentStatus,
                 String transactionReference) {
        this(null, customerId, items, discountAmount, status, paymentStatus, null, transactionReference);
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    /**
     * Returns an unmodifiable list of the order item snapshots.
     *
     * @return unmodifiable list of {@link OrderItem}
     */
    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getFinalTotal() {
        return finalTotal;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        if (status == null) {
            throw new InvalidInputException("Order status must not be null.");
        }
        this.status = status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            throw new InvalidInputException("Payment status must not be null.");
        }
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public int lineCount() {
        return items.size();
    }

    @Override
    public String toString() {
        return String.format(
                "Order{id='%s', customer='%s', lines=%d, subtotal=$%s, discount=$%s, total=$%s, status=%s, payment=%s, ref='%s'}",
                orderId, customerId, items.size(), subtotal.toPlainString(),
                discountAmount.toPlainString(), finalTotal.toPlainString(), status, paymentStatus,
                transactionReference != null ? transactionReference : "none");
    }
}
