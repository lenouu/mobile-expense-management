package com.expensemanagement.backend.Exceptions;

/**
 * The request is understood and authenticated, but the operation is not permitted in the
 * current state. Mapped to HTTP 422 (a business-rule violation, not a data conflict).
 *
 * <p>Examples: signing in to a suspended account, changing a password whose confirmation does
 * not match, demoting the last remaining admin, deleting a category that expenses still use.
 *
 * <p>Distinct from {@link DuplicateResourceException}, which is a genuine conflict with existing
 * data and stays a 409.
 */
public class OperationNotAllowedException extends RuntimeException
{
    public OperationNotAllowedException(String message)
    {
        super(message);
    }
}
