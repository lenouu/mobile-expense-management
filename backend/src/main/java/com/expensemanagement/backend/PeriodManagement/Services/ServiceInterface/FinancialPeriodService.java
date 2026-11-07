package com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface;

import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Financial periods of one user (S4-TECH-1) - the shared foundation for budgets (US28-US33),
 * savings (US44) and debt carry-forward (US48, US49).
 *
 * <p>Periods are created on demand, so every method can be called for any user at any time:
 * a user who never chose a length gets the default of one calendar month, counted from the day
 * their account was created.
 */
public interface FinancialPeriodService
{
    /** The period containing today. */
    FinancialPeriod current(Long userId);

    /**
     * The period containing {@code date}, e.g. to file an expense or income under its period.
     * Empty when the date is before the user's first period (an expense dated before sign-up).
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the date is
     *         more than a year in the future
     */
    Optional<FinancialPeriod> containing(Long userId, LocalDate date);

    /**
     * One of the user's own periods by id, e.g. to compute totals for a past period (US39).
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if it does not
     *         exist or belongs to someone else
     */
    FinancialPeriod findMine(Long userId, Long periodId);

    /** The period just before {@code period}; empty for the user's first period. */
    Optional<FinancialPeriod> previous(FinancialPeriod period);

    /** The period just after {@code period}, created if needed (debt carry-forward, US48). */
    FinancialPeriod next(FinancialPeriod period);

    /**
     * Last day of the opening window covering the first {@code percent}% of the period, e.g.
     * {@code openingWindowEnd(period, 6)} for the budget edit window (US32, US33).
     */
    LocalDate openingWindowEnd(FinancialPeriod period, double percent);

    /** Whether {@code date} falls in the first {@code percent}% of the period. */
    boolean isInOpeningWindow(FinancialPeriod period, double percent, LocalDate date);

    FinancialPeriodSettings settings(Long userId);

    /**
     * Changes the user's period length (the rule behind US27). The period in progress keeps its
     * dates; the new length applies from the next period on.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the length is
     *         shorter than one week or longer than one year
     */
    FinancialPeriodSettings changeLength(Long userId, int count, PeriodUnit unit);

    /** The user's periods up to the current one, newest first. */
    Page<PeriodResponses.Period> history(Long userId, Pageable pageable);
}
