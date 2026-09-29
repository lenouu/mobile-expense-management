package com.expensemanagement.backend.Exceptions;

/**
 * The request is well-formed but its values do not make sense together, e.g. a date range
 * whose start is after its end. Mapped to HTTP 400.
 */
public class InvalidRequestException extends RuntimeException
{
    public InvalidRequestException(String message)
    {
        super(message);
    }
}
