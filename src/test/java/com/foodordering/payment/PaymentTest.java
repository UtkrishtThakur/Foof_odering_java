package com.foodordering.payment;

import com.foodordering.exception.InvalidInputException;
import com.foodordering.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Payment Abstraction & Simulation Tests")
class PaymentTest {

    @Nested
    @DisplayName("CashPayment Tests")
    class CashPaymentTests {

        @Test
        @DisplayName("Should successfully process cash payment")
        void shouldProcessCashPaymentSuccessfully() {
            Payment payment = new CashPayment(new BigDecimal("49.99"));

            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertEquals(new BigDecimal("49.99"), payment.getAmount());

            PaymentStatus result = payment.processPayment();

            assertEquals(PaymentStatus.PAID, result);
            assertEquals(PaymentStatus.PAID, payment.getStatus());
            assertTrue(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should accept zero amount payment (e.g. 100% discount)")
        void shouldAcceptZeroAmountPayment() {
            Payment payment = new CashPayment(BigDecimal.ZERO);

            assertEquals(new BigDecimal("0.00"), payment.getAmount());
            PaymentStatus result = payment.processPayment();

            assertEquals(PaymentStatus.PAID, result);
            assertTrue(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should reject null payment amount")
        void shouldRejectNullAmount() {
            assertThrows(InvalidInputException.class,
                    () -> new CashPayment((BigDecimal) null));
        }

        @Test
        @DisplayName("Should reject negative payment amount")
        void shouldRejectNegativeAmount() {
            assertThrows(InvalidInputException.class,
                    () -> new CashPayment(new BigDecimal("-0.01")));
        }
    }

    @Nested
    @DisplayName("OnlinePayment Tests")
    class OnlinePaymentTests {

        @Test
        @DisplayName("Should simulate successful online payment")
        void shouldSimulateSuccessfulOnlinePayment() {
            OnlinePayment payment = new OnlinePayment(new BigDecimal("99.95"), true);

            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertTrue(payment.isSimulateSuccess());
            assertNotNull(payment.getTransactionReference());

            PaymentStatus result = payment.processPayment();

            assertEquals(PaymentStatus.PAID, result);
            assertEquals(PaymentStatus.PAID, payment.getStatus());
            assertTrue(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should simulate failed online payment without throwing exception")
        void shouldSimulateFailedOnlinePayment() {
            OnlinePayment payment = new OnlinePayment(new BigDecimal("99.95"), false);

            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            assertFalse(payment.isSimulateSuccess());

            PaymentStatus result = payment.processPayment();

            assertEquals(PaymentStatus.FAILED, result);
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
            assertFalse(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should default to success when outcome is not explicitly provided")
        void shouldDefaultToSuccess() {
            OnlinePayment payment = new OnlinePayment(new BigDecimal("25.00"));

            assertTrue(payment.isSimulateSuccess());
            PaymentStatus result = payment.processPayment();

            assertEquals(PaymentStatus.PAID, result);
            assertTrue(payment.isSuccessful());
        }

        @Test
        @DisplayName("Should reject null payment amount")
        void shouldRejectNullAmount() {
            assertThrows(InvalidInputException.class,
                    () -> new OnlinePayment((BigDecimal) null, true));
            assertThrows(InvalidInputException.class,
                    () -> new OnlinePayment((String) null, true));
        }

        @Test
        @DisplayName("Should reject negative payment amount")
        void shouldRejectNegativeAmount() {
            assertThrows(InvalidInputException.class,
                    () -> new OnlinePayment(new BigDecimal("-15.00"), true));
        }
    }

    @Nested
    @DisplayName("Payment Polymorphism Tests")
    class PolymorphismTests {

        @Test
        @DisplayName("Should process multiple payment implementations polymorphically")
        void shouldProcessPaymentsPolymorphically() {
            List<Payment> paymentQueue = new ArrayList<>();
            paymentQueue.add(new CashPayment(new BigDecimal("30.00")));
            paymentQueue.add(new OnlinePayment(new BigDecimal("45.50"), true));
            paymentQueue.add(new OnlinePayment(new BigDecimal("12.00"), false));

            List<PaymentStatus> results = new ArrayList<>();
            for (Payment payment : paymentQueue) {
                // Polymorphic invocation of processPayment()
                results.add(payment.processPayment());
            }

            assertEquals(PaymentStatus.PAID, results.get(0));
            assertEquals(PaymentStatus.PAID, results.get(1));
            assertEquals(PaymentStatus.FAILED, results.get(2));

            assertTrue(paymentQueue.get(0).isSuccessful());
            assertTrue(paymentQueue.get(1).isSuccessful());
            assertFalse(paymentQueue.get(2).isSuccessful());
        }
    }
}
