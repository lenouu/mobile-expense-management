package com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Outgoing payloads for incomes (S2-TECH-2). Same shape as {@code UserResponses}.
 */
public class IncomeResponses
{
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details
    {
        private final Long id;
        private final BigDecimal amount;
        private final LocalDate date;
        private final String source;
        private final String description;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }
}
