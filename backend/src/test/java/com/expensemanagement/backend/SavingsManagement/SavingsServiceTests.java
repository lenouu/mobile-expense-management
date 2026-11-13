package com.expensemanagement.backend.SavingsManagement;

import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses.SavingsResponses;
import com.expensemanagement.backend.SavingsManagement.Services.ServiceImplements.SavingsServiceImpl;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
//import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SavingsServiceImpl} (US44, US46, US48, US49).
 *
 * <p>
 * Pure Mockito — no Spring context, no database, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class SavingsServiceTests {
    @Mock
    private FinancialPeriodService periodService;
    @Mock
    private IncomeRepository incomeRepository;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SavingsServiceImpl savingsService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID P1_ID = UUID.randomUUID();
    private static final UUID P2_ID = UUID.randomUUID();
    private static final UUID P3_ID = UUID.randomUUID();

    private FinancialPeriod p1;
    private FinancialPeriod p2;
    private FinancialPeriod p3;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setBaseCurrency("XAF");

        p1 = new FinancialPeriod();
        p1.setId(P1_ID);
        p1.setUser(user);
        p1.setSequence(1);
        p1.setStartDate(LocalDate.of(2026, 9, 1));
        p1.setEndDate(LocalDate.of(2026, 9, 30));

        p2 = new FinancialPeriod();
        p2.setId(P2_ID);
        p2.setUser(user);
        p2.setSequence(2);
        p2.setStartDate(LocalDate.of(2026, 10, 1));
        p2.setEndDate(LocalDate.of(2026, 10, 31));

        p3 = new FinancialPeriod();
        p3.setId(P3_ID);
        p3.setUser(user);
        p3.setSequence(3);
        p3.setStartDate(LocalDate.of(2026, 11, 1));
        p3.setEndDate(LocalDate.of(2026, 11, 30));
    }

    // ------------------------------------------------------------------
    // Single period basic rules
    // ------------------------------------------------------------------

    @Test
    void incomeGreaterThanExpenses_resultsInSavingsAndNullDebt() {
        stubUser();
        when(periodService.findMine(USER_ID, P1_ID)).thenReturn(p1);
        stubIncomeAndExpense(p1, new BigDecimal("1000.00"), new BigDecimal("600.00"));

        SavingsResponses.PeriodSummary summary = savingsService.summaryFor(USER_ID, P1_ID);

        assertThat(summary.getPeriodId()).isEqualTo(P1_ID);
        assertThat(summary.getTotalIncome().amount()).isEqualByComparingTo("1000.00");
        assertThat(summary.getTotalExpenses().amount()).isEqualByComparingTo("600.00");
        assertThat(summary.getCarriedDebt().amount()).isEqualByComparingTo("0.00");
        assertThat(summary.getEffectiveIncome().amount()).isEqualByComparingTo("1000.00");
        assertThat(summary.getSavings()).isNotNull();
        assertThat(summary.getSavings().amount()).isEqualByComparingTo("400.00");
        assertThat(summary.getSavings().currency()).isEqualTo("XAF");
        assertThat(summary.getDebt()).isNull();
    }

    @Test
    void expensesGreaterThanIncome_resultsInDebtAndNullSavings() {
        stubUser();
        when(periodService.findMine(USER_ID, P1_ID)).thenReturn(p1);
        stubIncomeAndExpense(p1, new BigDecimal("500.00"), new BigDecimal("800.00"));

        SavingsResponses.PeriodSummary summary = savingsService.summaryFor(USER_ID, P1_ID);

        assertThat(summary.getSavings()).isNull();
        assertThat(summary.getDebt()).isNotNull();
        assertThat(summary.getDebt().amount()).isEqualByComparingTo("300.00");
        assertThat(summary.getDebt().currency()).isEqualTo("XAF");
    }

    @Test
    void incomeEqualsExpenses_bothSavingsAndDebtAreNull() {
        stubUser();
        when(periodService.findMine(USER_ID, P1_ID)).thenReturn(p1);
        stubIncomeAndExpense(p1, new BigDecimal("500.00"), new BigDecimal("500.00"));

        SavingsResponses.PeriodSummary summary = savingsService.summaryFor(USER_ID, P1_ID);

        assertThat(summary.getEffectiveIncome().amount()).isEqualByComparingTo("500.00");
        assertThat(summary.getSavings()).isNull();
        assertThat(summary.getDebt()).isNull();
    }

    @Test
    void firstPeriod_carriedDebtIsZero() {
        stubUser();
        when(periodService.findMine(USER_ID, P1_ID)).thenReturn(p1);
        stubIncomeAndExpense(p1, new BigDecimal("200.00"), new BigDecimal("100.00"));

        SavingsResponses.PeriodSummary summary = savingsService.summaryFor(USER_ID, P1_ID);

        assertThat(summary.getCarriedDebt().amount()).isEqualByComparingTo("0.00");
        assertThat(summary.getEffectiveIncome().amount()).isEqualByComparingTo("200.00");
    }

    // ------------------------------------------------------------------
    // Multi-period carry-forward chain
    // ------------------------------------------------------------------

    @Test
    void p1DebtCarriesIntoP2_carriedDebtAndEffectiveIncomeCorrect() {
        stubUser();
        when(periodService.findMine(USER_ID, P2_ID)).thenReturn(p2);
        when(periodService.previous(p2)).thenReturn(Optional.of(p1));

        // P1: income 100, expenses 250 -> debt 150
        stubIncomeAndExpense(p1, new BigDecimal("100.00"), new BigDecimal("250.00"));
        // P2: income 300, expenses 50 -> effectiveIncome = 300 - 150 = 150 -> net = 150
        // - 50 = 100 savings
        stubIncomeAndExpense(p2, new BigDecimal("300.00"), new BigDecimal("50.00"));

        SavingsResponses.PeriodSummary summary = savingsService.summaryFor(USER_ID, P2_ID);

        assertThat(summary.getPeriodId()).isEqualTo(P2_ID);
        assertThat(summary.getTotalIncome().amount()).isEqualByComparingTo("300.00");
        assertThat(summary.getCarriedDebt().amount()).isEqualByComparingTo("150.00");
        assertThat(summary.getEffectiveIncome().amount()).isEqualByComparingTo("150.00");
        assertThat(summary.getTotalExpenses().amount()).isEqualByComparingTo("50.00");
        assertThat(summary.getSavings()).isNotNull();
        assertThat(summary.getSavings().amount()).isEqualByComparingTo("100.00");
        assertThat(summary.getDebt()).isNull();
    }

    @Test
    void p2IncomeCoversDebt_p2DebtNullAndP3CarriedDebtIsZero() {
        stubUser();
        when(periodService.previous(p3)).thenReturn(Optional.of(p2));
        when(periodService.previous(p2)).thenReturn(Optional.of(p1));

        // P1: debt 100
        stubIncomeAndExpense(p1, new BigDecimal("0.00"), new BigDecimal("100.00"));
        // P2: covers debt 100 and yields 50 savings -> debt is null
        stubIncomeAndExpense(p2, new BigDecimal("200.00"), new BigDecimal("50.00"));
        // P3: carriedDebt must be 0 (debt was not deducted again)
        stubIncomeAndExpense(p3, new BigDecimal("100.00"), new BigDecimal("80.00"));

        List<SavingsResponses.PeriodSummary> summaries = savingsService.summariesUpTo(USER_ID, p3);

        assertThat(summaries).hasSize(3);
        // P2 summary:
        SavingsResponses.PeriodSummary p2Summary = summaries.get(1);
        assertThat(p2Summary.getCarriedDebt().amount()).isEqualByComparingTo("100.00");
        assertThat(p2Summary.getDebt()).isNull();
        assertThat(p2Summary.getSavings().amount()).isEqualByComparingTo("50.00");

        // P3 summary: carriedDebt is 0.00, debt not deducted again
        SavingsResponses.PeriodSummary p3Summary = summaries.get(2);
        assertThat(p3Summary.getCarriedDebt().amount()).isEqualByComparingTo("0.00");
        assertThat(p3Summary.getEffectiveIncome().amount()).isEqualByComparingTo("100.00");
        assertThat(p3Summary.getSavings().amount()).isEqualByComparingTo("20.00");
        assertThat(p3Summary.getDebt()).isNull();
    }

    @Test
    void p2IncomeOnlyPartlyCoversDebt_remainderCarriesIntoP3() {
        stubUser();
        when(periodService.previous(p3)).thenReturn(Optional.of(p2));
        when(periodService.previous(p2)).thenReturn(Optional.of(p1));

        // P1: debt 100
        stubIncomeAndExpense(p1, new BigDecimal("0.00"), new BigDecimal("100.00"));
        // P2: income 40, expenses 0 -> effectiveIncome = 40 - 100 = -60 -> net = -60 ->
        // debt = 60
        stubIncomeAndExpense(p2, new BigDecimal("40.00"), new BigDecimal("0.00"));
        // P3: carriedDebt = 60, income = 100, expenses = 20 -> effectiveIncome = 40,
        // savings = 20
        stubIncomeAndExpense(p3, new BigDecimal("100.00"), new BigDecimal("20.00"));

        List<SavingsResponses.PeriodSummary> summaries = savingsService.summariesUpTo(USER_ID, p3);

        assertThat(summaries).hasSize(3);
        SavingsResponses.PeriodSummary p2Summary = summaries.get(1);
        assertThat(p2Summary.getCarriedDebt().amount()).isEqualByComparingTo("100.00");
        assertThat(p2Summary.getEffectiveIncome().amount()).isEqualByComparingTo("-60.00");
        assertThat(p2Summary.getDebt().amount()).isEqualByComparingTo("60.00");

        SavingsResponses.PeriodSummary p3Summary = summaries.get(2);
        assertThat(p3Summary.getCarriedDebt().amount()).isEqualByComparingTo("60.00");
        assertThat(p3Summary.getEffectiveIncome().amount()).isEqualByComparingTo("40.00");
        assertThat(p3Summary.getSavings().amount()).isEqualByComparingTo("20.00");
        assertThat(p3Summary.getDebt()).isNull();
    }

    @Test
    void threePeriodChain_walksAllSummariesInAscendingOrder() {
        stubUser();
        when(periodService.previous(p3)).thenReturn(Optional.of(p2));
        when(periodService.previous(p2)).thenReturn(Optional.of(p1));

        stubIncomeAndExpense(p1, new BigDecimal("500.00"), new BigDecimal("300.00"));
        stubIncomeAndExpense(p2, new BigDecimal("400.00"), new BigDecimal("600.00"));
        stubIncomeAndExpense(p3, new BigDecimal("700.00"), new BigDecimal("200.00"));

        List<SavingsResponses.PeriodSummary> result = savingsService.summariesUpTo(USER_ID, p3);

        assertThat(result).hasSize(3);
        // P1: income 500, exp 300 -> savings 200, debt null, carried 0
        assertThat(result.get(0).getPeriodId()).isEqualTo(P1_ID);
        assertThat(result.get(0).getCarriedDebt().amount()).isEqualByComparingTo("0.00");
        assertThat(result.get(0).getSavings().amount()).isEqualByComparingTo("200.00");

        // P2: carried 0, income 400, exp 600 -> savings null, debt 200
        assertThat(result.get(1).getPeriodId()).isEqualTo(P2_ID);
        assertThat(result.get(1).getCarriedDebt().amount()).isEqualByComparingTo("0.00");
        assertThat(result.get(1).getDebt().amount()).isEqualByComparingTo("200.00");

        // P3: carried 200, income 700 -> effectiveIncome 500, exp 200 -> savings 300,
        // debt null
        assertThat(result.get(2).getPeriodId()).isEqualTo(P3_ID);
        assertThat(result.get(2).getCarriedDebt().amount()).isEqualByComparingTo("200.00");
        assertThat(result.get(2).getEffectiveIncome().amount()).isEqualByComparingTo("500.00");
        assertThat(result.get(2).getSavings().amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void foreignOrMissingPeriod_throws404() {
        when(periodService.findMine(USER_ID, P1_ID))
                .thenThrow(new ResourceNotFoundException("No financial period with id " + P1_ID));

        assertThatThrownBy(() -> savingsService.summaryFor(USER_ID, P1_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No financial period with id " + P1_ID);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void stubIncomeAndExpense(FinancialPeriod period, BigDecimal income, BigDecimal expenses) {
        when(incomeRepository.sumByUserIdAndDateBetween(USER_ID, period.getStartDate(), period.getEndDate()))
                .thenReturn(income);

        ExpenseRepository.Totals totals = new ExpenseRepository.Totals() {
            @Override
            public BigDecimal getTotal() {
                return expenses;
            }

            @Override
            public long getCount() {
                return 1L;
            }
        };
        when(expenseRepository.totalsBetween(USER_ID, period.getStartDate(), period.getEndDate()))
                .thenReturn(totals);
    }
}
