package com.foodordering.payment;

import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.PaymentStatus;

import java.math.BigDecimal;

/**
 * Concrete payment implementation representing a cash transaction
 * (such as cash-on-delivery or counter payment).
 *
 * <p>Simulates cash acceptance and always succeeds for valid non-negative amounts.</p>
 */
public class CashPayment extends Payment {

    /**
     * Constructs a CashPayment attempt for the specified amount.
     *
     * @param amount the payment amount; must be non-null and non-negative
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    public CashPayment(BigDecimal amount) {
        super(amount);
    }

    /**
     * Convenience constructor accepting a string representation of the amount.
     *
     * @param amount the payment amount string
     * @throws InvalidInputException if {@code amount} is null, blank, or invalid
     */
    public CashPayment(String amount) {
        this(parseBigDecimal(amount, "Payment amount"));
    }

    @Override
    public PaymentStatus processPayment() {
        setStatus(PaymentStatus.PAID);
        return PaymentStatus.PAID;
    }

    @Override
    public CashPayment withAmount(BigDecimal newAmount) {
        return new CashPayment(newAmount);
    }

    private static BigDecimal parseBigDecimal(String val, String fieldName) {
        if (val == null || val.isBlank()) {
            throw new InvalidInputException(fieldName + " must not be null or blank.");
        }
        try {
            return new BigDecimal(val.strip());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(fieldName + " is not a valid number: '" + val + "'.", e);
        }
    }

    @Override
    public String toString() {
        return String.format("CashPayment{amount=$%s, status=%s}",
                getAmount().toPlainString(), getStatus());
    }
}
