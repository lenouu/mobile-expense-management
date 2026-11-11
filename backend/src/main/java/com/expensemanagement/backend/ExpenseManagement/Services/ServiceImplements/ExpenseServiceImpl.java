package com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Mappers.ExpenseMappers;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Mappers.PeriodMappers;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.CurrencyPolicy;
import com.expensemanagement.backend.Shared.DateRanges;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Expenses (S2-TECH-2).
 *
 * <p>Every query is scoped by the owner's id, which the controller takes from the token. That is
 * the whole privacy model: there is no code path that loads an expense by id alone.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
public class ExpenseServiceImpl implements ExpenseService
{
    private final ExpenseRepository expenseRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final UserRepository userRepository;
    private final FinancialPeriodService periodService;
    private final BudgetRepository budgetRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository, UserCategoryRepository userCategoryRepository,
                              UserRepository userRepository, FinancialPeriodService periodService,
                              BudgetRepository budgetRepository)
    {
        this.expenseRepository = expenseRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.userRepository = userRepository;
        this.periodService = periodService;
        this.budgetRepository = budgetRepository;
    }

    @Override
    public Page<ExpenseResponses.Details> findMine(UUID userId, UUID periodId, LocalDate from, LocalDate to,
                                                   UUID categoryId, String q, UUID budgetId, Pageable pageable)
    {
        DateRanges.Range range = resolveRange(userId, periodId, from, to);
        if (range == null)
        {
            // The period and the explicit dates do not overlap, so nothing can match. An empty
            // page rather than an error: both inputs are individually valid.
            return Page.empty(pageable);
        }

        return expenseRepository
                .search(userId, range.from(), range.to(), categoryId, likePattern(q), budgetId, pageable)
                .map(ExpenseMappers::toDetails);
    }

    /**
     * Works out the date window a listing should cover.
     *
     * <p>Without a period it is just {@code from..to} with wide defaults. With a period, the
     * period supplies the bounds and any explicit dates narrow them - so {@code periodId} and
     * {@code from}/{@code to} compose instead of one silently overriding the other.
     *
     * <p>{@link DateRanges#of} is always called first so an inverted {@code from}/{@code to} is
     * still a 400, whether or not a period was given.
     *
     * @return null when the two ranges do not overlap, which the caller turns into an empty page
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period is
     *         not the caller's
     */
    private DateRanges.Range resolveRange(UUID userId, UUID periodId, LocalDate from, LocalDate to)
    {
        DateRanges.Range requested = DateRanges.of(from, to);
        if (periodId == null)
        {
            return requested;
        }

        // Answers 404 for another user's period, exactly like a period that does not exist.
        return narrowToPeriod(requested, periodService.findMine(userId, periodId));
    }

    /**
     * Intersects a requested window with a period's dates.
     *
     * @return null when they do not overlap, which the caller turns into an empty result
     */
    private static DateRanges.Range narrowToPeriod(DateRanges.Range requested, FinancialPeriod period)
    {
        LocalDate start = requested.from().isAfter(period.getStartDate())
                ? requested.from()
                : period.getStartDate();
        LocalDate end = requested.to().isBefore(period.getEndDate())
                ? requested.to()
                : period.getEndDate();

        return start.isAfter(end) ? null : new DateRanges.Range(start, end);
    }

    /**
     * Lower-cased {@code %term%}, or null when there is nothing to search for.
     *
     * <p>A null pattern is what the query treats as "no text filter", which is why an empty or
     * blank {@code q} must not become {@code %%} - that would be a filter matching every row that
     * has a description, and none of the rows that do not.
     */
    private static String likePattern(String q)
    {
        return StringUtils.hasText(q) ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%" : null;
    }

    @Override
    public ExpenseResponses.Details findMine(UUID userId, UUID id)
    {
        return ExpenseMappers.toDetails(getExpenseOrThrow(userId, id));
    }

    @Override
    @Transactional
    public ExpenseResponses.Details create(UUID userId, ExpenseRequests.SaveExpense request)
    {
        User user = getUserOrThrow(userId);

        Expense expense = new Expense();
        expense.setUser(user);
        ExpenseMappers.apply(expense, request, getOwnCategoryOrThrow(userId, request.getCategoryId()));
        expense.setBudget(resolveBudget(userId, request));
        // The account's unit wins: a request in another currency is refused rather than stored.
        expense.setCurrency(CurrencyPolicy.resolve(request.getAmount(), user.getBaseCurrency()));

        Expense saved = expenseRepository.save(expense);

        log.info("User id={} added expense id={}", userId, saved.getId());
        return ExpenseMappers.toDetails(saved);
    }

    @Override
    @Transactional
    public ExpenseResponses.Details update(UUID userId, UUID id, ExpenseRequests.SaveExpense request)
    {
        User user = getUserOrThrow(userId);

        Expense expense = getExpenseOrThrow(userId, id);
        ExpenseMappers.apply(expense, request, getOwnCategoryOrThrow(userId, request.getCategoryId()));
        expense.setBudget(resolveBudget(userId, request));
        expense.setCurrency(CurrencyPolicy.resolve(request.getAmount(), user.getBaseCurrency()));

        // Flushed now so @PreUpdate has stamped updatedAt before the response is built.
        return ExpenseMappers.toDetails(expenseRepository.saveAndFlush(expense));
    }

    @Override
    public boolean hasAny(UUID userId)
    {
        return expenseRepository.existsByUserId(userId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id)
    {
        expenseRepository.delete(getExpenseOrThrow(userId, id));
        log.info("User id={} deleted expense id={}", userId, id);
    }

    /**
     * Not read-only, unlike the other reads: the window may resolve to the current period, which
     * can have to be created first, and a read-only transaction would refuse that insert.
     * Nothing is cached, so an added, edited or deleted expense counts on the very next call.
     */
    @Override
    @Transactional
    public ExpenseResponses.Total totals(UUID userId, UUID periodId, LocalDate from, LocalDate to,
                                         UUID categoryId, String q)
    {
        // DateRanges.of runs first, so an inverted from/to is still a 400 even with a period.
        DateRanges.Range requested = DateRanges.of(from, to);

        // The sum is in the account's own unit; without it the label would be a guess.
        String baseCurrency = getUserOrThrow(userId).getBaseCurrency();

        FinancialPeriod period = periodId == null
                ? (from == null && to == null ? periodService.current(userId) : null)
                : periodService.findMine(userId, periodId);

        // With neither a period nor dates given, the current period is the window.
        DateRanges.Range range = period == null
                ? requested
                : narrowToPeriod(requested, period);

        if (range == null)
        {
            // The period and the explicit dates do not overlap: a real zero, not an error.
            return ExpenseResponses.Total.builder()
                    .from(requested.from())
                    .to(requested.to())
                    .period(null)
                    .total(Money.of(BigDecimal.ZERO, baseCurrency))
                    .count(0)
                    .build();
        }

        ExpenseRepository.Totals totals = expenseRepository.totalsMatching(
                userId, range.from(), range.to(), categoryId, likePattern(q));

        return ExpenseResponses.Total.builder()
                .from(range.from())
                .to(range.to())
                .period(period == null ? null : PeriodMappers.toPeriod(period, LocalDate.now()))
                // Already two decimals in the column, so the sum is exact; no rounding needed.
                .total(Money.of(totals.getTotal(), baseCurrency))
                .count(totals.getCount())
                .build();
    }

    /**
     * The budget an expense is counted against, or null for a normal expense.
     *
     * <p>Three conditions, and each is the reason the link means something: the budget is the
     * caller's, it has not closed, and the expense's date falls inside its window - an expense from
     * last month cannot join this week's plan.
     */
    @Override
    @Transactional
    public ExpenseResponses.Overview overview(UUID userId, LocalDate from, LocalDate to)
    {
        DateRanges.Range window = DateRanges.of(from, to);
        String baseCurrency = getUserOrThrow(userId).getBaseCurrency();

        // The same length immediately before: a 29-day window compares with the 29 days before it,
        // which is honest for a custom range as well as for a period.
        long length = ChronoUnit.DAYS.between(window.from(), window.to()) + 1;
        LocalDate previousTo = window.from().minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(length - 1);

        ExpenseRepository.Totals current = expenseRepository
                .totalsMatching(userId, window.from(), window.to(), null, null);
        ExpenseRepository.Totals previous = expenseRepository
                .totalsMatching(userId, previousFrom, previousTo, null, null);

        BigDecimal change = current.getTotal().subtract(previous.getTotal());

        Map<UUID, BigDecimal> previousByCategory = new HashMap<>();
        for (Object[] row : expenseRepository.sumByCategory(userId, previousFrom, previousTo))
        {
            previousByCategory.put((UUID) row[0], (BigDecimal) row[1]);
        }

        BigDecimal grandTotal = current.getTotal();
        List<ExpenseResponses.CategorySlice> byCategory = new ArrayList<>();

        for (Object[] row : expenseRepository.sumByCategory(userId, window.from(), window.to()))
        {
            UUID categoryId = (UUID) row[0];
            BigDecimal total = (BigDecimal) row[1];
            BigDecimal before = previousByCategory.getOrDefault(categoryId, BigDecimal.ZERO);

            byCategory.add(ExpenseResponses.CategorySlice.builder()
                    .category(categorySlice(userId, categoryId))
                    .total(Money.of(total, baseCurrency))
                    .count(0)
                    .previousTotal(Money.of(before, baseCurrency))
                    .change(Money.of(total.subtract(before), baseCurrency))
                    .changePercent(percentOf(total.subtract(before), before))
                    .share(grandTotal.signum() == 0 ? 0
                            : total.multiply(BigDecimal.valueOf(100))
                                    .divide(grandTotal, 1, RoundingMode.HALF_UP)
                                    .doubleValue())
                    .build());
        }

        byCategory.sort((a, b) -> b.getTotal().amount().compareTo(a.getTotal().amount()));

        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (Object[] row : expenseRepository.sumByDate(userId, window.from(), window.to()))
        {
            byDate.put((LocalDate) row[0], (BigDecimal) row[1]);
        }

        List<ExpenseResponses.DaySlice> daily = new ArrayList<>();
        for (LocalDate date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1))
        {
            daily.add(ExpenseResponses.DaySlice.builder()
                    .date(date)
                    .total(Money.of(byDate.getOrDefault(date, BigDecimal.ZERO), baseCurrency))
                    .build());
        }

        return ExpenseResponses.Overview.builder()
                .from(window.from())
                .to(window.to())
                .total(Money.of(current.getTotal(), baseCurrency))
                .count(current.getCount())
                .previousFrom(previousFrom)
                .previousTo(previousTo)
                .previousTotal(Money.of(previous.getTotal(), baseCurrency))
                .previousCount(previous.getCount())
                .change(Money.of(change, baseCurrency))
                .changePercent(percentOf(change, previous.getTotal()))
                .byCategory(byCategory)
                .daily(daily)
                .build();
    }

    /** Percentage change against a baseline, or null when the baseline is zero. */
    private static Double percentOf(BigDecimal change, BigDecimal baseline)
    {
        if (baseline == null || baseline.signum() == 0)
        {
            return null;
        }
        return change.multiply(BigDecimal.valueOf(100))
                .divide(baseline, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** The category behind a slice, looked up once per row - the list is tiny. */
    private ExpenseResponses.CategoryRef categorySlice(UUID userId, UUID categoryId)
    {
        return userCategoryRepository.findByIdAndUserId(categoryId, userId)
                .map(category -> ExpenseResponses.CategoryRef.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .icon(category.getIcon())
                        .color(category.getColor())
                        .build())
                .orElse(null);
    }
    private Budget resolveBudget(UUID userId, ExpenseRequests.SaveExpense request)
    {
        if (request.getBudgetId() == null)
        {
            return null;
        }

        Budget budget = budgetRepository.findByIdAndUserId(request.getBudgetId(), userId)
                .orElseThrow(() -> new InvalidRequestException(
                        "Budget " + request.getBudgetId() + " is not one of your budgets"));

        LocalDate date = request.getDate();

        if (date.isBefore(budget.getStartDate()) || date.isAfter(budget.getEndDate()))
        {
            throw new InvalidRequestException("This expense is dated " + date
                    + ", outside the budget's window (" + budget.getStartDate() + " to "
                    + budget.getEndDate() + ").");
        }

        if (budget.getStatus() == BudgetStatus.CLOSED)
        {
            throw new InvalidRequestException("That budget has closed, so it cannot take more expenses.");
        }

        return budget;
    }
    private Expense getExpenseOrThrow(UUID userId, UUID id)
    {
        return expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No expense with id " + id));
    }

    /**
     * 400 rather than 404: the URL is fine, it is a value in the body that is wrong. Someone
     * else's category gets the same message as a missing one, so ids cannot be probed.
     */
    private UserCategory getOwnCategoryOrThrow(UUID userId, UUID categoryId)
    {
        return userCategoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new InvalidRequestException("Category " + categoryId + " is not one of your categories"));
    }

    /** The caller, whose base currency labels every amount this service writes. */
    private User getUserOrThrow(UUID userId)
    {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No user with id " + userId));
    }
}
