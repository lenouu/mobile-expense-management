package com.expensemanagement.backend.SavingsManagement.Services.ServiceImplements;

import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses.SavingsResponses;
import com.expensemanagement.backend.SavingsManagement.Services.ServiceInterface.SavingsService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Shared savings and debt calculation with automatic carry-forward (US44, US46, US48, US49).
 *
 * <p>Read-only. Carry-forward is derived automatically across contiguous periods without any
 * database write, schema migration, or background scheduler. The budget is never inspected:
 * savings and debt operate identically with or without a budget.
 */
@Service
@Transactional(readOnly = true)
public class SavingsServiceImpl implements SavingsService
{
    private final FinancialPeriodService periodService;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    public SavingsServiceImpl(FinancialPeriodService periodService,
                              IncomeRepository incomeRepository,
                              ExpenseRepository expenseRepository,
                              UserRepository userRepository)
    {
        this.periodService = periodService;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<SavingsResponses.PeriodSummary> summariesUpTo(UUID userId, FinancialPeriod period)
    {
        List<FinancialPeriod> periods = getPeriodsUpTo(period);
        String currency = currency(userId);

        List<SavingsResponses.PeriodSummary> summaries = new ArrayList<>(periods.size());
        BigDecimal carriedDebt = BigDecimal.ZERO;

        for (FinancialPeriod p : periods)
        {
            LocalDate from = p.getStartDate();
            LocalDate to = p.getEndDate();

            BigDecimal totalIncome = scale(incomeRepository.sumByUserIdAndDateBetween(userId, from, to));
            BigDecimal totalExpenses = scale(expenseRepository.totalsBetween(userId, from, to).getTotal());

            BigDecimal effectiveIncome = scale(totalIncome.subtract(carriedDebt));
            BigDecimal net = scale(effectiveIncome.subtract(totalExpenses));

            BigDecimal savings = null;
            BigDecimal debt = null;

            int cmp = net.compareTo(BigDecimal.ZERO);
            if (cmp > 0)
            {
                savings = net;
            }
            else if (cmp < 0)
            {
                debt = net.negate();
            }

            summaries.add(SavingsResponses.PeriodSummary.builder()
                    .periodId(p.getId())
                    .totalIncome(Money.of(totalIncome, currency))
                    .totalExpenses(Money.of(totalExpenses, currency))
                    .carriedDebt(Money.of(scale(carriedDebt), currency))
                    .effectiveIncome(Money.of(effectiveIncome, currency))
                    .savings(savings != null ? Money.of(savings, currency) : null)
                    .debt(debt != null ? Money.of(debt, currency) : null)
                    .build());

            carriedDebt = debt != null ? debt : BigDecimal.ZERO;
        }

        return summaries;
    }

    @Override
    public SavingsResponses.PeriodSummary summaryFor(UUID userId, UUID periodId)
    {
        FinancialPeriod period = periodService.findMine(userId, periodId);
        return summaryFor(userId, period);
    }

    @Override
    public SavingsResponses.PeriodSummary summaryFor(UUID userId, FinancialPeriod period)
    {
        List<SavingsResponses.PeriodSummary> summaries = summariesUpTo(userId, period);
        return summaries.get(summaries.size() - 1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Walks backwards from {@code target} via {@link FinancialPeriodService#previous(FinancialPeriod)}
     * until the user's first period (sequence 1), producing an ordered list of periods in ascending
     * sequence.
     */
    private List<FinancialPeriod> getPeriodsUpTo(FinancialPeriod target)
    {
        LinkedList<FinancialPeriod> periods = new LinkedList<>();
        FinancialPeriod current = target;
        while (current != null)
        {
            periods.addFirst(current);
            if (current.getSequence() <= 1)
            {
                break;
            }
            current = periodService.previous(current).orElse(null);
        }
        return periods;
    }

    private String currency(UUID userId)
    {
        return userRepository.findById(userId)
                .map(User::getBaseCurrency)
                .orElse(Money.DEFAULT_CURRENCY);
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }
}

