package com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.CaptureManagement.Entities.CaptureSource;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyProvider;
import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyTransactionType;
import com.expensemanagement.backend.Shared.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class CaptureResponses
{
    /**
     * A captured transaction. Fields the SMS did not contain are null, so the review form shows
     * them blank instead of a guess.
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details
    {
        private final UUID id;
        private final CaptureSource source;
        private final MobileMoneyProvider provider;
        private final MobileMoneyTransactionType type;
        /** The amount sent or paid, without the fee. */
        private final Money amount;
        /** The operator's fee, or null when the message stated none. */
        private final Money fee;
        /** What actually left the wallet: amount plus fee. The review form starts from this. */
        private final Money total;
        private final String counterparty;
        private final String reference;
        private final LocalDate transactionDate;
        private final LocalTime transactionTime;
        private final String merchant;
        private final Double confidence;
        /** A description the review form can start from, e.g. "MTN MoMo payment - ENEO". */
        private final String suggestedDescription;
        private final CaptureStatus status;
        /** The expense created on confirmation; null until then. */
        private final UUID expenseId;
        private final LocalDateTime createdAt;
        private final LocalDateTime reviewedAt;
    }

    public record Receipt(UUID captureId, Extracted extracted, double confidence) {}

    public record Extracted(Money amount, LocalDate date, String merchant) {}
}
