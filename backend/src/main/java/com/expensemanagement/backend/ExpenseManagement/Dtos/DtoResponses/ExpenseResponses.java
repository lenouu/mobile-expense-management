package com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.Shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outgoing payloads for expenses (S2-TECH-2). Same shape as {@code UserResponses}.
 */
public class ExpenseResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details
    {
        private final UUID id;
        /** Never a bare number: {@code {"amount": 12.50, "currency": "XAF"}}. */
        private final Money amount;
        private final LocalDate date;
        private final String description;
        private final CategoryRef category;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    /** Body of {@code GET /api/expenses/total} (US39). */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Total
    {
        /** The financial period the total covers. */
        private final PeriodResponses.Period period;
        /** Sum of the expenses dated inside the period; 0 when there are none. */
        private final Money total;
        /** How many expenses make up the total. */
        private final long count;
    }

    /** Just enough of the category to display it next to the expense. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategoryRef
    {
        private final UUID id;
        private final String name;
        private final String icon;
        private final String color;
    }
}
