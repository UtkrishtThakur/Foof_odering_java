package com.foodordering.payment;

import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Abstract base class representing a payment attempt in the food ordering system.
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Encapsulates monetary amount validation (must be non-null and non-negative).</li>
 *   <li>Tracks payment lifecycle status via {@link PaymentStatus}.</li>
 *   <li>Defines the polymorphic contract {@link #processPayment()} for concrete payment methods.</li>
 *   <li>Simulates transactions without connecting to external payment networks or real banking systems.</li>
 * </ul>
 * </p>
 */
public abstract class Payment {

    private static final int SCALE = 2;

    private final BigDecimal amount;
    private PaymentStatus status;

    /**
     * Constructs a Payment attempt for the specified amount.
     *
     * @param amount the payment amount; must be non-null and non-negative (&gt;= 0)
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    protected Payment(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidInputException("Payment amount must not be null.");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException(
                    "Payment amount must not be negative: " + amount.toPlainString());
        }
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        this.status = PaymentStatus.PENDING;
    }

    /**
     * Returns the payment amount.
     *
     * @return the amount as a {@link BigDecimal}
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the current lifecycle status of this payment attempt.
     *
     * @return the {@link PaymentStatus}
     */
    public PaymentStatus getStatus() {
        return status;
    }

    /**
     * Updates the payment status. Subclasses invoke this during processing.
     *
     * @param status the new payment status; must not be null
     * @throws InvalidInputException if {@code status} is null
     */
    protected void setStatus(PaymentStatus status) {
        if (status == null) {
            throw new InvalidInputException("Payment status must not be null.");
        }
        this.status = status;
    }

    /**
     * Checks if this payment attempt was successfully completed.
     *
     * @return {@code true} if status is {@link PaymentStatus#PAID}, {@code false} otherwise
     */
    public boolean isSuccessful() {
        return this.status == PaymentStatus.PAID;
    }

    /**
     * Processes this payment attempt. Concrete payment implementations define their specific
     * simulation logic (e.g., cash acceptance or online payment simulation).
     *
     * @return the resulting {@link PaymentStatus} (e.g. {@link PaymentStatus#PAID} or {@link PaymentStatus#FAILED})
     */
    public abstract PaymentStatus processPayment();

    /**
     * Convenience alias for {@link #processPayment()} to facilitate invocation.
     *
     * @return the resulting {@link PaymentStatus}
     */
    public PaymentStatus process() {
        return processPayment();
    }

    /**
     * Creates a new payment instance of the same concrete implementation with the specified amount.
     *
     * @param newAmount the updated payment amount; must be non-null and non-negative
     * @return a new {@link Payment} instance with identical configuration and the specified amount
     */
    public abstract Payment withAmount(BigDecimal newAmount);
}
