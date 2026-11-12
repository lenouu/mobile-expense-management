package com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.Shared.Money;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Response DTOs for the savings and debt analytics endpoints (US44, US46, US48, US49).
 *
 * <p>{@code savings} and {@code debt} are serialized as explicit {@code null} when absent
 * (the contract specifies "Money or null").
 */
public class SavingsResponses
{
    /**
     * Summary of savings, debt, and carry-forward calculation for one financial period.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class PeriodSummary
    {
        private final UUID periodId;
        private final Money totalIncome;
        private final Money totalExpenses;

        /** Debt carried in from the previous period; zero for the user's first period. */
        private final Money carriedDebt;

        /** {@code totalIncome - carriedDebt}; may be negative. */
        private final Money effectiveIncome;

        /** Present when net &gt; 0, otherwise explicit null. */
        private final Money savings;

        /** Present when net &lt; 0, otherwise explicit null. */
        private final Money debt;
    }
}

