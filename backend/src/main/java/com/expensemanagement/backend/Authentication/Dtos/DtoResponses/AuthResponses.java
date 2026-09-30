package com.expensemanagement.backend.Authentication.Dtos.DtoResponses;

import com.expensemanagement.backend.UserManagement.Dtos.DtoResponses.UserResponses;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Outgoing payloads for the authentication API.
 *
 * <p>Same shape as {@code UserResponses}: a holder class with nested {@code public static}
 * payload classes, built with Lombok builders.
 */
public class AuthResponses
{

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Token
    {
        /** The signed JWT. */
        private final String accessToken;

        /** Always {@code "Bearer"} - supplied so clients do not have to hardcode it. */
        private final String tokenType;

        /** Lifetime in seconds, so the client can refresh proactively. */
        private final long expiresIn;

        /** The authenticated user's profile, saving the client an immediate round-trip. */
        private final UserResponses.Details user;
    }
}
