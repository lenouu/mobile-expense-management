package com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetAnalyticsResponses;

import java.util.List;
import java.util.UUID;

/**
 * Budget analytics: overall balance (US41) and per-category balance (US42).
 *
 * <p>Read-only. Every number reuses the same repository queries as
 * {@link BudgetService} so the figures agree with {@code GET /api/budgets/{id}}.
 */
public interface BudgetAnalyticsService
{
    /**
     * Overall budget vs actual for the budget overlapping the given period.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period
     *         does not exist, does not belong to the caller, or no budget overlaps it
     */
    BudgetAnalyticsResponses.OverallBalance overallBalance(UUID userId, UUID periodId);

    /**
     * Per-category balance for every allocation of the budget overlapping the given period.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period
     *         does not exist, does not belong to the caller, or no budget overlaps it
     */
    List<BudgetAnalyticsResponses.CategoryBalance> categoryBalance(UUID userId, UUID periodId);
}

