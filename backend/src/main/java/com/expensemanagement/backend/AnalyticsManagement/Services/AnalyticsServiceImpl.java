package com.expensemanagement.backend.AnalyticsManagement.Services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expensemanagement.backend.AnalyticsManagement.Dtos.AnalyticsResponses;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeSourceRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * What the Dashboard is made of.
 *
 * <p>One method, one window, one answer: income, expenses and the difference between them for the
 * chosen days and for the window of the same length before it; both windows day by day and in seven
 * buckets, aligned so they can be drawn against each other; where the money came from and where it
 * went, each compared with its own past; the best and worst day by net; the largest single expense;
 * and the plan in force.
 *
 * <p>Aligning the two windows is what makes a comparison possible at all: bucket 3 of this window
 * and bucket 3 of the one before cover the same number of days, so a paired bar means something.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class AnalyticsServiceImpl
{
    private static final int BUCKETS = 7;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM");

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public AnalyticsServiceImpl(ExpenseRepository expenseRepository,
                                IncomeRepository incomeRepository,
                                IncomeSourceRepository incomeSourceRepository,
                                UserCategoryRepository userCategoryRepository,
                                BudgetRepository budgetRepository,
                                UserRepository userRepository)
    {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    public AnalyticsResponses.Summary summary(UUID userId, LocalDate from, LocalDate to)
    {
        if (from == null || to == null)
        {
            throw new InvalidRequestException("Both from and to are required.");
        }
        if (from.isAfter(to))
        {
            throw new InvalidRequestException("The start date is after the end date.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        String currency = user.getBaseCurrency();

        long length = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(length - 1);

        AnalyticsResponses.Totals current = totals(userId, from, to, length, currency);
        AnalyticsResponses.Totals previous = totals(userId, previousFrom, previousTo, length, currency);

        List<AnalyticsResponses.DailyPoint> daily = daily(userId, from, to, currency);
        List<AnalyticsResponses.DailyPoint> previousDaily =
                daily(userId, previousFrom, previousTo, currency);

        AnalyticsResponses.Change change = AnalyticsResponses.Change.builder()
                .incomePercent(percent(current.getIncome().getAmount(), previous.getIncome().getAmount()))
                .expensePercent(percent(current.getExpenses().getAmount(), previous.getExpenses().getAmount()))
                .netPercent(percent(current.getNet().getAmount(), previous.getNet().getAmount()))
                .build();

        return AnalyticsResponses.Summary.builder()
                .from(from)
                .to(to)
                .currency(currency)
                .current(current)
                .previousFrom(previousFrom)
                .previousTo(previousTo)
                .previous(previous)
                .change(change)
                .daily(daily)
                .buckets(buckets(daily, from, to, currency))
                .previousDaily(previousDaily)
                .previousBuckets(buckets(previousDaily, previousFrom, previousTo, currency))
                .byCategory(byCategory(userId, from, to, previousFrom, previousTo, currency,
                        current.getExpenses().getAmount()))
                .bySource(bySource(userId, from, to, previousFrom, previousTo, currency,
                        current.getIncome().getAmount()))
                .bestDay(best(daily, true))
                .worstDay(best(daily, false))
                .biggestExpense(biggest(userId, from, to, currency))
                .noSpendDays(daily.stream().filter(day -> day.getExpenses().getAmount().signum() == 0).count())
                .budget(budget(userId, currency))
                .build();
    }

    // ------------------------------------------------------------------
    // Pieces
    // ------------------------------------------------------------------

    private AnalyticsResponses.Totals totals(UUID userId, LocalDate from, LocalDate to, long days,
                                             String currency)
    {
        ExpenseRepository.Totals expenses = expenseRepository.totalsMatching(userId, from, to, null, null);
        BigDecimal income = incomeRepository.sumByUserIdAndDateBetween(userId, from, to);
        long incomeCount = incomeRepository.countByUserIdAndDateBetween(userId, from, to);

        BigDecimal net = income.subtract(expenses.getTotal());
        Double rate = income.signum() == 0 ? null
                : net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP).doubleValue();

        BigDecimal average = days <= 0 ? BigDecimal.ZERO
                : expenses.getTotal().divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        return AnalyticsResponses.Totals.builder()
                .income(money(income, currency))
                .expenses(money(expenses.getTotal(), currency))
                .net(money(net, currency))
                .savingsRate(rate)
                .averagePerDay(money(average, currency))
                .incomeCount(incomeCount)
                .expenseCount(expenses.getCount())
                .build();
    }

    private List<AnalyticsResponses.DailyPoint> daily(UUID userId, LocalDate from, LocalDate to,
                                                      String currency)
    {
        Map<LocalDate, BigDecimal> income = new HashMap<>();
        for (Object[] row : incomeRepository.sumByDate(userId, from, to))
        {
            income.put((LocalDate) row[0], (BigDecimal) row[1]);
        }

        Map<LocalDate, BigDecimal> expense = new HashMap<>();
        for (Object[] row : expenseRepository.sumByDate(userId, from, to))
        {
            expense.put((LocalDate) row[0], (BigDecimal) row[1]);
        }

        List<AnalyticsResponses.DailyPoint> points = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1))
        {
            points.add(AnalyticsResponses.DailyPoint.builder()
                    .date(date)
                    .income(money(income.getOrDefault(date, BigDecimal.ZERO), currency))
                    .expenses(money(expense.getOrDefault(date, BigDecimal.ZERO), currency))
                    .build());
        }

        return points;
    }

    /** The window in at most seven equal segments, labelled with their own dates. */
    private List<AnalyticsResponses.Bucket> buckets(List<AnalyticsResponses.DailyPoint> daily,
                                                    LocalDate from, LocalDate to, String currency)
    {
        long length = ChronoUnit.DAYS.between(from, to) + 1;
        int size = (int) Math.max(1, Math.ceil((double) length / BUCKETS));

        List<AnalyticsResponses.Bucket> buckets = new ArrayList<>();
        for (int start = 0; start < daily.size(); start += size)
        {
            int end = Math.min(daily.size(), start + size);
            List<AnalyticsResponses.DailyPoint> slice = daily.subList(start, end);
            if (slice.isEmpty())
            {
                continue;
            }

            BigDecimal income = slice.stream().map(p -> p.getIncome().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal expenses = slice.stream().map(p -> p.getExpenses().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LocalDate bucketFrom = slice.get(0).getDate();
            LocalDate bucketTo = slice.get(slice.size() - 1).getDate();

            buckets.add(AnalyticsResponses.Bucket.builder()
                    .label(bucketFrom.equals(bucketTo) ? bucketFrom.format(DAY_MONTH)
                            : bucketFrom.format(DAY_MONTH) + " – " + bucketTo.format(DAY_MONTH))
                    .from(bucketFrom)
                    .to(bucketTo)
                    .income(money(income, currency))
                    .expenses(money(expenses, currency))
                    .build());
        }

        return buckets;
    }

    private List<AnalyticsResponses.CategorySlice> byCategory(UUID userId, LocalDate from, LocalDate to,
                                                              LocalDate previousFrom, LocalDate previousTo,
                                                              String currency, BigDecimal total)
    {
        Map<UUID, BigDecimal> before = new HashMap<>();
        for (Object[] row : expenseRepository.sumByCategory(userId, previousFrom, previousTo))
        {
            before.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<AnalyticsResponses.CategorySlice> slices = new ArrayList<>();

        for (Object[] row : expenseRepository.sumByCategory(userId, from, to))
        {
            UUID categoryId = (UUID) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            BigDecimal previous = before.getOrDefault(categoryId, BigDecimal.ZERO);
            Double moved = previous.signum() == 0 ? null : percent(amount, previous);

            userCategoryRepository.findByIdAndUserId(categoryId, userId).ifPresent(category ->
                    slices.add(AnalyticsResponses.CategorySlice.builder()
                            .id(categoryId)
                            .name(category.getName())
                            .icon(category.getIcon())
                            .color(category.getColor())
                            .total(money(amount, currency))
                            .previousTotal(money(previous, currency))
                            .changePercent(moved)
                            .share(share(amount, total))
                            .build()));
        }

        slices.sort((a, b) -> b.getTotal().getAmount().compareTo(a.getTotal().getAmount()));
        return slices;
    }

    private List<AnalyticsResponses.SourceSlice> bySource(UUID userId, LocalDate from, LocalDate to,
                                                          LocalDate previousFrom, LocalDate previousTo,
                                                          String currency, BigDecimal total)
    {
        Map<UUID, BigDecimal> before = new HashMap<>();
        for (Object[] row : incomeRepository.sumBySource(userId, previousFrom, previousTo))
        {
            before.put((UUID) row[0], (BigDecimal) row[1]);
        }

        List<AnalyticsResponses.SourceSlice> slices = new ArrayList<>();

        for (Object[] row : incomeRepository.sumBySource(userId, from, to))
        {
            UUID sourceId = (UUID) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            BigDecimal previous = before.getOrDefault(sourceId, BigDecimal.ZERO);
            Double moved = previous.signum() == 0 ? null : percent(amount, previous);

            // Scoped by owner: a source deleted between the queries is skipped rather than failing
            // the whole dashboard.
            incomeSourceRepository.findByIdAndUserId(sourceId, userId).ifPresent(source ->
                    slices.add(AnalyticsResponses.SourceSlice.builder()
                            .id(sourceId)
                            .name(source.getName())
                            .icon(source.getIcon())
                            .color(source.getColor())
                            .total(money(amount, currency))
                            .previousTotal(money(previous, currency))
                            .changePercent(moved)
                            .share(share(amount, total))
                            .build()));
        }

        slices.sort((a, b) -> b.getTotal().getAmount().compareTo(a.getTotal().getAmount()));
        return slices;
    }

    /** The highest or lowest net day, ignoring days where nothing happened at all. */
    private AnalyticsResponses.DaySummary best(List<AnalyticsResponses.DailyPoint> daily, boolean highest)
    {
        AnalyticsResponses.DaySummary best = null;
        BigDecimal bestNet = null;

        for (AnalyticsResponses.DailyPoint point : daily)
        {
            boolean silent = point.getIncome().getAmount().signum() == 0
                    && point.getExpenses().getAmount().signum() == 0;
            if (silent)
            {
                continue;
            }

            BigDecimal net = point.getIncome().getAmount().subtract(point.getExpenses().getAmount());
            if (bestNet == null || (highest ? net.compareTo(bestNet) > 0 : net.compareTo(bestNet) < 0))
            {
                bestNet = net;
                best = AnalyticsResponses.DaySummary.builder()
                        .date(point.getDate())
                        .income(point.getIncome())
                        .expenses(point.getExpenses())
                        .net(money(net, point.getIncome().getCurrency()))
                        .build();
            }
        }

        return best;
    }

    private AnalyticsResponses.BiggestExpense biggest(UUID userId, LocalDate from, LocalDate to,
                                                      String currency)
    {
        List<Expense> top = expenseRepository
                .findTopByUserIdAndDateBetweenOrderByAmountDesc(userId, from, to);
        if (top.isEmpty())
        {
            return null;
        }

        Expense expense = top.get(0);

        return AnalyticsResponses.BiggestExpense.builder()
                .date(expense.getDate())
                .categoryName(expense.getCategory() != null ? expense.getCategory().getName() : null)
                .icon(expense.getCategory() != null ? expense.getCategory().getIcon() : null)
                .color(expense.getCategory() != null ? expense.getCategory().getColor() : null)
                .description(expense.getDescription())
                .amount(money(expense.getAmount(), currency))
                .build();
    }

    private AnalyticsResponses.BudgetSnapshot budget(UUID userId, String currency)
    {
        var budgets = budgetRepository.findAllByUserIdNewestFirst(userId);
        var live = budgets.stream()
                .filter(b -> b.getStatus() == BudgetStatus.ACTIVE || b.getStatus() == BudgetStatus.UPCOMING)
                .findFirst()
                .orElse(null);

        if (live == null)
        {
            return null;
        }

        ExpenseRepository.Totals spent = expenseRepository.totalsByBudget(userId, live.getId());
        BigDecimal remaining = live.getOverallAmount().subtract(spent.getTotal());

        return AnalyticsResponses.BudgetSnapshot.builder()
                .id(live.getId())
                .name(live.getName())
                .planned(money(live.getOverallAmount(), currency))
                .spent(money(spent.getTotal(), currency))
                .remaining(money(remaining, currency))
                .daysLeft(ChronoUnit.DAYS.between(LocalDate.now(), live.getEndDate()))
                .status(live.getStatus().name())
                .build();
    }

    // ------------------------------------------------------------------
    // Arithmetic
    // ------------------------------------------------------------------

    private static AnalyticsResponses.Money money(BigDecimal amount, String currency)
    {
        return AnalyticsResponses.Money.builder()
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .build();
    }

    private static double share(BigDecimal part, BigDecimal total)
    {
        if (total == null || total.signum() == 0)
        {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double percent(BigDecimal now, BigDecimal before)
    {
        if (before == null || before.signum() == 0)
        {
            return null;
        }
        return now.subtract(before).multiply(BigDecimal.valueOf(100))
                .divide(before.abs(), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
