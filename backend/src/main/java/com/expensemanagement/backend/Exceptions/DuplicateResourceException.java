package com.expensemanagement.backend.Exceptions;

/**
 * A unique value is already taken (username, email). Mapped to HTTP 409.
 */
public class DuplicateResourceException extends RuntimeException
{
    public DuplicateResourceException(String message)
    {
        super(message);
    }
}
