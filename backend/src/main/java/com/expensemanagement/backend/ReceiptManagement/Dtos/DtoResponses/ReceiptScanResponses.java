package com.expensemanagement.backend.ReceiptManagement.Dtos.DtoResponses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a read receipt produced: expenses the user can review, never expenses already saved.
 *
 * <p>Nothing here is persisted. The scan only proposes; the app shows the rows for confirmation
 * and the ordinary expense endpoint is what creates them.
 */
public final class ReceiptScanResponses
{
    private ReceiptScanResponses()
    {
    }

    /** The scan's answer. An empty list is a valid answer: the photo held no readable expense. */
    public record Scan(List<Expense> expenses)
    {
    }

    /**
     * @param description a short label, e.g. the shop or the item
     * @param amount      positive, in the caller's own currency
     * @param date        the day the expense happened; today when the photo does not say
     * @param category    the name of one of the caller's categories, or null when none matched
     */
    public record Expense(
            String description,
            BigDecimal amount,
            LocalDate date,
            String category
    )
    {
    }
}
