package com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface;

import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

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
}
