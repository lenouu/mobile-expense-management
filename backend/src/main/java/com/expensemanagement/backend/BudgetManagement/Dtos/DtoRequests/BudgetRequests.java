package com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

public class BudgetRequests
{
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBudget
    {
        @NotNull(message = "Period ID is required")
        private Long periodId;

        @NotNull(message = "Overall amount is required")
        @DecimalMin(value = "0.01", message = "Overall amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal overallAmount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBudget
    {
        @NotNull(message = "Overall amount is required")
        @DecimalMin(value = "0.01", message = "Overall amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal overallAmount;
    }
}
