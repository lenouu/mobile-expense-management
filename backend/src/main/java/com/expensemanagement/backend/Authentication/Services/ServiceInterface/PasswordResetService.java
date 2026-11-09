package com.expensemanagement.backend.Authentication.Services.ServiceInterface;

/**
 * Password recovery (US06), reachable without a token because a user who forgot their password
 * cannot obtain one.
 */
public interface PasswordResetService
{
    /**
     * Issues a reset token and emails a link, if an account matches {@code identifier}.
     *
     * <p>Returns normally in every case - the caller answers 202 regardless - and does nothing at
     * all when the identifier is unknown. That is what stops the endpoint being an account
     * directory.
     */
    void requestReset(String identifier);

    /**
     * Sets a new password using the token from the emailed link.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if the token is
     *         unknown, already used, or expired
     */
    void reset(String token, String newPassword);
}
