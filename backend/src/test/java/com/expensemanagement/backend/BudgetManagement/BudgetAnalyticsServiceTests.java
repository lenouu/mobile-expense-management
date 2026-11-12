package com.expensemanagement.backend.BudgetManagement;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetAnalyticsResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements.BudgetAnalyticsServiceImpl;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BudgetAnalyticsServiceImpl} (US41, US42).
 *
 * <p>Pure Mockito — no Spring context, no database, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class BudgetAnalyticsServiceTests
{
    @Mock private FinancialPeriodService periodService;
    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryAllocationRepository allocationRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private BudgetAnalyticsServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID BUDGET_ID = UUID.randomUUID();
    private static final UUID CAT_A = UUID.randomUUID();
    private static final UUID CAT_B = UUID.randomUUID();

    private FinancialPeriod period;
    private Budget budget;
    private User user;

    @BeforeEach
    void setUp()
    {
        period = new FinancialPeriod();
        period.setId(PERIOD_ID);
        period.setStartDate(LocalDate.of(2026, 11, 1));
        period.setEndDate(LocalDate.of(2026, 11, 30));

        budget = new Budget();
        budget.setId(BUDGET_ID);
        budget.setStartDate(LocalDate.of(2026, 11, 1));
        budget.setEndDate(LocalDate.of(2026, 11, 30));
        budget.setOverallAmount(new BigDecimal("100000.00"));

        user = new User();
        user.setId(USER_ID);
        user.setBaseCurrency("XAF");
    }

    // ------------------------------------------------------------------
    // US41 — overall balance
    // ------------------------------------------------------------------

    @Test
    void overallBalance_underBudget()
    {
        stubPeriodAndBudget();
        stubTotals(new BigDecimal("60000.00"), 5);

        BudgetAnalyticsResponses.OverallBalance result = service.overallBalance(USER_ID, PERIOD_ID);

        assertThat(result.getPeriodId()).isEqualTo(PERIOD_ID);
        assertThat(result.getBudgetAmount().amount()).isEqualByComparingTo("100000.00");
        assertThat(result.getActualSpent().amount()).isEqualByComparingTo("60000.00");
        assertThat(result.getBalance().amount()).isEqualByComparingTo("40000.00");
        assertThat(result.getBalance().currency()).isEqualTo("XAF");
    }

    @Test
    void overallBalance_overBudget_negativeBalance_noException()
    {
        stubPeriodAndBudget();
        stubTotals(new BigDecimal("120000.00"), 8);

        BudgetAnalyticsResponses.OverallBalance result = service.overallBalance(USER_ID, PERIOD_ID);

        assertThat(result.getBalance().amount()).isEqualByComparingTo("-20000.00");
        assertThat(result.getActualSpent().amount()).isEqualByComparingTo("120000.00");
    }

    @Test
    void overallBalance_noBudgetForPeriod_throws404()
    {
        when(periodService.findMine(USER_ID, PERIOD_ID)).thenReturn(period);
        when(budgetRepository.findAllOverlapping(eq(USER_ID), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.overallBalance(USER_ID, PERIOD_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No budget for this period");
    }

    @Test
    void overallBalance_foreignPeriod_throws404()
    {
        when(periodService.findMine(USER_ID, PERIOD_ID))
                .thenThrow(new ResourceNotFoundException("Period not found"));

        assertThatThrownBy(() -> service.overallBalance(USER_ID, PERIOD_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // US42 — category balance
    // ------------------------------------------------------------------

    @Test
    void categoryBalance_overspent_flagIsTrue()
    {
        stubPeriodAndBudget();

        CategoryAllocation allocA = allocation(CAT_A, "30000.00");
        when(allocationRepository.findAllByBudgetId(BUDGET_ID)).thenReturn(List.of(allocA));
        when(expenseRepository.sumByCategoryForBudget(USER_ID, BUDGET_ID))
                .thenReturn(Collections.singletonList(new Object[]{CAT_A, new BigDecimal("35000.00")}));

        List<BudgetAnalyticsResponses.CategoryBalance> result =
                service.categoryBalance(USER_ID, PERIOD_ID);

        assertThat(result).hasSize(1);
        BudgetAnalyticsResponses.CategoryBalance row = result.get(0);
        assertThat(row.getCategoryId()).isEqualTo(CAT_A);
        assertThat(row.getAllocated().amount()).isEqualByComparingTo("30000.00");
        assertThat(row.getSpent().amount()).isEqualByComparingTo("35000.00");
        assertThat(row.getBalance().amount()).isEqualByComparingTo("-5000.00");
        assertThat(row.isOverspent()).isTrue();
    }

    @Test
    void categoryBalance_underOrEqualBudget_overspentIsFalse()
    {
        stubPeriodAndBudget();

        CategoryAllocation allocA = allocation(CAT_A, "30000.00");
        CategoryAllocation allocB = allocation(CAT_B, "50000.00");
        when(allocationRepository.findAllByBudgetId(BUDGET_ID)).thenReturn(List.of(allocA, allocB));
        when(expenseRepository.sumByCategoryForBudget(USER_ID, BUDGET_ID))
                .thenReturn(List.<Object[]>of(
                        new Object[]{CAT_A, new BigDecimal("30000.00")},
                        new Object[]{CAT_B, new BigDecimal("40000.00")}));

        List<BudgetAnalyticsResponses.CategoryBalance> result =
                service.categoryBalance(USER_ID, PERIOD_ID);

        assertThat(result).hasSize(2);
        // CAT_A: spent == allocated → not overspent
        assertThat(result.get(0).getBalance().amount()).isEqualByComparingTo("0");
        assertThat(result.get(0).isOverspent()).isFalse();
        // CAT_B: spent < allocated → not overspent
        assertThat(result.get(1).getBalance().amount()).isEqualByComparingTo("10000.00");
        assertThat(result.get(1).isOverspent()).isFalse();
    }

    @Test
    void categoryBalance_noSpend_spentIsZero()
    {
        stubPeriodAndBudget();

        CategoryAllocation allocA = allocation(CAT_A, "30000.00");
        when(allocationRepository.findAllByBudgetId(BUDGET_ID)).thenReturn(List.of(allocA));
        when(expenseRepository.sumByCategoryForBudget(USER_ID, BUDGET_ID))
                .thenReturn(Collections.emptyList());

        List<BudgetAnalyticsResponses.CategoryBalance> result =
                service.categoryBalance(USER_ID, PERIOD_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSpent().amount()).isEqualByComparingTo("0");
        assertThat(result.get(0).getBalance().amount()).isEqualByComparingTo("30000.00");
        assertThat(result.get(0).isOverspent()).isFalse();
    }

    // ------------------------------------------------------------------
    // Budget resolution — two overlapping budgets
    // ------------------------------------------------------------------

    @Test
    void resolveBudget_twoOverlapping_picksLargerOverlap()
    {
        // period: Nov 1–30
        // budgetA: Nov 1–15 → 15 days overlap
        // budgetB: Nov 10–Dec 15 → 21 days overlap → should be picked
        Budget budgetA = new Budget();
        budgetA.setId(UUID.randomUUID());
        budgetA.setStartDate(LocalDate.of(2026, 11, 1));
        budgetA.setEndDate(LocalDate.of(2026, 11, 15));
        budgetA.setOverallAmount(new BigDecimal("50000.00"));

        Budget budgetB = new Budget();
        budgetB.setId(UUID.randomUUID());
        budgetB.setStartDate(LocalDate.of(2026, 11, 10));
        budgetB.setEndDate(LocalDate.of(2026, 12, 15));
        budgetB.setOverallAmount(new BigDecimal("80000.00"));

        when(periodService.findMine(USER_ID, PERIOD_ID)).thenReturn(period);
        when(budgetRepository.findAllOverlapping(USER_ID, period.getStartDate(), period.getEndDate()))
                .thenReturn(List.of(budgetA, budgetB));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // Wire up budget B for the totals call
        stubTotalsFor(budgetB.getId(), new BigDecimal("40000.00"), 3);

        BudgetAnalyticsResponses.OverallBalance result = service.overallBalance(USER_ID, PERIOD_ID);

        // budgetB has 21 overlap days vs 15, so it was picked
        assertThat(result.getBudgetAmount().amount()).isEqualByComparingTo("80000.00");
    }

    @Test
    void resolveBudget_tieInOverlap_picksEarliestStartDate()
    {
        // period: Nov 1–30
        // budgetA: Nov 1–20 → 20 days overlap
        // budgetB: Nov 11–30 → 20 days overlap → tie → earliest startDate wins → budgetA
        Budget budgetA = new Budget();
        budgetA.setId(UUID.randomUUID());
        budgetA.setStartDate(LocalDate.of(2026, 11, 1));
        budgetA.setEndDate(LocalDate.of(2026, 11, 20));
        budgetA.setOverallAmount(new BigDecimal("50000.00"));

        Budget budgetB = new Budget();
        budgetB.setId(UUID.randomUUID());
        budgetB.setStartDate(LocalDate.of(2026, 11, 11));
        budgetB.setEndDate(LocalDate.of(2026, 11, 30));
        budgetB.setOverallAmount(new BigDecimal("80000.00"));

        when(periodService.findMine(USER_ID, PERIOD_ID)).thenReturn(period);
        when(budgetRepository.findAllOverlapping(USER_ID, period.getStartDate(), period.getEndDate()))
                .thenReturn(List.of(budgetA, budgetB));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        stubTotalsFor(budgetA.getId(), new BigDecimal("20000.00"), 2);

        BudgetAnalyticsResponses.OverallBalance result = service.overallBalance(USER_ID, PERIOD_ID);

        assertThat(result.getBudgetAmount().amount()).isEqualByComparingTo("50000.00");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubPeriodAndBudget()
    {
        when(periodService.findMine(USER_ID, PERIOD_ID)).thenReturn(period);
        when(budgetRepository.findAllOverlapping(USER_ID, period.getStartDate(), period.getEndDate()))
                .thenReturn(List.of(budget));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void stubTotals(BigDecimal total, long count)
    {
        stubTotalsFor(BUDGET_ID, total, count);
    }

    private void stubTotalsFor(UUID budgetId, BigDecimal total, long count)
    {
        ExpenseRepository.Totals totals = new ExpenseRepository.Totals()
        {
            @Override public BigDecimal getTotal() { return total; }
            @Override public long getCount() { return count; }
        };
        when(expenseRepository.totalsByBudget(USER_ID, budgetId)).thenReturn(totals);
    }

    private CategoryAllocation allocation(UUID categoryId, String amount)
    {
        UserCategory category = new UserCategory();
        category.setId(categoryId);

        CategoryAllocation alloc = new CategoryAllocation();
        alloc.setId(UUID.randomUUID());
        alloc.setBudget(budget);
        alloc.setCategory(category);
        alloc.setAllocatedAmount(new BigDecimal(amount));
        return alloc;
    }
}

