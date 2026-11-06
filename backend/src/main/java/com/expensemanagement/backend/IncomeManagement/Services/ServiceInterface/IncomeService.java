package com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Incomes of one user. Every method takes the caller's id (from the token), and another user's
 * income id answers 404, exactly like an id that does not exist.
 */
public interface IncomeService
{
    /**
     * Incomes between {@code from} and {@code to} (both optional, inclusive).
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    Page<IncomeResponses.Details> findMine(UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    IncomeResponses.Details findMine(UUID userId, UUID id);

    IncomeResponses.Details create(UUID userId, IncomeRequests.SaveIncome request);

    IncomeResponses.Details update(UUID userId, UUID id, IncomeRequests.SaveIncome request);

    void delete(UUID userId, UUID id);
}
