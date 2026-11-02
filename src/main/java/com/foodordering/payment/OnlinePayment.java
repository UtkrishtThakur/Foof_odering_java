package com.foodordering.payment;

import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concrete payment implementation representing a simulated online payment method
 * (such as credit/debit card, UPI, or net banking).
 *
 * <p>Allows deterministic simulation of both successful and failed payment outcomes
 * via a constructor parameter, without involving external payment gateways, network
 * calls, or real banking credentials.</p>
 */
public class OnlinePayment extends Payment {

    private static final AtomicInteger TRANSACTION_COUNTER = new AtomicInteger(1000);

    private final boolean simulateSuccess;
    private final String transactionReference;

    /**
     * Constructs an OnlinePayment attempt with configurable outcome simulation.
     *
     * @param amount          the payment amount; must be non-null and non-negative
     * @param simulateSuccess {@code true} to simulate a successful payment,
     *                        {@code false} to simulate a payment failure
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    public OnlinePayment(BigDecimal amount, boolean simulateSuccess) {
        super(amount);
        this.simulateSuccess = simulateSuccess;
        this.transactionReference = "TXN-SIM-" + TRANSACTION_COUNTER.incrementAndGet();
    }

    /**
     * Constructs an OnlinePayment attempt that defaults to successful simulation.
     *
     * @param amount the payment amount; must be non-null and non-negative
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    public OnlinePayment(BigDecimal amount) {
        this(amount, true);
    }

    /**
     * Convenience constructor accepting a string representation of the amount.
     *
     * @param amount          the payment amount string
     * @param simulateSuccess {@code true} to simulate success, {@code false} for failure
     * @throws InvalidInputException if {@code amount} is null, blank, or invalid
     */
    public OnlinePayment(String amount, boolean simulateSuccess) {
        this(parseBigDecimal(amount, "Payment amount"), simulateSuccess);
    }

    /**
     * Returns whether this online payment is configured to simulate success.
     *
     * @return {@code true} if configured to succeed, {@code false} otherwise
     */
    public boolean isSimulateSuccess() {
        return simulateSuccess;
    }

    /**
     * Returns the simulated transaction reference identifier.
     *
     * @return the transaction reference
     */
    public String getTransactionReference() {
        return transactionReference;
    }

    @Override
    public PaymentStatus processPayment() {
        if (simulateSuccess) {
            setStatus(PaymentStatus.PAID);
            return PaymentStatus.PAID;
        } else {
            setStatus(PaymentStatus.FAILED);
            return PaymentStatus.FAILED;
        }
    }

    @Override
    public OnlinePayment withAmount(BigDecimal newAmount) {
        return new OnlinePayment(newAmount, this.simulateSuccess);
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
        return String.format("OnlinePayment{amount=$%s, simulatedSuccess=%b, ref='%s', status=%s}",
                getAmount().toPlainString(), simulateSuccess, transactionReference, getStatus());
    }
}
