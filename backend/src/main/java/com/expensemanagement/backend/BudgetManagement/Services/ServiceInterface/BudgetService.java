package com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BudgetService
{
    /**
     * Creates a budget for a window of the caller's choosing.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException when the window is
     *         invalid, overlaps another budget, or the split does not add up to the total
     */
    BudgetResponses.BudgetDetails createBudget(UUID userId, BudgetRequests.CreateBudget request);

    /**
     * Replaces a budget's plan. Accepted only while it is editable and under the edit cap.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException when it is fixed,
     *         closed, or the new plan is invalid
     */
    BudgetResponses.BudgetDetails updateBudget(UUID userId, UUID budgetId, BudgetRequests.UpdateBudget request);

    BudgetResponses.BudgetDetails getBudget(UUID userId, UUID budgetId);

    /**
     * The caller's budgets, running first, then upcoming, then history.
     *
     * <p>Reading a budget whose window has ended closes it and writes the recap notification, which
     * is why this is a write-capable operation: there is no scheduler to do it in the background.
     */
    List<BudgetResponses.BudgetSummary> listMine(UUID userId);

    /** The expenses assigned to a budget - the rows behind its spending figure. */
    Page<ExpenseResponses.Details> expensesOf(UUID userId, UUID budgetId, Pageable pageable);

    /**
     * Deletes a budget.
     *
     * <p>A future or running budget can go, and taking it away takes the expenses that were
     * recorded under it: they exist only as part of that plan. A closed budget cannot be deleted -
     * its verdict is history.
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException when it has closed
     */
    void deleteBudget(UUID userId, UUID budgetId);
}
