package com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests.CaptureRequests;
import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Transactions detected from a mobile money SMS and their review (S6-TECH-2).
 *
 * <p>Every method takes the caller's id from the token; another user's capture answers 404.
 */
public interface CaptureService
{
    /**
     * Reads a pasted SMS and keeps the transaction it describes as PENDING. No expense is created.
     *
     * @throws com.expensemanagement.backend.Exceptions.UnsupportedMessageException if the text is
     *         not a supported outgoing mobile money transaction
     * @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the same
     *         transaction (same provider and reference) was already captured
     */
    CaptureResponses.Details captureSms(UUID userId, CaptureRequests.SmsCapture request);

    /** Newest first; {@code status} null means every status. */
    Page<CaptureResponses.Details> findMine(UUID userId, CaptureStatus status, Pageable pageable);

    CaptureResponses.Details findMine(UUID userId, UUID id);

    /**
     * Creates the expense from the values the user reviewed and marks the capture CONFIRMED,
     * both or neither.
     *
     * @throws com.expensemanagement.backend.Exceptions.OperationNotAllowedException if the capture
     *         was already confirmed or dismissed
     */
    CaptureResponses.Details confirm(UUID userId, UUID id, ExpenseRequests.SaveExpense expense);

    /**
     * Discards a pending capture; no expense is created.
     *
     * @throws com.expensemanagement.backend.Exceptions.OperationNotAllowedException if the capture
     *         was already confirmed or dismissed
     */
    CaptureResponses.Details dismiss(UUID userId, UUID id);
}
