package com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.Shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outgoing payloads for expenses (S2-TECH-2, S8-TECH-1). Same shape as {@code UserResponses}.
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
    /**
     * Body of {@code GET /expenses/overview}: one window, and what it is worth.
     *
     * <p>Everything is derived from the same rows the ledger would list for the window, so the
     * headline, the category split and the daily shape can never disagree with the list.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Overview
    {
        private final LocalDate from;
        private final LocalDate to;

        private final Money total;
        private final long count;

        /** The window of the same length ending the day before {@code from}. */
        private final LocalDate previousFrom;
        private final LocalDate previousTo;
        private final Money previousTotal;
        private final long previousCount;

        /** {@code total − previousTotal}. Negative when this window spent less than the last. */
        private final Money change;
        /** Percentage change against the previous window, or null when that window had nothing. */
        private final Double changePercent;

        private final List<CategorySlice> byCategory;
        private final List<DaySlice> daily;
    }

    /** One category's share of a window, and how it compares with the window before. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategorySlice
    {
        private final CategoryRef category;
        private final Money total;
        private final long count;
        private final Money previousTotal;
        private final Money change;
        private final Double changePercent;
        /** Percentage of the window's total, 0-100. */
        private final double share;
    }

    /** One day of the window. Days with nothing are present as zero, so the strip has no gaps. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DaySlice
    {
        private final LocalDate date;
        private final Money total;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategorySpending
    {
        private final UUID categoryId;
        private final String categoryName;
        private final String categoryIcon;
        private final String categoryColor;
        private final Money totalSpent;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SpendingSummary
    {
        private final LocalDate from;
        private final LocalDate to;
        private final Money totalSpent;
        private final List<CategorySpending> categories;
    }
}
