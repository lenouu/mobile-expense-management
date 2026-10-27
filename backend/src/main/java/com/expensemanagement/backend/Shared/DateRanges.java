package com.expensemanagement.backend.Shared;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;

import java.time.LocalDate;

/**
 * Optional {@code from} / {@code to} filters shared by the expense and income listings.
 */
public final class DateRanges
{
    /** Wide enough for any real record, and both representable in PostgreSQL's date type. */
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private DateRanges()
    {
        throw new AssertionError("DateRanges is a static utility class and cannot be instantiated");
    }

    public record Range(LocalDate from, LocalDate to)
    {
    }

    /**
     * Fills a missing bound so the repository can always use a single BETWEEN query.
     *
     * @throws InvalidRequestException if from is after to
     */
    public static Range of(LocalDate from, LocalDate to)
    {
        if (from != null && to != null && from.isAfter(to))
        {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        return new Range(from != null ? from : EARLIEST, to != null ? to : LATEST);
    }
}
