package com.expensemanagement.backend.Exceptions;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body for every failed request.
 *
 * <p>{@code fieldErrors} is empty for most failures and populated only for bean-validation
 * errors, where it maps field name to message so the client can highlight the right input.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
)
{
    public static ApiError of(int status, String error, String message)
    {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }

    public static ApiError of(int status, String error, String message, Map<String, String> fieldErrors)
    {
        return new ApiError(Instant.now(), status, error, message, fieldErrors);
    }
}
