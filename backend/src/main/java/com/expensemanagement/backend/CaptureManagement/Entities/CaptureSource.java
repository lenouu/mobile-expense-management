package com.expensemanagement.backend.CaptureManagement.Entities;

/**
 * How a transaction reached the app: a pasted mobile money SMS, or a photographed receipt
 * (S6-TECH-1). Messages read directly from the phone can be added here without changing the
 * review flow.
 */
public enum CaptureSource
{
    MOBILE_MONEY_SMS,
    RECEIPT
}
