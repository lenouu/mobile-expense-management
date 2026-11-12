package com.expensemanagement.backend.CaptureManagement.Entities;

/**
 * What kind of money movement an SMS describes. Only money leaving the wallet can be captured as
 * an expense; an SMS for money received is refused by the parser.
 */
public enum MobileMoneyTransactionType
{
    /** Paying a merchant or a bill. */
    PAYMENT("payment"),
    /** Sending money to another person. */
    TRANSFER("transfer"),
    /** Cash taken out at an agent. */
    WITHDRAWAL("withdrawal"),
    /** An outgoing transaction whose kind the wording did not reveal; the user decides on review. */
    UNKNOWN("transaction");

    private final String label;

    MobileMoneyTransactionType(String label)
    {
        this.label = label;
    }

    public String label()
    {
        return label;
    }
}
