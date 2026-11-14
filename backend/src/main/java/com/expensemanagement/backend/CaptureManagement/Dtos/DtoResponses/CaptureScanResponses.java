package com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a pasted message contained, as proposals the user reviews.
 *
 * <p>Nothing here is persisted. The direction is a suggestion - the app shows it as a toggle,
 * and the user may turn an "out" into an "in" before anything is created.
 */
public final class CaptureScanResponses
{
    private CaptureScanResponses()
    {
    }

    /** Which way the money moved. */
    public enum Direction
    {
        /** Money arrived: an income. */
        IN,
        /** Money left: an expense. */
        OUT
    }

    /** The reader's answer. An empty list is valid: the text held no money movement. */
    public record Scan(List<Transaction> transactions)
    {
    }

    /**
     * @param direction    what the model believes; the user can flip it
     * @param description  a short label, e.g. the merchant or the sender
     * @param amount       positive, in the caller's own currency
     * @param date         the day it happened; today when the message does not say
     * @param category     an expense category name - only for {@code OUT}, null otherwise
     * @param incomeSource an income source name - only for {@code IN}, null otherwise
     */
    public record Transaction(
            Direction direction,
            String description,
            BigDecimal amount,
            LocalDate date,
            String category,
            String incomeSource
    )
    {
    }
}
