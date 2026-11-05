package com.expensemanagement.backend.ExpenseManagement.Mappers;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
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
                .amount(expense.getAmount())
                .date(expense.getDate())
                .description(expense.getDescription())
                .category(ExpenseResponses.CategoryRef.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .icon(category.getIcon())
                        .color(category.getColor())
                        .build())
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    /**
     * Copies the request onto the expense (create and full replace). The category is resolved
     * and ownership-checked by the service, so it is passed in rather than looked up here.
     */
    public static Expense apply(Expense expense, ExpenseRequests.SaveExpense request, UserCategory category)
    {
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        return expense;
    }
}
