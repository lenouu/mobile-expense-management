package com.expensemanagement.backend.IncomeManagement.Mappers;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;
import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import com.expensemanagement.backend.Shared.Money;
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
                .amount(Money.of(income.getAmount(), income.getCurrency()))
                .date(income.getDate())
                .source(income.getSource() == null ? null : IncomeResponses.SourceRef.builder()
                        .id(income.getSource().getId())
                        .name(income.getSource().getName())
                        .icon(income.getSource().getIcon())
                        .color(income.getSource().getColor())
                        .build())
                .name(income.getName())
                .description(income.getDescription())
                .createdAt(income.getCreatedAt())
                .updatedAt(income.getUpdatedAt())
                .build();
    }

    /** Copies the request onto the income (create and full replace). */
    public static Income apply(Income income, IncomeRequests.SaveIncome request, IncomeSource source)
    {
        income.setAmount(request.getAmount().normalisedAmount());
        income.setCurrency(request.getAmount().currencyOrDefault());
        income.setDate(request.getDate());
        income.setSource(source);
        income.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : null);
        income.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        return income;
    }
}
