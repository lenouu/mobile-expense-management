package com.expensemanagement.backend.Exceptions;

/**
 * The request is understood and authenticated, but the operation is not permitted in the
 * current state. Mapped to HTTP 409.
 *
 * <p>Examples: signing in to a suspended account, changing a password whose confirmation does
 * not match, demoting the last remaining admin.
 */
public class OperationNotAllowedException extends RuntimeException
{
    public OperationNotAllowedException(String message)
    {
        super(message);
    }
}
