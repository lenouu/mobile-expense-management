package com.expensemanagement.backend.BudgetManagement.Calendar;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * When a budget stops being editable (US32, US33).
 *
 * <p>The rule from the product vision: after a budget exists, it may be edited only within the
 * <strong>first 6% of its own duration</strong>, and at most {@value #EDIT_CAP} times. Whichever
 * runs out first fixes it.
 *
 * <p>6% of a 30-day month is 1.8 days; 6% of a 7-day week is just over 10 hours - the examples the
 * vision itself gives. Because the window is the budget's own, a two-month plan gets a noticeably
 * longer editing window than a one-week plan.
 *
 * <p>A budget that has not started yet is always editable: the clock only begins on its first day,
 * and a typo before the plan begins should be fixable.
 */
public final class BudgetWindow
{
    /** How many edits a budget gets before it is fixed. The vision says "3 to 4"; this is 3. */
    public static final int EDIT_CAP = 3;

    /** The share of the budget's duration during which edits remain possible. */
    public static final double WINDOW_FRACTION = 0.06;

    /** A budget must last at least a week, mirroring the vision's period bounds. */
    public static final int MINIMUM_DAYS = 7;

    /** And at most a year. */
    public static final int MAXIMUM_DAYS = 366;

    private BudgetWindow()
    {
        throw new AssertionError("BudgetWindow is a static utility class and cannot be instantiated");
    }

    /** The instant the edit clock starts running: the start of the budget's first day. */
    public static LocalDateTime opensAt(LocalDate startDate)
    {
        return startDate.atStartOfDay();
    }

    /** The instant after which a running budget is fixed, whatever the edit count says. */
    public static LocalDateTime editableUntil(LocalDate startDate, long lengthInDays)
    {
        long minutes = Math.max(1, Math.round(lengthInDays * WINDOW_FRACTION * 24 * 60));
        return opensAt(startDate).plusMinutes(minutes);
    }

    /** Convenience for a budget. */
    public static LocalDateTime editableUntil(Budget budget)
    {
        return editableUntil(budget.getStartDate(), budget.lengthInDays());
    }

    /** Halfway through the edit window: when the "closing soon" reminder becomes due (US59). */
    public static LocalDateTime reminderDueAt(Budget budget)
    {
        LocalDateTime opens = opensAt(budget.getStartDate());
        return opens.plus(Duration.between(opens, editableUntil(budget)).dividedBy(2));
    }

    /** Whether the time half of the rule still allows an edit. */
    public static boolean isWindowOpen(Budget budget, LocalDateTime now)
    {
        // Before the start there is no clock to run out: the plan has not begun.
        if (!now.toLocalDate().isAfter(budget.getStartDate().minusDays(1))) {
            return true;
        }
        return now.isBefore(editableUntil(budget));
    }

    /** Whether the count half of the rule still allows an edit. */
    public static boolean hasEditsLeft(int editsUsed)
    {
        return editsUsed < EDIT_CAP;
    }
}
