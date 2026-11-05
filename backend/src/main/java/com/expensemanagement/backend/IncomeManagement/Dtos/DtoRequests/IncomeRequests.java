package com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IncomeRequests {

    /** Used by both POST and PUT. PUT is a full replace, so an omitted description is cleared. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveIncome {

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal amount;

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
