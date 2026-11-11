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

        /** The budget this expense is counted against, or null. */
        private final BudgetRef budget;

        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    /** Body of {@code GET /api/expenses/total} (US39). */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Total
    {
        /** First day the total covers, inclusive. */
        private final LocalDate from;
        /** Last day the total covers, inclusive. */
        private final LocalDate to;
        /** Sum of the matching expenses; 0 when there are none. */
        private final Money total;
        /** How many expenses make up the total. */
        private final long count;
        /**
         * The financial period the window belongs to, or null when the request named dates
         * only - a reporting window is not necessarily a period (section 6.2 of the vision).
         */
        private final PeriodResponses.Period period;
    }

    /** Just enough of the category to display it next to the expense. */
    /** The budget an expense belongs to. Its name may be null - unnamed budgets go by their dates. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetRef
    {
        private final UUID id;
        private final String name;
        private final LocalDate startDate;
        private final LocalDate endDate;
    }

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
