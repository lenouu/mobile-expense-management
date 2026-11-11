package com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface;

import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Expenses of one user. Every method takes the caller's id (from the token), and another user's
 * expense id answers 404, exactly like an id that does not exist.
 */
public interface ExpenseService
{
    /**
     * The caller's expenses, newest first, with optional additive filters.
     *
     * <p>{@code periodId} restricts to one of the caller's financial periods (US21): the period's
     * start and end dates become the date bounds, and any explicit {@code from} / {@code to}
     * narrow that range further rather than replacing it. When the intersection is empty the
     * result is an empty page, not an error.
     *
     * <p>{@code categoryId} narrows to one of the caller's categories, and {@code q} is a
     * case-insensitive substring of the description.
     *
     * <p>Neither {@code categoryId} nor {@code periodId} is checked for ownership: because the
     * query is already scoped to the caller, someone else's id simply matches nothing. A period
     * id is different - it is resolved up front, so another user's period answers 404.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period does
     *         not exist or belongs to someone else
     */
    Page<ExpenseResponses.Details> findMine(UUID userId, UUID periodId, LocalDate from, LocalDate to,
                                            UUID categoryId, String q, UUID budgetId, Pageable pageable);

    ExpenseResponses.Details findMine(UUID userId, UUID id);

    /** @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the category is not the caller's */
    ExpenseResponses.Details create(UUID userId, ExpenseRequests.SaveExpense request);

    /** @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the category is not the caller's */
    ExpenseResponses.Details update(UUID userId, UUID id, ExpenseRequests.SaveExpense request);

    void delete(UUID userId, UUID id);

    /**
     * Whether the account has recorded anything at all.
     *
     * <p>Used before changing the account's base currency: amounts already stored are in the old
     * unit, and there is no rate to convert them with.
     */
    boolean hasAny(UUID userId);

    /**
     * Total of the caller's expenses over a window (US39).
     *
     * <p>The window is resolved exactly like the listing: {@code periodId} supplies the bounds and
     * {@code from}/{@code to} narrow them, and with neither it is the current period. The same
     * {@code categoryId} and {@code q} filters apply, so the total always matches the rows the
     * list would return for the same query.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the period
     *         does not exist or belongs to someone else
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if {@code from} is
     *         after {@code to}
     */
    ExpenseResponses.Total totals(UUID userId, UUID periodId, LocalDate from, LocalDate to,
                                  UUID categoryId, String q);
}
