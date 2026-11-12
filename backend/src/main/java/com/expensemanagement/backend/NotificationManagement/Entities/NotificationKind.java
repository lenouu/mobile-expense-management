package com.expensemanagement.backend.NotificationManagement.Entities;

/** The kinds of in-app news the app can show. */
public enum NotificationKind
{
    /** A financial period ended, and its budget got a verdict. */
    BUDGET_PERIOD_CLOSED,

    /** Half of a budget's edit window has gone by and edits are still possible (US59). */
    EDIT_WINDOW_CLOSING
}
