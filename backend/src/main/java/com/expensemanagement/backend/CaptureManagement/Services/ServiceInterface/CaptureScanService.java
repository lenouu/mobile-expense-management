package com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests.CaptureScanRequests;
import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureScanResponses;

import java.util.UUID;

/**
 * Reads a pasted message and proposes the money movements in it.
 *
 * <p>Read-only: it creates nothing and stores nothing. The app reviews the rows - including
 * flipping an in for an out - and creates the records through the ordinary income and expense
 * endpoints.
 */
public interface CaptureScanService
{
    /**
     * @param userId  the caller, taken from the token - never from the request
     * @param request the pasted text
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException when the text is blank
     * @throws com.expensemanagement.backend.Exceptions.AiUnavailableException when the AI service
     *         is not configured, refuses the call, or cannot be reached
     */
    CaptureScanResponses.Scan scan(UUID userId, CaptureScanRequests.Scan request);
}
