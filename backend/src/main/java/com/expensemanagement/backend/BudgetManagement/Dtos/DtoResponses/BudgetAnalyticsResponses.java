package com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.Shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Response DTOs for the budget-analytics endpoints (US41, US42).
 *
 * <p>Money is always an {@code {amount, currency}} object, matching every other endpoint.
 */
public class BudgetAnalyticsResponses
{
    /**
     * US41 — overall budget vs actual for the resolved period.
     *
     * <p>{@code balance} may be negative: overspending is a normal result, never an error.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class OverallBalance
    {
        private final UUID periodId;
        private final Money budgetAmount;
        private final Money actualSpent;
        private final Money balance;
    }

    /**
     * US42 — one row per category allocation of the resolved budget.
     *
     * <p>{@code balance} may be negative. {@code overspent} is {@code true} exactly when
     * {@code balance < 0}.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategoryBalance
    {
        private final UUID categoryId;
        private final Money allocated;
        private final Money spent;
        private final Money balance;
        private final boolean overspent;
    }
}

