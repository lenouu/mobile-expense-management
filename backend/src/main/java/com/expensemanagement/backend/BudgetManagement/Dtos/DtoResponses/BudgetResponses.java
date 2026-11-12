package com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BudgetResponses
{
    /**
     * A budget with everything needed to render it: what was planned, what was spent under it, and
     * whether it can still be edited.
     *
     * <p>{@code spent} is the sum of the expenses the user <em>assigned</em> to this budget - not
     * everything dated inside its window. That is the whole point of the link: the ledger keeps
     * flowing, and a budget only sees what was put under it.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetDetails
    {
        private final UUID id;

        /** Null when the user did not name the plan. */
        private final String name;

        private final LocalDate startDate;
        private final LocalDate endDate;
        private final long lengthInDays;

        /** What the user planned to spend. */
        private final BigDecimal overallAmount;
        /** What the categories add up to. Equal to {@code overallAmount} whenever it is non-zero. */
        private final BigDecimal totalAllocated;

        /** Actual expenses assigned to this budget. */
        private final BigDecimal spent;

        /** {@code overallAmount − spent}. Negative means the goal was passed. */
        private final BigDecimal net;

        /** How many expenses are counted under it. */
        private final long expenseCount;

        private final BudgetStatus status;

        /** Whether an edit would be accepted right now. */
        private final boolean editable;

        private final int editsUsed;
        private final int editsRemaining;

        /** The instant the editing window closes, shown so the user knows how long is left. */
        private final LocalDateTime editableUntil;

        /** {@code SUCCESS} or {@code EXCEEDED} once the window has ended, otherwise null. */
        private final String verdict;

        private final LocalDateTime closedAt;

        private final List<AllocationDetails> allocations;

        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AllocationDetails
    {
        private final UUID id;
        private final UUID budgetId;
        private final CategoryRef category;

        /** What was planned for this category. */
        private final BigDecimal allocatedAmount;
        /** What was spent on it under this budget. */
        private final BigDecimal spent;
        /** {@code allocatedAmount − spent}; negative when the category was overspent. */
        private final BigDecimal remaining;
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
     * One row of the budget list: what is running, what is waiting, and what is history.
     *
     * <p>Lighter than {@link BudgetDetails} on purpose - a list only needs enough to draw a row.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetSummary
    {
        private final UUID id;
        private final String name;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final BigDecimal overallAmount;
        private final BigDecimal spent;
        private final BigDecimal net;
        private final BudgetStatus status;
        private final String verdict;
        private final LocalDateTime closedAt;
    }
}
