package com.expensemanagement.backend.SavingsManagement.Services.ServiceInterface;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses.SavingsResponses;

import java.util.List;
import java.util.UUID;

/**
 * Shared savings and debt calculation with automatic carry-forward (US44, US46, US48, US49).
 *
 * <p>Carry-forward is derived automatically across contiguous periods: debt from period \(n\)
 * is carried into period \(n+1\) as a deduction from income. A shortfall is debt; a surplus
 * is savings; neither is recorded as a user action or tied to a budget.
 */
public interface SavingsService
{
    /**
     * Walks the user's financial periods in ascending order from period 1 up to {@code period},
     * calculating cumulative debt carry-forward, effective income, and savings/debt for each period.
     *
     * @param userId the caller's user id
     * @param period the target financial period (must belong to the user)
     * @return ordered list of period summaries from the first period to {@code period}
     */
    List<SavingsResponses.PeriodSummary> summariesUpTo(UUID userId, FinancialPeriod period);

    /**
     * Resolves the financial period by id and returns its savings/debt summary.
     *
     * @param userId the caller's user id
     * @param periodId the financial period id
     * @return the savings and debt summary for the requested period
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period
     *         does not exist or does not belong to the caller
     */
    SavingsResponses.PeriodSummary summaryFor(UUID userId, UUID periodId);

    /**
     * Convenience method returning the summary for a resolved period (the last element of
     * {@link #summariesUpTo(UUID, FinancialPeriod)}).
     *
     * @param userId the caller's user id
     * @param period the target financial period
     * @return the savings and debt summary for {@code period}
     */
    SavingsResponses.PeriodSummary summaryFor(UUID userId, FinancialPeriod period);
}

