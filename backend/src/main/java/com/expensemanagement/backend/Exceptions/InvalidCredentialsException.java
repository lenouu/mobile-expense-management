package com.expensemanagement.backend.Exceptions;

/**
 * Credentials were rejected. Mapped to HTTP 401.
 *
 * <p>Deliberately carries no detail about <em>which</em> part was wrong - callers should use
 * one message for both "no such user" and "wrong password" so the response cannot be used to
 * enumerate accounts.
 */
public class InvalidCredentialsException extends RuntimeException
{
    public InvalidCredentialsException(String message)
    {
        super(message);
    }
}
