package com.foodordering.discount;

import com.foodordering.exception.InvalidInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Discount System Tests")
class DiscountTest {

    @Nested
    @DisplayName("PercentageDiscount Tests")
    class PercentageDiscountTests {

        @Test
        @DisplayName("Should apply 10% discount correctly on 1000.00")
        void shouldApplyTenPercentDiscount() {
            PercentageDiscount discount = new PercentageDiscount(new BigDecimal("10"));
            BigDecimal original = new BigDecimal("1000.00");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("900.00"), result);
            assertEquals(new BigDecimal("100.00"), discount.calculateDiscountAmount(original));
        }

        @Test
        @DisplayName("Should apply 0% discount resulting in original amount")
        void shouldApplyZeroPercentDiscount() {
            PercentageDiscount discount = new PercentageDiscount(BigDecimal.ZERO);
            BigDecimal original = new BigDecimal("150.50");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("150.50"), result);
        }

        @Test
        @DisplayName("Should apply 100% discount resulting in zero")
        void shouldApplyHundredPercentDiscount() {
            PercentageDiscount discount = new PercentageDiscount(new BigDecimal("100"));
            BigDecimal original = new BigDecimal("150.50");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("0.00"), result);
        }

        @Test
        @DisplayName("Should round with HALF_UP to 2 decimal places")
        void shouldRoundWithHalfUp() {
            // 8.99 * 10 / 100 = 0.899 -> 0.90 discount -> 8.09 final
            PercentageDiscount discount = new PercentageDiscount(new BigDecimal("10"));
            BigDecimal original = new BigDecimal("8.99");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("8.09"), result);
            assertEquals(new BigDecimal("0.90"), discount.calculateDiscountAmount(original));
        }

        @Test
        @DisplayName("Should reject negative percentage")
        void shouldRejectNegativePercentage() {
            assertThrows(InvalidInputException.class,
                    () -> new PercentageDiscount(new BigDecimal("-1")));
        }

        @Test
        @DisplayName("Should reject percentage greater than 100")
        void shouldRejectPercentageGreaterThanHundred() {
            assertThrows(InvalidInputException.class,
                    () -> new PercentageDiscount(new BigDecimal("100.01")));
        }

        @Test
        @DisplayName("Should reject null percentage")
        void shouldRejectNullPercentage() {
            assertThrows(InvalidInputException.class,
                    () -> new PercentageDiscount((BigDecimal) null));
        }

        @Test
        @DisplayName("Should reject null monetary amount")
        void shouldRejectNullAmount() {
            PercentageDiscount discount = new PercentageDiscount(new BigDecimal("10"));
            assertThrows(InvalidInputException.class,
                    () -> discount.applyDiscount(null));
        }

        @Test
        @DisplayName("Should reject negative monetary amount")
        void shouldRejectNegativeAmount() {
            PercentageDiscount discount = new PercentageDiscount(new BigDecimal("10"));
            assertThrows(InvalidInputException.class,
                    () -> discount.applyDiscount(new BigDecimal("-10.00")));
        }
    }

    @Nested
    @DisplayName("FlatDiscount Tests")
    class FlatDiscountTests {

        @Test
        @DisplayName("Should deduct flat amount correctly")
        void shouldDeductFlatAmount() {
            FlatDiscount discount = new FlatDiscount(new BigDecimal("5.00"));
            BigDecimal original = new BigDecimal("20.00");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("15.00"), result);
        }

        @Test
        @DisplayName("Should cap final amount at zero when discount exceeds original amount")
        void shouldCapAtZeroWhenDiscountExceedsAmount() {
            FlatDiscount discount = new FlatDiscount(new BigDecimal("150.00"));
            BigDecimal original = new BigDecimal("100.00");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("0.00"), result);
        }

        @Test
        @DisplayName("Should return zero when discount equals original amount exactly")
        void shouldReturnZeroWhenDiscountEqualsAmount() {
            FlatDiscount discount = new FlatDiscount(new BigDecimal("50.00"));
            BigDecimal original = new BigDecimal("50.00");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("0.00"), result);
        }

        @Test
        @DisplayName("Should support zero flat discount")
        void shouldSupportZeroFlatDiscount() {
            FlatDiscount discount = new FlatDiscount(BigDecimal.ZERO);
            BigDecimal original = new BigDecimal("75.00");

            BigDecimal result = discount.applyDiscount(original);

            assertEquals(new BigDecimal("75.00"), result);
        }

        @Test
        @DisplayName("Should reject negative flat discount amount")
        void shouldRejectNegativeFlatDiscount() {
            assertThrows(InvalidInputException.class,
                    () -> new FlatDiscount(new BigDecimal("-0.01")));
        }

        @Test
        @DisplayName("Should reject null flat discount amount")
        void shouldRejectNullFlatDiscount() {
            assertThrows(InvalidInputException.class,
                    () -> new FlatDiscount((BigDecimal) null));
        }

        @Test
        @DisplayName("Should reject null monetary amount")
        void shouldRejectNullAmount() {
            FlatDiscount discount = new FlatDiscount(new BigDecimal("5.00"));
            assertThrows(InvalidInputException.class,
                    () -> discount.applyDiscount(null));
        }

        @Test
        @DisplayName("Should reject negative monetary amount")
        void shouldRejectNegativeAmount() {
            FlatDiscount discount = new FlatDiscount(new BigDecimal("5.00"));
            assertThrows(InvalidInputException.class,
                    () -> discount.applyDiscount(new BigDecimal("-1.00")));
        }
    }

    @Nested
    @DisplayName("Discount Polymorphism Tests")
    class PolymorphismTests {

        @Test
        @DisplayName("Should apply discounts polymorphically via Discountable interface")
        void shouldApplyDiscountsPolymorphically() {
            List<Discountable> discountStrategies = new ArrayList<>();
            discountStrategies.add(new PercentageDiscount(new BigDecimal("20"))); // 20% off
            discountStrategies.add(new FlatDiscount(new BigDecimal("15.00")));    // $15 off

            BigDecimal originalAmount = new BigDecimal("100.00");

            // Apply 20%: 100 - 20 = 80.00
            BigDecimal res1 = discountStrategies.get(0).applyDiscount(originalAmount);
            assertEquals(new BigDecimal("80.00"), res1);

            // Apply $15: 100 - 15 = 85.00
            BigDecimal res2 = discountStrategies.get(1).applyDiscount(originalAmount);
            assertEquals(new BigDecimal("85.00"), res2);
        }
    }
}
