package com.expensemanagement.backend.Authentication.Services.ServiceInterface;

import com.expensemanagement.backend.Authentication.Dtos.DtoResponses.AuthResponses;
import com.expensemanagement.backend.UserManagement.Dtos.DtoRequests.UserRequests;

/**
 * Authentication operations - who you are, as opposed to {@code UserService}'s "what you may
 * do to users".
 *
 * <p>Kept separate from {@code UserService} so that credential checking and token issuance can
 * change (refresh tokens, 2FA, OAuth) without touching user management.
 */
public interface AuthenticationService
{
    /**
     * Verifies credentials and issues a token.
     *
     * <p>Delegates the actual password check to {@code UserService.authenticate} so there is
     * exactly one place that decides whether credentials are valid and whether the account is
     * allowed to sign in.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidCredentialsException if the
     *         identifier or password is wrong
     * @throws com.expensemanagement.backend.Exceptions.OperationNotAllowedException if the
     *         account is not {@code ACTIVE}
     */
    AuthResponses.Token login(UserRequests.Authenticate request);
}
