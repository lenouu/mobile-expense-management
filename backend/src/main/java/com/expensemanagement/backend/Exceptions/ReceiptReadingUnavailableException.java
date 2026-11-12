package com.expensemanagement.backend.Exceptions;

public class ReceiptReadingUnavailableException extends RuntimeException
{
    public ReceiptReadingUnavailableException()
    {
        super("receipt reading unavailable, enter the expense manually");
    }
}
