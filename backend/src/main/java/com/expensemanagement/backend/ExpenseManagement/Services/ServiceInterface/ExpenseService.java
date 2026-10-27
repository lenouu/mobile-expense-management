package com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface;

import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Expenses of one user. Every method takes the caller's id (from the token), and another user's
 * expense id answers 404, exactly like an id that does not exist.
 */
public interface ExpenseService
{
    /**
     * Expenses between {@code from} and {@code to} (both optional, inclusive).
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    Page<ExpenseResponses.Details> findMine(Long userId, LocalDate from, LocalDate to, Pageable pageable);

    ExpenseResponses.Details findMine(Long userId, Long id);

    /** @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the category is not the caller's */
    ExpenseResponses.Details create(Long userId, ExpenseRequests.SaveExpense request);

    /** @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the category is not the caller's */
    ExpenseResponses.Details update(Long userId, Long id, ExpenseRequests.SaveExpense request);

    void delete(Long userId, Long id);

    /**
     * S8-TECH-1: Calculates the total amount spent by the user between from and to (inclusive).
     * If no expenses exist in the range, returns BigDecimal.ZERO.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    BigDecimal calculateTotalSpending(Long userId, LocalDate from, LocalDate to);

    /**
     * S8-TECH-1: Returns total spending grouped by category for the user between from and to (inclusive),
     * ordered by total spent descending.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    List<ExpenseResponses.CategorySpending> findSpendingByCategory(Long userId, LocalDate from, LocalDate to);

    /**
     * S8-TECH-1: Aggregated spending summary (total spending and per-category spending) for the user
     * between from and to (inclusive).
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    ExpenseResponses.SpendingSummary getSpendingSummary(Long userId, LocalDate from, LocalDate to);
}
