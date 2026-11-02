package com.foodordering.discount;

import com.foodordering.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Monetary & Discount Calculation Audit Tests")
class DiscountCalculationTest {

    @Test
    @DisplayName("PercentageDiscount: Correct calculations at 0%, 10%, 50%, and 100%")
    void shouldCalculatePercentageDiscountCorrectly() {
        BigDecimal base = new BigDecimal("100.00");

        Discountable discount0 = new PercentageDiscount(BigDecimal.ZERO);
        assertEquals(new BigDecimal("100.00"), discount0.applyDiscount(base));

        Discountable discount10 = new PercentageDiscount(new BigDecimal("10"));
        assertEquals(new BigDecimal("90.00"), discount10.applyDiscount(base));

        Discountable discount50 = new PercentageDiscount(new BigDecimal("50"));
        assertEquals(new BigDecimal("50.00"), discount50.applyDiscount(base));

        Discountable discount100 = new PercentageDiscount(new BigDecimal("100"));
        assertEquals(new BigDecimal("0.00"), discount100.applyDiscount(base));
    }

    @Test
    @DisplayName("PercentageDiscount: Half-up rounding boundary precision")
    void shouldHandleRoundingHalfUpCorrectly() {
        // $15.55 with 15% discount
        // 15.55 * 0.15 = 2.3325 -> rounded to 2.33 discount
        // Final total: 15.55 - 2.33 = 13.22
        PercentageDiscount discount = new PercentageDiscount(new BigDecimal("15"));
        BigDecimal amount = new BigDecimal("15.55");

        BigDecimal discountAmount = discount.calculateDiscountAmount(amount);
        assertEquals(new BigDecimal("2.33"), discountAmount);

        BigDecimal finalTotal = discount.applyDiscount(amount);
        assertEquals(new BigDecimal("13.22"), finalTotal);
    }

    @Test
    @DisplayName("PercentageDiscount: Reject negative percentages or percentages > 100%")
    void shouldRejectOutOfBoundsPercentages() {
        assertThrows(InvalidInputException.class, () -> new PercentageDiscount(new BigDecimal("-0.01")));
        assertThrows(InvalidInputException.class, () -> new PercentageDiscount(new BigDecimal("100.01")));
        assertThrows(InvalidInputException.class, () -> new PercentageDiscount((BigDecimal) null));
    }

    @Test
    @DisplayName("FlatDiscount: Deducts exact flat amount and preserves scale")
    void shouldDeductFlatAmountCorrectly() {
        FlatDiscount discount = new FlatDiscount(new BigDecimal("7.50"));
        BigDecimal original = new BigDecimal("25.00");

        BigDecimal finalAmount = discount.applyDiscount(original);
        assertEquals(new BigDecimal("17.50"), finalAmount);
    }

    @Test
    @DisplayName("FlatDiscount: Capped at zero when discount equals or exceeds original total")
    void shouldCapFlatDiscountAtZero() {
        FlatDiscount discount = new FlatDiscount(new BigDecimal("50.00"));

        // Exact match
        assertEquals(new BigDecimal("0.00"), discount.applyDiscount(new BigDecimal("50.00")));

        // Exceeds original amount
        assertEquals(new BigDecimal("0.00"), discount.applyDiscount(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("FlatDiscount: Reject negative or null discount amount")
    void shouldRejectNegativeOrNullFlatDiscount() {
        assertThrows(InvalidInputException.class, () -> new FlatDiscount(new BigDecimal("-5.00")));
        assertThrows(InvalidInputException.class, () -> new FlatDiscount((BigDecimal) null));
    }

    @Test
    @DisplayName("Discounts: Rejects null or negative amount arguments in applyDiscount")
    void shouldRejectInvalidInputAmounts() {
        Discountable discount = new PercentageDiscount(10);
        assertThrows(InvalidInputException.class, () -> discount.applyDiscount(null));
        assertThrows(InvalidInputException.class, () -> discount.applyDiscount(new BigDecimal("-10.00")));

        Discountable flat = new FlatDiscount(new BigDecimal("5.00"));
        assertThrows(InvalidInputException.class, () -> flat.applyDiscount(null));
        assertThrows(InvalidInputException.class, () -> flat.applyDiscount(new BigDecimal("-10.00")));
    }

    @Test
    @DisplayName("Monetary Invariant: Large monetary calculations avoid precision loss")
    void shouldHandleLargeMonetaryAmountsAccurately() {
        BigDecimal largeAmount = new BigDecimal("100000000.00"); // 100 million
        PercentageDiscount discount = new PercentageDiscount(new BigDecimal("10"));

        BigDecimal result = discount.applyDiscount(largeAmount);
        assertEquals(new BigDecimal("90000000.00"), result);
    }
}
