package com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;

import java.math.BigDecimal;
import java.util.List;

public interface BudgetService
{
    BudgetResponses.BudgetDetails createBudget(Long userId, BudgetRequests.CreateBudget request);

    BudgetResponses.BudgetDetails updateBudget(Long userId, Long budgetId, BudgetRequests.UpdateBudget request);

    BudgetResponses.BudgetDetails getBudget(Long userId, Long budgetId);

    BudgetResponses.BudgetDetails getBudgetByPeriod(Long userId, Long periodId);

    BudgetResponses.AllocationDetails addOrUpdateAllocation(Long userId, Long budgetId, CategoryAllocationRequests.SaveAllocation request);

    BudgetResponses.AllocationDetails updateAllocation(Long userId, Long budgetId, Long allocationId, CategoryAllocationRequests.UpdateAllocation request);

    void deleteAllocation(Long userId, Long budgetId, Long allocationId);

    List<BudgetResponses.AllocationDetails> getAllocations(Long userId, Long budgetId);

    /**
     * Validates that adding or updating an allocation will not cause the total allocated amount
     * to exceed the overall budget (S4-TECH-2).
     *
     * @param budget the budget being validated
     * @param proposedAmount the amount for the category allocation
     * @param existingAllocationId null if adding a new allocation, or the ID of the allocation being updated (to avoid double-counting)
     */
    void validateAllocationAgainstBudget(Budget budget, BigDecimal proposedAmount, Long existingAllocationId);
}
