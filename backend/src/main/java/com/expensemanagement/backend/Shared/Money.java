package com.expensemanagement.backend.Shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * An amount together with its currency, which is the shape every money field in the API uses.
 *
 * <p>The contract is explicit that money is never a bare number: a client must not be able to
 * receive {@code 15000} without being told whether that is 15 000 XAF or 15 000 USD. Both a
 * request and a response carry {@code {"amount": 15000.00, "currency": "XAF"}}.
 *
 * <p>{@code amount} stays a {@link BigDecimal} with two decimals rather than an integer in the
 * currency's smallest unit. That keeps the existing column type, the existing two-decimal
 * validation and every stored value intact, while still exposing the currency the contract asks
 * for. Switching to minor units later is a migration, not a new decision.
 *
 * <p>{@code currency} is optional on input and defaults to {@value #DEFAULT_CURRENCY} (CFA franc,
 * the currency this product's examples are written in), so a single-currency client does not have
 * to repeat it on every record. It is always present in a response.
 */
public record Money(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 10 digits and 2 decimals")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code like XAF")
        String currency)
{
    /** CFA franc: the currency the product's own examples use, and the fallback when omitted. */
    public static final String DEFAULT_CURRENCY = "XAF";

    private static final int SCALE = 2;

    /** Builds a money value, normalising the currency (upper case, defaulted, never null). */
    public static Money of(BigDecimal amount, String currency)
    {
        return new Money(amount, currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase(Locale.ROOT));
    }

    /** Builds a money value in the default currency. */
    public static Money of(BigDecimal amount)
    {
        return of(amount, DEFAULT_CURRENCY);
    }

    /** The currency as it will be stored: upper case, defaulted, never null. */
    @JsonIgnore
    public String currencyOrDefault()
    {
        return currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase(Locale.ROOT);
    }

    /** The amount as it will be stored: two decimals, never null. */
    @JsonIgnore
    public BigDecimal normalisedAmount()
    {
        return amount == null ? BigDecimal.ZERO.setScale(SCALE) : amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
