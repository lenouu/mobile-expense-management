package com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

public class CategoryRequests {

    /** Used by both POST and PUT. PUT is a full replace, so omitted optional fields are cleared. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveCategory {

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must be at most 50 characters")
        private String name;

        @Size(max = 255, message = "Description must be at most 255 characters")
        private String description;

        @Size(max = 50, message = "Icon must be at most 50 characters")
        private String icon;

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a hex value like #1F9D55")
        private String color;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryStatus {

        @NotNull(message = "Active flag is required")
        private Boolean active;
    }
}
