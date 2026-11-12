package com.expensemanagement.backend.CaptureManagement.Entities;

/**
 * How a transaction reached the app. Only pasted mobile money SMS exist today; receipts (S6-TECH-1)
 * and messages read directly from the phone can be added here without changing the review flow.
 */
public enum CaptureSource
{
    MOBILE_MONEY_SMS
}
