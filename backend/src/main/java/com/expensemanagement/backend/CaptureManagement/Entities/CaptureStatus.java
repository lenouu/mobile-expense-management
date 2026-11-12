package com.expensemanagement.backend.CaptureManagement.Entities;

/**
 * Where a captured transaction is in its review (S6-TECH-2, US37).
 *
 * <p>A capture is never an expense by itself: it waits as {@link #PENDING} until the user either
 * confirms it, which creates the expense, or dismisses it.
 */
public enum CaptureStatus
{
    PENDING,
    CONFIRMED,
    DISMISSED
}
