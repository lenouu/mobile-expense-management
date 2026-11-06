package com.expensemanagement.backend.UserManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class UserResponses {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private final UUID id;
        private final String userName;
        private final String firstName;
        private final String lastName;
        private final String profilePictureReference;
        private final UserAccountState status;
        private final UserType type;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details {
        private final UUID id;
        private final String userName;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final LocalDate dateOfBirth;
        private final String profilePictureReference;
        /**
         * Where the avatar bytes can be fetched, relative to the API base (`/api`), e.g.
         * {@code /avatars/8f3c….jpg}. Null when the account has no picture.
         *
         * <p>The file name is random and changes on every upload, which is what makes the URL a
         * reliable cache key: a new picture is a new URL, so no client ever shows a stale one.
         */
        private final String profilePictureUrl;
        private final UserAccountState status;
        private final UserType type;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }
}
