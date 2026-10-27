package com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        private final Long id;
        private final BigDecimal amount;
        private final LocalDate date;
        private final String description;
        private final CategoryRef category;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    /** Just enough of the category to display it next to the expense. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategoryRef
    {
        private final Long id;
        private final String name;
        private final String icon;
        private final String color;
    }

    /**
     * S8-TECH-1: Aggregated spending per category.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategorySpending
    {
        private final Long categoryId;
        private final String categoryName;
        private final String categoryIcon;
        private final String categoryColor;
        private final BigDecimal totalSpent;
    }

    /**
     * S8-TECH-1: Aggregated total and per-category spending summary for a date range.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class SpendingSummary
    {
        private final LocalDate from;
        private final LocalDate to;
        private final BigDecimal totalSpent;
        private final List<CategorySpending> categories;
    }
}
