package com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements;

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
import com.expensemanagement.backend.Shared.DateRanges;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
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

    public ExpenseServiceImpl(ExpenseRepository expenseRepository, UserCategoryRepository userCategoryRepository,
                              UserRepository userRepository, FinancialPeriodService periodService)
    {
        this.expenseRepository = expenseRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.userRepository = userRepository;
        this.periodService = periodService;
    }

    @Override
    public Page<ExpenseResponses.Details> findMine(UUID userId, UUID periodId, LocalDate from, LocalDate to,
                                                   UUID categoryId, String q, Pageable pageable)
    {
        DateRanges.Range range = resolveRange(userId, periodId, from, to);
        if (range == null)
        {
            // The period and the explicit dates do not overlap, so nothing can match. An empty
            // page rather than an error: both inputs are individually valid.
            return Page.empty(pageable);
        }

        return expenseRepository.search(userId, range.from(), range.to(), categoryId, likePattern(q), pageable)
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
        FinancialPeriod period = periodService.findMine(userId, periodId);

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
        Expense expense = new Expense();
        // A reference, not a query: the id comes from a verified token, so the user exists.
        expense.setUser(userRepository.getReferenceById(userId));
        ExpenseMappers.apply(expense, request, getOwnCategoryOrThrow(userId, request.getCategoryId()));

        Expense saved = expenseRepository.save(expense);

        log.info("User id={} added expense id={}", userId, saved.getId());
        return ExpenseMappers.toDetails(saved);
    }

    @Override
    @Transactional
    public ExpenseResponses.Details update(UUID userId, UUID id, ExpenseRequests.SaveExpense request)
    {
        Expense expense = getExpenseOrThrow(userId, id);
        ExpenseMappers.apply(expense, request, getOwnCategoryOrThrow(userId, request.getCategoryId()));

        // Flushed now so @PreUpdate has stamped updatedAt before the response is built.
        return ExpenseMappers.toDetails(expenseRepository.saveAndFlush(expense));
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id)
    {
        expenseRepository.delete(getExpenseOrThrow(userId, id));
        log.info("User id={} deleted expense id={}", userId, id);
    }

    /**
     * Not read-only, unlike the other reads: asking for the current period may create it (see
     * {@code FinancialPeriodService}), and a read-only transaction would refuse that insert.
     * Nothing is cached, so an added, edited or deleted expense counts on the very next call.
     */
    @Override
    @Transactional
    public ExpenseResponses.Total totalForPeriod(UUID userId, UUID periodId)
    {
        FinancialPeriod period = periodId == null
                ? periodService.current(userId)
                : periodService.findMine(userId, periodId);

        ExpenseRepository.Totals totals =
                expenseRepository.totalsBetween(userId, period.getStartDate(), period.getEndDate());

        return ExpenseResponses.Total.builder()
                .period(PeriodMappers.toPeriod(period, LocalDate.now()))
                // Already two decimals in the column, so the sum is exact; no rounding needed.
                .total(Money.of(totals.getTotal()))
                .count(totals.getCount())
                .build();
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
}
