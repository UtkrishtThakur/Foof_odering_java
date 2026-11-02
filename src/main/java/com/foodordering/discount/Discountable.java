package com.foodordering.discount;

import com.foodordering.exception.InvalidInputException;

import java.math.BigDecimal;

/**
 * Strategy interface representing a discount that can be applied to a monetary amount.
 *
 * <p>Implementations must adhere to the following contract:
 * <ul>
 *   <li>Reject {@code null} or negative input amounts with an {@link InvalidInputException}.</li>
 *   <li>Never return a negative monetary amount.</li>
 *   <li>Ensure that the discounted result does not exceed the original input amount.</li>
 *   <li>Perform all monetary calculations using {@link BigDecimal} with high precision.</li>
 * </ul>
 * </p>
 */
public interface Discountable {

    /**
     * Applies this discount strategy to the supplied monetary amount and returns
     * the final amount payable after discount deduction.
     *
     * @param amount the original monetary amount before discount; must be non-null and non-negative
     * @return the final monetary amount after applying the discount; never null, never negative,
     *         and never greater than the original amount
     * @throws InvalidInputException if {@code amount} is null or negative
     */
    BigDecimal applyDiscount(BigDecimal amount);
}
