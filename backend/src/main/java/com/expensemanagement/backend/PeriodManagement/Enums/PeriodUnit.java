package com.expensemanagement.backend.PeriodManagement.Enums;

/**
 * Unit of a financial period length (S4-TECH-1). A length is a count of one of these, e.g.
 * 2 WEEKS or 1 MONTHS.
 */
public enum PeriodUnit
{
    DAYS(1),
    WEEKS(7),
    /** Calendar months: from 25 January to 24 February, whatever the number of days. */
    MONTHS(28);

    /** The fewest days one unit can span - used to enforce the one week minimum (US27). */
    private final int minimumDays;

    PeriodUnit(int minimumDays)
    {
        this.minimumDays = minimumDays;
    }

    public int minimumDays()
    {
        return minimumDays;
    }
}
