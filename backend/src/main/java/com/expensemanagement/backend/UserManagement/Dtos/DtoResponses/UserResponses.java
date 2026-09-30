package com.expensemanagement.backend.UserManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserResponses {

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private final Long id;
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
        private final Long id;
        private final String userName;
        private final String email;
        private final String firstName;
        private final String lastName;
        private final LocalDate dateOfBirth;
        private final String profilePictureReference;
        private final UserAccountState status;
        private final UserType type;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }
}
