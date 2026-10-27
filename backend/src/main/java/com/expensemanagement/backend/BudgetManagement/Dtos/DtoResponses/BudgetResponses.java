package com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BudgetResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetDetails
    {
        private final Long id;
        private final Long periodId;
        private final BigDecimal overallAmount;
        private final BigDecimal totalAllocated;
        private final BigDecimal remainingUnallocated;
        private final List<AllocationDetails> allocations;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AllocationDetails
    {
        private final Long id;
        private final Long budgetId;
        private final CategoryRef category;
        private final BigDecimal allocatedAmount;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategoryRef
    {
        private final Long id;
        private final String name;
        private final String icon;
        private final String color;
    }
}
