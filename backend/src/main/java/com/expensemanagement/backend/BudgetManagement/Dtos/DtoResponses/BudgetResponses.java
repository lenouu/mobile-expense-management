package com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BudgetResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetDetails
    {
        private final UUID id;
        private final UUID periodId;
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
        private final UUID id;
        private final UUID budgetId;
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
        private final UUID id;
        private final String name;
        private final String icon;
        private final String color;
    }
}
