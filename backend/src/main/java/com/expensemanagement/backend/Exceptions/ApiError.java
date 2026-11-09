package com.expensemanagement.backend.Exceptions;

import java.util.Map;

/**
 * Uniform error body for every failed request: {@code {code, message, fields}}.
 *
 * <p>{@code code} is a stable machine-readable token a client switches on, independent of the
 * human-readable {@code message}, which may be reworded. {@code fields} is empty for most
 * failures and populated only for bean-validation errors, where it maps a field path to the
 * reason it failed so the client can highlight the right input.
 *
 * <p>The HTTP status is carried by the response itself, not repeated in the body: two sources of
 * truth for the same fact drift apart as soon as one of them is edited.
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fields
)
{
    public static ApiError of(String code, String message)
    {
        return new ApiError(code, message, Map.of());
    }

    public static ApiError of(String code, String message, Map<String, String> fields)
    {
        return new ApiError(code, message, fields);
    }
}
