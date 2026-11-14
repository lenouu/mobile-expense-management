package com.expensemanagement.backend.ReceiptManagement.Services.ServiceInterface;

import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoRequests.ReceiptScanRequests;
import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoResponses.ReceiptScanResponses;

import java.util.UUID;

/**
 * Reads expenses out of a picture, using the caller's own categories as the vocabulary.
 *
 * <p>Read-only by design: it creates nothing and stores nothing. The returned rows are a
 * proposal for the app to show, and saving them stays with the ordinary expense endpoint.
 */
public interface ReceiptScanService
{
    /**
     * @param userId  the caller, taken from the token - never from the request
     * @param request the picture, base64-encoded, with an optional MIME type
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException when the picture
     *         cannot be decoded, is empty, is too large, or is not a supported image format
     * @throws com.expensemanagement.backend.Exceptions.AiUnavailableException when the AI service
     *         is not configured, refuses the call, or cannot be reached
     */
    ReceiptScanResponses.Scan scan(UUID userId, ReceiptScanRequests.Scan request);
}
