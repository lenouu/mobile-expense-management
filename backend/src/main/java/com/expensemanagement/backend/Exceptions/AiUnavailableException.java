package com.expensemanagement.backend.Exceptions;

/**
 * The AI reader could not answer: no key configured, the service refused the call, or it could
 * not be reached. Mapped to HTTP 503.
 *
 * <p>Distinct from {@link InvalidRequestException} because the request itself was fine - retrying
 * later is exactly the right thing for the client to do, and the message says which case it was.
 */
public class AiUnavailableException extends RuntimeException
{
    public AiUnavailableException(String message)
    {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
