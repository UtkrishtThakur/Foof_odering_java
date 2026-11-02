package com.foodordering.discount;

import com.foodordering.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Applies a fixed flat monetary discount to an amount.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Discount amount must be non-null and non-negative.</li>
 *   <li>The final amount after discount will never be negative.</li>
 *   <li>If the discount amount exceeds or equals the original amount, the final amount is capped at zero.</li>
 *   <li>Monetary calculations use {@link BigDecimal} with scale 2 and {@link RoundingMode#HALF_UP}.</li>
 * </ul>
 * </p>
 */
public class FlatDiscount implements Discountable {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 2;

    private final BigDecimal discountAmount;

    /**
     * Constructs a FlatDiscount with the specified flat deduction amount.
     *
     * @param discountAmount the flat amount to deduct; must be non-null and non-negative
     * @throws InvalidInputException if {@code discountAmount} is null or negative
     */
    public FlatDiscount(BigDecimal discountAmount) {
        if (discountAmount == null) {
            throw new InvalidInputException("Flat discount amount must not be null.");
        }
        if (discountAmount.compareTo(ZERO) < 0) {
            throw new InvalidInputException(
                    "Flat discount amount must not be negative: " + discountAmount.toPlainString());
        }
        this.discountAmount = discountAmount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Convenience constructor accepting a string representation of the flat discount.
     *
     * @param discountAmount the flat discount amount string; must not be null or blank
     * @throws InvalidInputException if {@code discountAmount} is null, blank, or not a valid number
     */
    public FlatDiscount(String discountAmount) {
        this(parseBigDecimal(discountAmount, "Flat discount amount"));
    }

    /**
     * Returns the configured flat discount amount.
     *
     * @return the discount amount
     */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    @Override
    public BigDecimal applyDiscount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidInputException("Monetary amount must not be null.");
        }
        if (amount.compareTo(ZERO) < 0) {
            throw new InvalidInputException("Monetary amount must not be negative: " + amount.toPlainString());
        }

        BigDecimal scaledAmount = amount.setScale(SCALE, RoundingMode.HALF_UP);

        // Cap at zero if discount exceeds or equals the original amount
        if (discountAmount.compareTo(scaledAmount) >= 0) {
            return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        return scaledAmount.subtract(discountAmount);
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
        return "FlatDiscount{$" + discountAmount.toPlainString() + "}";
    }
}
