package com.expensemanagement.backend.BudgetManagement.Mappers;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public final class BudgetMappers
{
    private BudgetMappers() {}

    public static BudgetResponses.BudgetDetails toBudgetDetails(Budget budget, List<CategoryAllocation> allocations)
    {
        List<CategoryAllocation> safeAllocations = allocations != null ? allocations : Collections.emptyList();

        BigDecimal totalAllocated = safeAllocations.stream()
                .map(CategoryAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remainingUnallocated = budget.getOverallAmount().subtract(totalAllocated);

        List<BudgetResponses.AllocationDetails> allocationDtos = safeAllocations.stream()
                .map(BudgetMappers::toAllocationDetails)
                .toList();

        return BudgetResponses.BudgetDetails.builder()
                .id(budget.getId())
                .periodId(budget.getPeriod() != null ? budget.getPeriod().getId() : null)
                .overallAmount(budget.getOverallAmount())
                .totalAllocated(totalAllocated)
                .remainingUnallocated(remainingUnallocated)
                .allocations(allocationDtos)
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    public static BudgetResponses.AllocationDetails toAllocationDetails(CategoryAllocation allocation)
    {
        return BudgetResponses.AllocationDetails.builder()
                .id(allocation.getId())
                .budgetId(allocation.getBudget().getId())
                .category(toCategoryRef(allocation.getCategory()))
                .allocatedAmount(allocation.getAllocatedAmount())
                .createdAt(allocation.getCreatedAt())
                .updatedAt(allocation.getUpdatedAt())
                .build();
    }

    public static BudgetResponses.CategoryRef toCategoryRef(UserCategory category)
    {
        if (category == null)
        {
            return null;
        }
        return BudgetResponses.CategoryRef.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .build();
    }
}
