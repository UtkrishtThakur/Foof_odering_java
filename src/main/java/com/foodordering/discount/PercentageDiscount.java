package com.foodordering.discount;

import com.foodordering.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a percentage-based discount to a monetary amount.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Percentage must be between 0 and 100 inclusive.</li>
 *   <li>All calculations use {@link BigDecimal} with scale 2 and {@link RoundingMode#HALF_UP}.</li>
 *   <li>Avoids primitive floating-point types ({@code double}, {@code float}) to maintain financial precision.</li>
 *   <li>Validates that input amounts are non-null and non-negative.</li>
 *   <li>Guarantees the resulting amount is non-negative and never exceeds the original amount.</li>
 * </ul>
 * </p>
 */
public class PercentageDiscount implements Discountable {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int SCALE = 2;

    private final BigDecimal percentage;

    /**
     * Constructs a PercentageDiscount with the specified percentage.
     *
     * @param percentage the discount percentage; must be non-null and between 0 and 100 inclusive
     * @throws InvalidInputException if {@code percentage} is null, less than 0, or greater than 100
     */
    public PercentageDiscount(BigDecimal percentage) {
        if (percentage == null) {
            throw new InvalidInputException("Discount percentage must not be null.");
        }
        if (percentage.compareTo(ZERO) < 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new InvalidInputException(
                    "Discount percentage must be between 0 and 100 inclusive, but received: "
                            + percentage.toPlainString());
        }
        this.percentage = percentage;
    }

    /**
     * Convenience constructor accepting an integer percentage.
     *
     * @param percentage the discount percentage between 0 and 100 inclusive
     * @throws InvalidInputException if {@code percentage} is less than 0 or greater than 100
     */
    public PercentageDiscount(int percentage) {
        this(BigDecimal.valueOf(percentage));
    }

    /**
     * Convenience constructor accepting a string representation of the percentage.
     *
     * @param percentage the discount percentage string; must not be null or blank
     * @throws InvalidInputException if {@code percentage} is null, blank, or not a valid number
     */
    public PercentageDiscount(String percentage) {
        this(parseBigDecimal(percentage, "Discount percentage"));
    }

    /**
     * Returns the configured discount percentage.
     *
     * @return the discount percentage
     */
    public BigDecimal getPercentage() {
        return percentage;
    }

    /**
     * Calculates the monetary discount amount that will be deducted from the given amount.
     *
     * @param amount the original amount; must be non-null and non-negative
     * @return the discount deduction amount rounded to 2 decimal places using HALF_UP
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    public BigDecimal calculateDiscountAmount(BigDecimal amount) {
        validateAmount(amount);
        return amount.multiply(percentage).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal applyDiscount(BigDecimal amount) {
        validateAmount(amount);

        BigDecimal discountDeduction = calculateDiscountAmount(amount);
        BigDecimal scaledAmount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal finalAmount = scaledAmount.subtract(discountDeduction);

        // Defensive invariant checks
        if (finalAmount.compareTo(ZERO) < 0) {
            return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (finalAmount.compareTo(scaledAmount) > 0) {
            return scaledAmount;
        }

        return finalAmount;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidInputException("Monetary amount must not be null.");
        }
        if (amount.compareTo(ZERO) < 0) {
            throw new InvalidInputException("Monetary amount must not be negative: " + amount.toPlainString());
        }
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
        return "PercentageDiscount{" + percentage.toPlainString() + "%}";
    }
}
