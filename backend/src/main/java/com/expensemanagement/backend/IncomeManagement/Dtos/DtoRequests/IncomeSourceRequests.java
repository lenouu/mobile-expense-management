package com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Creating and renaming income sources. */
public class IncomeSourceRequests
{
    /** Used by both POST and PUT; PUT is a full replace. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveIncomeSource
    {
        @NotBlank(message = "Name is required")
        @Size(max = 60, message = "Name must be at most 60 characters")
        private String name;

        /** An icon name the app knows; blank means "no icon". */
        @Size(max = 40, message = "Icon name must be at most 40 characters")
        private String icon;

        @Size(max = 9, message = "Colour must be a hex value such as #01916D")
        private String color;

        @Size(max = 200, message = "Description must be at most 200 characters")
        private String description;
    }

    /** Re-pointing incomes at another source before deleting the one they use. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoveIncomes
    {
        private UUID toSourceId;
    }
}
