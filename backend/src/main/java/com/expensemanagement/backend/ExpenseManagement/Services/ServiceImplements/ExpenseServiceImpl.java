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
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;

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
    public Page<ExpenseResponses.Details> findMine(Long userId, LocalDate from, LocalDate to, Pageable pageable)
    {
        DateRanges.Range range = DateRanges.of(from, to);
        return expenseRepository.findAllByUserIdAndDateBetween(userId, range.from(), range.to(), pageable)
                .map(ExpenseMappers::toDetails);
    }

    @Override
    public ExpenseResponses.Details findMine(Long userId, Long id)
    {
        return ExpenseMappers.toDetails(getExpenseOrThrow(userId, id));
    }

    @Override
    @Transactional
    public ExpenseResponses.Details create(Long userId, ExpenseRequests.SaveExpense request)
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
    public ExpenseResponses.Details update(Long userId, Long id, ExpenseRequests.SaveExpense request)
    {
        Expense expense = getExpenseOrThrow(userId, id);
        ExpenseMappers.apply(expense, request, getOwnCategoryOrThrow(userId, request.getCategoryId()));

        // Flushed now so @PreUpdate has stamped updatedAt before the response is built.
        return ExpenseMappers.toDetails(expenseRepository.saveAndFlush(expense));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id)
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
    public ExpenseResponses.Total totalForPeriod(Long userId, Long periodId)
    {
        FinancialPeriod period = periodId == null
                ? periodService.current(userId)
                : periodService.findMine(userId, periodId);

        ExpenseRepository.Totals totals =
                expenseRepository.totalsBetween(userId, period.getStartDate(), period.getEndDate());

        return ExpenseResponses.Total.builder()
                .period(PeriodMappers.toPeriod(period, LocalDate.now()))
                // Always two decimals, so 0 reads "0.00" like any other amount.
                .total(totals.getTotal().setScale(2, RoundingMode.UNNECESSARY))
                .count(totals.getCount())
                .build();
    }

    private Expense getExpenseOrThrow(Long userId, Long id)
    {
        return expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No expense with id " + id));
    }

    /**
     * 400 rather than 404: the URL is fine, it is a value in the body that is wrong. Someone
     * else's category gets the same message as a missing one, so ids cannot be probed.
     */
    private UserCategory getOwnCategoryOrThrow(Long userId, Long categoryId)
    {
        return userCategoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new InvalidRequestException("Category " + categoryId + " is not one of your categories"));
    }
}
