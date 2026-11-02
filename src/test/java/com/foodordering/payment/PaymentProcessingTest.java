package com.foodordering.payment;

import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Payment Abstraction & Simulation Tests")
class PaymentProcessingTest {

    @Test
    @DisplayName("CashPayment: Successfully processes valid amount and zero amount")
    void shouldProcessCashPayment() {
        Payment cash = new CashPayment(new BigDecimal("45.50"));
        assertEquals(PaymentStatus.PENDING, cash.getStatus());
        assertEquals(new BigDecimal("45.50"), cash.getAmount());

        PaymentStatus status = cash.processPayment();
        assertEquals(PaymentStatus.PAID, status);
        assertTrue(cash.isSuccessful());

        // Zero-amount cash payment (e.g. for 100% discounted order)
        Payment zeroCash = new CashPayment(BigDecimal.ZERO);
        assertEquals(PaymentStatus.PAID, zeroCash.processPayment());
        assertTrue(zeroCash.isSuccessful());
    }

    @Test
    @DisplayName("CashPayment: Rejects null or negative payment amount")
    void shouldRejectInvalidCashAmount() {
        assertThrows(InvalidInputException.class, () -> new CashPayment((BigDecimal) null));
        assertThrows(InvalidInputException.class, () -> new CashPayment(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("OnlinePayment: Approves payment and generates transaction reference on simulated success")
    void shouldProcessOnlinePaymentSuccess() {
        OnlinePayment online = new OnlinePayment(new BigDecimal("29.99"), true);
        assertEquals(PaymentStatus.PENDING, online.getStatus());
        assertNotNull(online.getTransactionReference());
        assertTrue(online.getTransactionReference().startsWith("TXN-SIM-"));

        PaymentStatus result = online.processPayment();
        assertEquals(PaymentStatus.PAID, result);
        assertTrue(online.isSuccessful());
    }

    @Test
    @DisplayName("OnlinePayment: Declines payment on simulated failure")
    void shouldProcessOnlinePaymentFailure() {
        OnlinePayment online = new OnlinePayment(new BigDecimal("29.99"), false);
        assertEquals(PaymentStatus.PENDING, online.getStatus());

        PaymentStatus result = online.processPayment();
        assertEquals(PaymentStatus.FAILED, result);
        assertFalse(online.isSuccessful());
    }

    @Test
    @DisplayName("Payment: Polymorphism and withAmount factory method")
    void shouldSupportPolymorphismAndWithAmount() {
        Payment payment = new OnlinePayment(new BigDecimal("10.00"), true);
        Payment reconfigured = payment.withAmount(new BigDecimal("15.00"));

        assertEquals(new BigDecimal("15.00"), reconfigured.getAmount());
        assertTrue(reconfigured instanceof OnlinePayment);
        assertEquals(PaymentStatus.PAID, reconfigured.processPayment());
    }
}
