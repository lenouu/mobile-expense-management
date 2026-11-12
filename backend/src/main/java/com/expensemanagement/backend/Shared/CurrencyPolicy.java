package com.expensemanagement.backend.Shared;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;

import java.util.Locale;

/**
 * The rule that keeps a sum meaningful: every amount in an account uses that account's base
 * currency.
 *
 * <p>An amount of 50 EUR and an amount of 10 000 XAF cannot be added without a conversion rate,
 * and this product has none. Refusing the odd one out is the only honest answer: the alternative
 * is a total that looks authoritative and is wrong. When multi-currency entry arrives, this is the
 * class that grows a rate and a conversion.
 */
public final class CurrencyPolicy
{
    private CurrencyPolicy()
    {
        throw new AssertionError("CurrencyPolicy is a static utility class and cannot be instantiated");
    }

    /**
     * The currency a new or edited record is stored in.
     *
     * @param money        the amount from the request; its currency may be omitted
     * @param baseCurrency the account's currency, e.g. {@code XAF}
     * @return the base currency, which is what gets stored
     * @throws InvalidRequestException when a different currency is asked for
     */
    public static String resolve(Money money, String baseCurrency)
    {
        String base = baseCurrency == null || baseCurrency.isBlank()
                ? Money.DEFAULT_CURRENCY
                : baseCurrency.trim().toUpperCase(Locale.ROOT);

        if (money == null || money.currency() == null || money.currency().isBlank())
        {
            // Nothing was asked for, so the account's own currency applies.
            return base;
        }

        String requested = money.currency().trim().toUpperCase(Locale.ROOT);
        if (!requested.equals(base))
        {
            throw new InvalidRequestException("This account records amounts in " + base
                    + ", not " + requested + ". Change the base currency in Settings first.");
        }

        return base;
    }
}
