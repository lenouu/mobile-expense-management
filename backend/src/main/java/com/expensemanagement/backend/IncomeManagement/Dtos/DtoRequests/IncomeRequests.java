package com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.Shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

public class IncomeRequests {

    /** Used by both POST and PUT. PUT is a full replace, so an omitted description is cleared. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveIncome {

        /** {@code {"amount": 250000.00, "currency": "XAF"}} - never a bare number. */
        @Valid
        @NotNull(message = "Amount is required")
        private Money amount;

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Date cannot be in the future")
        private LocalDate date;

        @NotBlank(message = "Source is required")
        @Size(max = 100, message = "Source must be at most 100 characters")
        private String source;

        @Size(max = 255, message = "Description must be at most 255 characters")
        private String description;
    }
}
