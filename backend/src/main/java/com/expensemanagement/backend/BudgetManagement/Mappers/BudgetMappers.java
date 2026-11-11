package com.expensemanagement.backend.BudgetManagement.Mappers;

import com.expensemanagement.backend.BudgetManagement.Calendar.BudgetWindow;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BudgetMappers
{
    /** The verdicts a closed budget can carry. */
    public static final String SUCCESS = "SUCCESS";
    public static final String EXCEEDED = "EXCEEDED";

    private BudgetMappers() {}

    /**
     * Builds the full picture of a budget.
     *
     * <p>Spending is passed in rather than fetched here: the numbers come from the expenses the user
     * assigned, and a mapper that queried would hide a database round-trip in what looks like a
     * conversion.
     *
     * @param spentByCategory what was spent per category id under this budget; missing means zero
     */
    public static BudgetResponses.BudgetDetails toBudgetDetails(
            Budget budget,
            List<CategoryAllocation> allocations,
            BigDecimal spent,
            long expenseCount,
            Map<UUID, BigDecimal> spentByCategory,
            boolean editable)
    {
        List<CategoryAllocation> safeAllocations = allocations != null ? allocations : Collections.emptyList();
        Map<UUID, BigDecimal> spentMap = spentByCategory != null ? spentByCategory : Collections.emptyMap();

        BigDecimal totalAllocated = safeAllocations.stream()
                .map(CategoryAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actualSpent = spent != null ? spent : BigDecimal.ZERO;
        BigDecimal net = budget.getOverallAmount().subtract(actualSpent);

        List<BudgetResponses.AllocationDetails> allocationDtos = safeAllocations.stream()
                .map(allocation -> toAllocationDetails(
                        allocation,
                        spentMap.getOrDefault(allocation.getCategory().getId(), BigDecimal.ZERO)))
                .toList();

        return BudgetResponses.BudgetDetails.builder()
                .id(budget.getId())
                .name(budget.getName())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .lengthInDays(budget.lengthInDays())
                .overallAmount(budget.getOverallAmount())
                .totalAllocated(totalAllocated)
                .spent(actualSpent)
                .net(net)
                .expenseCount(expenseCount)
                .status(budget.getStatus())
                .editable(editable)
                .editsUsed(budget.getEditsUsed())
                .editsRemaining(Math.max(0, BudgetWindow.EDIT_CAP - budget.getEditsUsed()))
                .editableUntil(BudgetWindow.editableUntil(budget))
                .verdict(verdict(budget.getStatus(), actualSpent, budget.getOverallAmount()))
                .closedAt(budget.getClosedAt())
                .allocations(allocationDtos)
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    public static BudgetResponses.BudgetSummary toSummary(Budget budget, BigDecimal spent)
    {
        BigDecimal actualSpent = spent != null ? spent : BigDecimal.ZERO;

        return BudgetResponses.BudgetSummary.builder()
                .id(budget.getId())
                .name(budget.getName())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .overallAmount(budget.getOverallAmount())
                .spent(actualSpent)
                .net(budget.getOverallAmount().subtract(actualSpent))
                .status(budget.getStatus())
                .verdict(verdict(budget.getStatus(), actualSpent, budget.getOverallAmount()))
                .closedAt(budget.getClosedAt())
                .build();
    }

    public static BudgetResponses.AllocationDetails toAllocationDetails(
            CategoryAllocation allocation, BigDecimal spent)
    {
        BigDecimal actualSpent = spent != null ? spent : BigDecimal.ZERO;

        return BudgetResponses.AllocationDetails.builder()
                .id(allocation.getId())
                .budgetId(allocation.getBudget().getId())
                .category(toCategoryRef(allocation.getCategory()))
                .allocatedAmount(allocation.getAllocatedAmount())
                .spent(actualSpent)
                .remaining(allocation.getAllocatedAmount().subtract(actualSpent))
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

    /** A closed budget gets a verdict; a running one has nothing to declare yet. */
    public static String verdict(BudgetStatus status, BigDecimal spent, BigDecimal overallAmount)
    {
        if (status != BudgetStatus.CLOSED)
        {
            return null;
        }
        return spent.compareTo(overallAmount) <= 0 ? SUCCESS : EXCEEDED;
    }
}
