package com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BudgetService
{
    BudgetResponses.BudgetDetails createBudget(UUID userId, BudgetRequests.CreateBudget request);

    BudgetResponses.BudgetDetails updateBudget(UUID userId, UUID budgetId, BudgetRequests.UpdateBudget request);

    BudgetResponses.BudgetDetails getBudget(UUID userId, UUID budgetId);

    BudgetResponses.BudgetDetails getBudgetByPeriod(UUID userId, UUID periodId);

    BudgetResponses.AllocationDetails addOrUpdateAllocation(UUID userId, UUID budgetId, CategoryAllocationRequests.SaveAllocation request);

    BudgetResponses.AllocationDetails updateAllocation(UUID userId, UUID budgetId, UUID allocationId, CategoryAllocationRequests.UpdateAllocation request);

    void deleteAllocation(UUID userId, UUID budgetId, UUID allocationId);

    List<BudgetResponses.AllocationDetails> getAllocations(UUID userId, UUID budgetId);

    /**
     * Validates that adding or updating an allocation will not cause the total allocated amount
     * to exceed the overall budget (S4-TECH-2).
     *
     * @param budget the budget being validated
     * @param proposedAmount the amount for the category allocation
     * @param existingAllocationId null if adding a new allocation, or the ID of the allocation being updated (to avoid double-counting)
     */
    void validateAllocationAgainstBudget(Budget budget, BigDecimal proposedAmount, UUID existingAllocationId);
}
