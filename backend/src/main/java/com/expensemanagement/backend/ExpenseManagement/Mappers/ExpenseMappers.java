package com.expensemanagement.backend.ExpenseManagement.Mappers;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.Shared.Money;
import org.springframework.util.StringUtils;

public final class ExpenseMappers
{
    private ExpenseMappers()
    {
        throw new AssertionError("ExpenseMappers is a static utility class and cannot be instantiated");
    }

    public static ExpenseResponses.Details toDetails(Expense expense)
    {
        UserCategory category = expense.getCategory();
        return ExpenseResponses.Details.builder()
                .id(expense.getId())
                .amount(Money.of(expense.getAmount(), expense.getCurrency()))
                .date(expense.getDate())
                .description(expense.getDescription())
                .category(ExpenseResponses.CategoryRef.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .icon(category.getIcon())
                        .color(category.getColor())
                        .build())
                .budget(toBudgetRef(expense.getBudget()))
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    /** The budget the expense is counted against, or null when it belongs to none. */
    private static ExpenseResponses.BudgetRef toBudgetRef(Budget budget)
    {
        if (budget == null)
        {
            return null;
        }
        return ExpenseResponses.BudgetRef.builder()
                .id(budget.getId())
                .name(budget.getName())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .build();
    }

    /**
     * Copies the request onto the expense (create and full replace). The category is resolved
     * and ownership-checked by the service, so it is passed in rather than looked up here.
     */
    public static Expense apply(Expense expense, ExpenseRequests.SaveExpense request, UserCategory category)
    {
        expense.setAmount(request.getAmount().normalisedAmount());
        expense.setCurrency(request.getAmount().currencyOrDefault());
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        return expense;
    }
}
