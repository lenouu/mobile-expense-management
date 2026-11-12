package com.expensemanagement.backend.CaptureManagement.Mappers;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction;
import com.expensemanagement.backend.Shared.Money;

import java.math.BigDecimal;

public final class CaptureMappers
{
    private static final int MAX_DESCRIPTION = 255;

    private CaptureMappers()
    {
        throw new AssertionError("CaptureMappers is a static utility class and cannot be instantiated");
    }

    public static CaptureResponses.Details toDetails(CapturedTransaction capture)
    {
        BigDecimal fee = capture.getFee();
        BigDecimal total = fee == null ? capture.getAmount() : capture.getAmount().add(fee);

        return CaptureResponses.Details.builder()
                .id(capture.getId())
                .source(capture.getSource())
                .provider(capture.getProvider())
                .type(capture.getType())
                .amount(Money.of(capture.getAmount(), capture.getCurrency()))
                .fee(fee == null ? null : Money.of(fee, capture.getCurrency()))
                .total(Money.of(total, capture.getCurrency()))
                .counterparty(capture.getCounterparty())
                .reference(capture.getReference())
                .transactionDate(capture.getTransactionDate())
                .transactionTime(capture.getTransactionTime())
                .suggestedDescription(suggestedDescription(capture))
                .status(capture.getStatus())
                .expenseId(capture.getExpenseId())
                .createdAt(capture.getCreatedAt())
                .reviewedAt(capture.getReviewedAt())
                .build();
    }

    /** "MTN MoMo payment - ENEO", or "Orange Money transfer" when no counterparty was read. */
    static String suggestedDescription(CapturedTransaction capture)
    {
        String description = capture.getProvider().label() + " " + capture.getType().label();
        if (capture.getCounterparty() != null)
        {
            description += " - " + capture.getCounterparty();
        }
        return description.length() > MAX_DESCRIPTION ? description.substring(0, MAX_DESCRIPTION) : description;
    }
}
