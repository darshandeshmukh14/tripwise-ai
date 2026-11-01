package com.tripwise.service;

import com.tripwise.entity.BudgetStatus;

import java.math.BigDecimal;

/**
 * Budget status is recomputed on the Java side instead of trusting the AI service.
 * Thresholds match the AI service's budget guard: NEAR above 90% of budget, OVER above 100%.
 */
public final class BudgetStatusCalculator {

    private static final BigDecimal NEAR_THRESHOLD = new BigDecimal("0.90");

    private BudgetStatusCalculator() {
    }

    public static BudgetStatus of(BigDecimal budget, BigDecimal estimatedTotal) {
        if (budget == null || estimatedTotal == null || budget.signum() <= 0) {
            return BudgetStatus.UNKNOWN;
        }
        if (estimatedTotal.compareTo(budget) > 0) {
            return BudgetStatus.OVER;
        }
        if (estimatedTotal.compareTo(budget.multiply(NEAR_THRESHOLD)) > 0) {
            return BudgetStatus.NEAR;
        }
        return BudgetStatus.WITHIN;
    }
}
