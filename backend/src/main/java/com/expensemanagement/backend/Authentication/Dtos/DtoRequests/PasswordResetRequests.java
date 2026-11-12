package com.expensemanagement.backend.Authentication.Dtos.DtoRequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class PasswordResetRequests {

    /**
     * Step one. The identifier is a username or an email, exactly as on the login form.
     *
     * <p>The response is always the same whether or not the account exists, so this endpoint
     * cannot be used to discover which addresses are registered.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPassword {

        @NotBlank(message = "Username or email is required")
        private String identifier;
    }

    /** Step two: the raw token from the emailed link, plus the new password. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPassword {

        @NotBlank(message = "Reset token is required")
        private String token;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;
    }
}
