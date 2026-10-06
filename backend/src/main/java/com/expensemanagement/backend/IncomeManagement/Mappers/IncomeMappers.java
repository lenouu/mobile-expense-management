package com.expensemanagement.backend.IncomeManagement.Mappers;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import org.springframework.util.StringUtils;

public final class IncomeMappers
{
    private IncomeMappers()
    {
        throw new AssertionError("IncomeMappers is a static utility class and cannot be instantiated");
    }

    public static IncomeResponses.Details toDetails(Income income)
    {
        return IncomeResponses.Details.builder()
                .id(income.getId())
                .amount(income.getAmount())
                .date(income.getDate())
                .source(income.getSource())
                .description(income.getDescription())
                .createdAt(income.getCreatedAt())
                .updatedAt(income.getUpdatedAt())
                .build();
    }

    /** Copies the request onto the income (create and full replace). */
    public static Income apply(Income income, IncomeRequests.SaveIncome request)
    {
        income.setAmount(request.getAmount());
        income.setDate(request.getDate());
        income.setSource(request.getSource().trim());
        income.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        return income;
    }
}
