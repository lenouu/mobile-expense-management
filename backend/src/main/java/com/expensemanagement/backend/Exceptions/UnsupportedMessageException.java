package com.expensemanagement.backend.Exceptions;

/**
 * A pasted message could not be turned into a transaction: it is not from a supported mobile money
 * service, it records money received, or it has no readable amount (S6-TECH-2).
 *
 * <p>Mapped to HTTP 422 with the code {@code unsupported_message}, so the app can tell this apart
 * from a validation error and show the message as it is.
 */
public class UnsupportedMessageException extends RuntimeException
{
    public UnsupportedMessageException(String message)
    {
        super(message);
    }
}
