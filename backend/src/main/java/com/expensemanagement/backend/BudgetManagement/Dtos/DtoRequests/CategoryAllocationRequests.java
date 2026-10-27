package com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

public class CategoryAllocationRequests
{
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveAllocation
    {
        @NotNull(message = "Category ID is required")
        private Long categoryId;

        @NotNull(message = "Allocated amount is required")
        @DecimalMin(value = "0.01", message = "Allocated amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal allocatedAmount;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAllocation
    {
        @NotNull(message = "Allocated amount is required")
        @DecimalMin(value = "0.01", message = "Allocated amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal allocatedAmount;
    }
}
