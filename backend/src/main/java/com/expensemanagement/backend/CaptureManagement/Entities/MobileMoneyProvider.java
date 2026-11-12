package com.expensemanagement.backend.CaptureManagement.Entities;

/** The mobile money services whose SMS the parser understands. */
public enum MobileMoneyProvider
{
    MTN_MOMO("MTN MoMo"),
    ORANGE_MONEY("Orange Money");

    private final String label;

    MobileMoneyProvider(String label)
    {
        this.label = label;
    }

    /** Human-readable name, used in the suggested expense description. */
    public String label()
    {
        return label;
    }
}
