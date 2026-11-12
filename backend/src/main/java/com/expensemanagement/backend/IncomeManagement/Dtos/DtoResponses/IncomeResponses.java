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
        /** The source record: id, name, icon and colour, so a row can be drawn without a lookup. */
        private final SourceRef source;
        /** An optional label for this payment, e.g. "November salary". */
        private final String name;
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
    /** The income source as an income row needs it. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class SourceRef
    {
        private final UUID id;
        private final String name;
        private final String icon;
        private final String color;
    }

    /** One source's share of a window: what came from it, and how much of the total that is. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class SourceSlice
    {
        private final SourceRef source;
        private final Money total;
        private final long count;
        private final double share;
    }

    /**
     * Body of {@code GET /incomes/summary}: what came in during a window, and from where.
     *
     * <p>The mirror of the expense overview, and the other half of the comparison the dashboard
     * will draw: income is never netted against spending, so this total is always gross.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary
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

        /** {@code total − previousTotal}; negative when less came in than before. */
        private final Money change;
        /** Against the previous window, or null when that window had nothing. */
        private final Double changePercent;

        private final java.util.List<SourceSlice> bySource;
        private final java.util.List<DaySlice> daily;
    }

    /** One day of the window; days with nothing are present as zero. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DaySlice
    {
        private final LocalDate date;
        private final Money total;
    }}
