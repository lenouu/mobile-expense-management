package com.expensemanagement.backend.BudgetManagement.Enums;

/**
 * Where a budget is in its life.
 *
 * <p>Nothing here blocks spending: a budget is a goal, and going past it is recorded as a negative
 * balance. The status is about what may be <em>changed</em>, and about what the user is looking at.
 */
public enum BudgetStatus
{
    /** The window has not started yet. Fully editable, and accepts no expenses. */
    UPCOMING,

    /** The window is running and the plan can still be edited. */
    ACTIVE,

    /** The window is running, but the plan is fixed for the rest of it. */
    FIXED,

    /** The window has ended. Read-only history, with a verdict. */
    CLOSED
}
