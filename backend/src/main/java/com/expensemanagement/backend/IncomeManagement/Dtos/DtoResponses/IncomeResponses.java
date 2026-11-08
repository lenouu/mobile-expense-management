package com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.Shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outgoing payloads for incomes (S2-TECH-2). Same shape as {@code UserResponses}.
 */
public class IncomeResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details
    {
        private final UUID id;
        /** Never a bare number: {@code {"amount": 250000.00, "currency": "XAF"}}. */
        private final Money amount;
        private final LocalDate date;
        private final String source;
        private final String description;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    /** US13: total income for a financial period. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class TotalIncome
    {
        private final Money total;
        private final UUID periodId;
        private final LocalDate periodStartDate;
        private final LocalDate periodEndDate;
        private final long recordCount;
    }
}
