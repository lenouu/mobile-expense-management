package com.expensemanagement.backend.Exceptions;

/**
 * A requested resource does not exist. Mapped to HTTP 404.
 *
 * <p>Typed rather than reusing {@code NoSuchElementException} so the handler cannot
 * accidentally swallow an unrelated JDK exception and turn a bug into a 404.
 */
public class ResourceNotFoundException extends RuntimeException
{
    public ResourceNotFoundException(String message)
    {
        super(message);
    }
}
