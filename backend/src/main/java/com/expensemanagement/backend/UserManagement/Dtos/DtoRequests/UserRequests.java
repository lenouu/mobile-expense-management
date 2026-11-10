package com.expensemanagement.backend.UserManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

public class UserRequests {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateAccount {

        @NotBlank(message = "Username is required")
        private String userName;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        // Optional on purpose: the account is created with only credentials, and the profile
        // screen is where the user fills in their name afterwards.
        @Size(max = 50)
        private String firstName;

        @Size(max = 50)
        private String lastName;

        @Past(message = "Date of birth must be in the past")
        private LocalDate dateOfBirth;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAccount {

        @Size(min = 3, max = 30)
        private String userName;

        @Size(max = 50)
        private String firstName;

        @Size(max = 50)
        private String lastName;

        @Past(message = "Date of birth must be in the past")
        private LocalDate dateOfBirth;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Authenticate {

        @NotBlank(message = "Username or email is required")
        private String usernameOrEmail;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePassword {

        @NotBlank(message = "Current password is required")
        private String oldPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;

        @NotBlank(message = "Password confirmation is required")
        private String confirmNewPassword;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAccountStatus {

        @NotNull(message = "Status is required")
        private UserAccountState status;

        private String reason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAccountType {

        @NotNull(message = "Type is required")
        private UserType type;

        private String reason;
    }

    /**
     * Body of `POST /users/me/photo`.
     *
     * <p>The picture travels base64-encoded in JSON rather than as a multipart part. A React Native
     * multipart upload failed before the request reached the server on a real device, while plain
     * JSON POSTs from the same app work - so the upload uses the transport that is known to work.
     * The server decodes it, re-checks the real size and re-encodes the image.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadPicture {

        /** The image file, base64-encoded (no data-URI prefix). */
        @NotBlank(message = "Image data is required")
        private String image;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteAccount {

        @NotBlank(message = "Password confirmation is required")
        private String password;

        private String reason;
    }
}
