package com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BudgetRequests
{
    /**
     * Body of {@code POST /budgets}: the whole plan in one call.
     *
     * <p>The window, the optional name, the amount and the optional split arrive together because
     * they are one decision - and because filling in a new budget is not an edit, so it must not
     * spend the three-edit cap before the plan is complete.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBudget
    {
        /** Optional. An unnamed budget is shown by its dates. */
        @Size(max = 60, message = "Name must be at most 60 characters")
        private String name;

        @NotNull(message = "A start date is required")
        private LocalDate startDate;

        @NotNull(message = "An end date is required")
        private LocalDate endDate;

        @NotNull(message = "Overall amount is required")
        @DecimalMin(value = "0.01", message = "Overall amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal overallAmount;

        /**
         * The plan's categories, optional. When present they must add up to exactly
         * {@code overallAmount} - a split that does not complete the total is refused.
         */
        @Valid
        private List<AllocationItem> allocations;
    }

    /**
     * Body of {@code PUT /budgets/{id}}: the plan, replaced.
     *
     * <p>A full replace rather than a patch, for the same reason the split must balance: the parts
     * are only meaningful together.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateBudget
    {
        @Size(max = 60, message = "Name must be at most 60 characters")
        private String name;

        private LocalDate startDate;
        private LocalDate endDate;

        @NotNull(message = "Overall amount is required")
        @DecimalMin(value = "0.01", message = "Overall amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal overallAmount;

        @Valid
        private List<AllocationItem> allocations;
    }

    /** One category and what the user intends to spend on it. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationItem
    {
        @NotNull(message = "Category ID is required")
        private UUID categoryId;

        @NotNull(message = "Allocated amount is required")
        @DecimalMin(value = "0.01", message = "Allocated amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        private BigDecimal allocatedAmount;
    }
}
