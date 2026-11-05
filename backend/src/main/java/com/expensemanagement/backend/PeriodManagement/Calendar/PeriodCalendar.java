package com.expensemanagement.backend.PeriodManagement.Calendar;

import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The date arithmetic of financial periods (S4-TECH-1), kept free of Spring and the database so
 * it can be unit tested on its own.
 *
 * <p>A run of periods of the same length starts at an {@code anchor} date. Period {@code k} of
 * the run (0-based) starts at {@code anchor + k * length} and ends the day before period
 * {@code k + 1} starts. Always counting from the anchor, rather than from the previous period,
 * stops monthly periods from drifting: from an anchor on 31 January the starts are 31 January,
 * 28 February, 31 March - not 31 January, 28 February, 28 March.
 */
public final class PeriodCalendar
{
    /** One week, the shortest financial period a user may choose (US27). */
    public static final int MINIMUM_DAYS = 7;

    private PeriodCalendar()
    {
        throw new AssertionError("PeriodCalendar is a static utility class and cannot be instantiated");
    }

    /** First day of period {@code index} (0-based) of the run starting at {@code anchor}. */
    public static LocalDate startOf(LocalDate anchor, int count, PeriodUnit unit, long index)
    {
        long steps = index * count;
        return switch (unit)
        {
            case DAYS -> anchor.plusDays(steps);
            case WEEKS -> anchor.plusWeeks(steps);
            case MONTHS -> anchor.plusMonths(steps);
        };
    }

    /** Last day (inclusive) of period {@code index} of the run starting at {@code anchor}. */
    public static LocalDate endOf(LocalDate anchor, int count, PeriodUnit unit, long index)
    {
        return startOf(anchor, count, unit, index + 1).minusDays(1);
    }

    /**
     * Index (0-based) of the period of the run that contains {@code date}.
     *
     * @throws IllegalArgumentException if the date is before the anchor
     */
    public static long indexOf(LocalDate anchor, int count, PeriodUnit unit, LocalDate date)
    {
        if (date.isBefore(anchor))
        {
            throw new IllegalArgumentException(date + " is before the start of the run, " + anchor);
        }

        // A close first guess, then corrected: month lengths vary, so for MONTHS the guess can
        // be one off in either direction.
        long index = switch (unit)
        {
            case DAYS -> ChronoUnit.DAYS.between(anchor, date) / count;
            case WEEKS -> ChronoUnit.WEEKS.between(anchor, date) / count;
            case MONTHS -> ChronoUnit.MONTHS.between(anchor, date) / count;
        };
        while (startOf(anchor, count, unit, index + 1).compareTo(date) <= 0)
        {
            index++;
        }
        while (index > 0 && startOf(anchor, count, unit, index).isAfter(date))
        {
            index--;
        }
        return index;
    }

    /** True when a length of {@code count} units spans at least one week (US27). */
    public static boolean isAtLeastOneWeek(int count, PeriodUnit unit)
    {
        return count >= 1 && (long) count * unit.minimumDays() >= MINIMUM_DAYS;
    }

    /** Number of days from {@code start} to {@code end}, both included. */
    public static long lengthInDays(LocalDate start, LocalDate end)
    {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * Last day of the opening window covering the first {@code percent}% of a period, e.g. the
     * 6% edit window for budgets (US32, US33). Rounded up, so a period always has at least one
     * day of window: 6% of a 30-day period is 1.8 days, giving a 2-day window.
     */
    public static LocalDate openingWindowEnd(LocalDate start, LocalDate end, double percent)
    {
        if (percent <= 0 || percent > 100)
        {
            throw new IllegalArgumentException("percent must be in (0, 100], was " + percent);
        }
        long windowDays = (long) Math.ceil(lengthInDays(start, end) * percent / 100.0);
        return start.plusDays(Math.max(1, windowDays) - 1);
    }
}
