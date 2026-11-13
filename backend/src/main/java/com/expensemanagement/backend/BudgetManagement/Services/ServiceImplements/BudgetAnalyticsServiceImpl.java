package com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetAnalyticsResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetAnalyticsService;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budget analytics (US41, US42).
 *
 * <p>Numbers come from the same repository methods as {@code BudgetServiceImpl}:
 * {@code totalsByBudget} and {@code sumByCategoryForBudget}, so the figures always match
 * what {@code GET /api/budgets/{id}} shows.
 */
@Service
@Transactional(readOnly = true)
public class BudgetAnalyticsServiceImpl implements BudgetAnalyticsService
{
    private final FinancialPeriodService periodService;
    private final BudgetRepository budgetRepository;
    private final CategoryAllocationRepository allocationRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public BudgetAnalyticsServiceImpl(FinancialPeriodService periodService,
                                      BudgetRepository budgetRepository,
                                      CategoryAllocationRepository allocationRepository,
                                      ExpenseRepository expenseRepository,
                                      UserRepository userRepository)
    {
        this.periodService = periodService;
        this.budgetRepository = budgetRepository;
        this.allocationRepository = allocationRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    @Override
    public BudgetAnalyticsResponses.OverallBalance overallBalance(UUID userId, UUID periodId)
    {
        FinancialPeriod period = periodService.findMine(userId, periodId);
        Budget budget = resolveBudget(userId, period);
        String currency = currency(userId);

        ExpenseRepository.Totals totals = expenseRepository.totalsByBudget(userId, budget.getId());
        BigDecimal budgetAmount = budget.getOverallAmount();
        BigDecimal spent = totals.getTotal();
        BigDecimal balance = budgetAmount.subtract(spent);

        return BudgetAnalyticsResponses.OverallBalance.builder()
                .periodId(periodId)
                .budgetAmount(Money.of(budgetAmount, currency))
                .actualSpent(Money.of(spent, currency))
                .balance(Money.of(balance, currency))
                .build();
    }

    @Override
    public List<BudgetAnalyticsResponses.CategoryBalance> categoryBalance(UUID userId, UUID periodId)
    {
        FinancialPeriod period = periodService.findMine(userId, periodId);
        Budget budget = resolveBudget(userId, period);
        String currency = currency(userId);

        Map<UUID, BigDecimal> spentByCategory = spentByCategoryOf(userId, budget);
        List<CategoryAllocation> allocations = allocationRepository.findAllByBudgetId(budget.getId());

        return allocations.stream()
                .map(alloc -> {
                    BigDecimal allocated = alloc.getAllocatedAmount();
                    BigDecimal spent = spentByCategory.getOrDefault(alloc.getCategory().getId(), BigDecimal.ZERO);
                    BigDecimal balance = allocated.subtract(spent);

                    return BudgetAnalyticsResponses.CategoryBalance.builder()
                            .categoryId(alloc.getCategory().getId())
                            .allocated(Money.of(allocated, currency))
                            .spent(Money.of(spent, currency))
                            .balance(Money.of(balance, currency))
                            .overspent(balance.compareTo(BigDecimal.ZERO) < 0)
                            .build();
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // Budget resolution
    // ------------------------------------------------------------------

    /**
     * Finds the budget whose window overlaps the period's window the most.
     *
     * <p>If several budgets overlap, the one with the largest number of overlapping days is chosen.
     * On a tie, the one with the earliest start date wins.
     *
     * @throws ResourceNotFoundException if no budget overlaps the period
     */
    Budget resolveBudget(UUID userId, FinancialPeriod period)
    {
        List<Budget> all = budgetRepository.findAllOverlapping(
                userId, period.getStartDate(), period.getEndDate());

        if (all.isEmpty())
        {
            throw new ResourceNotFoundException("No budget for this period");
        }

        return all.stream()
                .max(Comparator
                        .comparingLong((Budget b) -> overlapDays(b, period))
                        .thenComparing(Budget::getStartDate, Comparator.reverseOrder()))
                .orElseThrow();
    }

    /**
     * Overlap in days between a budget's window and a period's window, both inclusive.
     */
    static long overlapDays(Budget budget, FinancialPeriod period)
    {
        LocalDate overlapStart = budget.getStartDate().isAfter(period.getStartDate())
                ? budget.getStartDate() : period.getStartDate();
        LocalDate overlapEnd = budget.getEndDate().isBefore(period.getEndDate())
                ? budget.getEndDate() : period.getEndDate();

        if (overlapStart.isAfter(overlapEnd))
        {
            return 0;
        }
        return ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
    }

    // ------------------------------------------------------------------
    // Plumbing — mirrors BudgetServiceImpl
    // ------------------------------------------------------------------

    private Map<UUID, BigDecimal> spentByCategoryOf(UUID userId, Budget budget)
    {
        Map<UUID, BigDecimal> spent = new HashMap<>();
        for (Object[] row : expenseRepository.sumByCategoryForBudget(userId, budget.getId()))
        {
            spent.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return spent;
    }

    private String currency(UUID userId)
    {
        return userRepository.findById(userId)
                .map(User::getBaseCurrency)
                .orElse(Money.DEFAULT_CURRENCY);
    }
}

