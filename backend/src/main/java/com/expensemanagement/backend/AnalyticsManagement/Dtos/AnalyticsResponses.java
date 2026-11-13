package com.expensemanagement.backend.AnalyticsManagement.Dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * The Dashboard's one payload.
 *
 * <p>Everything the page shows comes from this single response, so the headline, the charts and the
 * sentences can never disagree with one another. Money is always {@code {amount, currency}}.
 */
public class AnalyticsResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Money
    {
        private final BigDecimal amount;
        private final String currency;
    }

    /** The three numbers of a window, and the rate that makes them comparable. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Totals
    {
        private final Money income;
        private final Money expenses;
        /** Income minus expenses. Negative is a debt, not an error. */
        private final Money net;
        /** Net as a percentage of income, or null when nothing came in. */
        private final Double savingsRate;
        /** The window's total divided by its number of days. */
        private final Money averagePerDay;
        private final long incomeCount;
        private final long expenseCount;
    }

    /** Percentage change against the comparison window; null where the baseline was zero. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Change
    {
        private final Double incomePercent;
        private final Double expensePercent;
        private final Double netPercent;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class DailyPoint
    {
        private final LocalDate date;
        private final Money income;
        private final Money expenses;
    }

    /** One segment of the window, for the balance chart. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Bucket
    {
        private final String label;
        private final LocalDate from;
        private final LocalDate to;
        private final Money income;
        private final Money expenses;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class CategorySlice
    {
        private final UUID id;
        private final String name;
        private final String icon;
        private final String color;
        private final Money total;
        private final Money previousTotal;
        /** Against the same slice in the window before; null when it had nothing. */
        private final Double changePercent;
        private final double share;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SourceSlice
    {
        private final UUID id;
        private final String name;
        private final String icon;
        private final String color;
        private final Money total;
        private final Money previousTotal;
        /** Against the same slice in the window before; null when it had nothing. */
        private final Double changePercent;
        private final double share;
    }

    /** The best or worst day, measured on net - a rent day is not bad if the salary landed. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DaySummary
    {
        private final LocalDate date;
        private final Money income;
        private final Money expenses;
        private final Money net;
    }

    /** The plan in force, so the dashboard is not silent about budgets. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BudgetSnapshot
    {
        private final UUID id;
        private final String name;
        private final Money planned;
        private final Money spent;
        private final Money remaining;
        /** Days left in its window; zero or negative once it has ended. */
        private final long daysLeft;
        private final String status;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary
    {
        private final LocalDate from;
        private final LocalDate to;
        private final String currency;

        private final Totals current;
        private final LocalDate previousFrom;
        private final LocalDate previousTo;
        private final Totals previous;
        private final Change change;

        private final List<DailyPoint> daily;
        private final List<Bucket> buckets;
        /** The window before, day by day, aligned to {@code daily} by index. */
        private final List<DailyPoint> previousDaily;
        /** The window before, in the same buckets, so bars pair one for one. */
        private final List<Bucket> previousBuckets;
        private final List<CategorySlice> byCategory;
        private final List<SourceSlice> bySource;

        private final DaySummary bestDay;
        private final DaySummary worstDay;
        /** The single largest expense of the window, or null when there was none. */
        private final BiggestExpense biggestExpense;
        /** Days with no expense at all - a figure people like to see. */
        private final long noSpendDays;

        private final BudgetSnapshot budget;
    }
    /** The one expense worth naming on a dashboard. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BiggestExpense
    {
        private final LocalDate date;
        private final String categoryName;
        private final String icon;
        private final String color;
        private final String description;
        private final Money amount;
    }}
