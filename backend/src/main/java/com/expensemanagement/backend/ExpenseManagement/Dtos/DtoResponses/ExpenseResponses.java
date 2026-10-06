package com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
