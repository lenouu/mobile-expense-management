package com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses;

import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Outgoing payloads for financial periods (S4-TECH-1). Same shape as {@code UserResponses}.
 */
public class PeriodResponses
{
    public enum Status
    {
        PAST,
        CURRENT,
        UPCOMING
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Period
    {
        private final Long id;
        private final int sequence;
        private final LocalDate startDate;
        /** Inclusive. */
        private final LocalDate endDate;
        private final long days;
        private final int lengthCount;
        private final PeriodUnit lengthUnit;
        private final Status status;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Settings
    {
        private final int lengthCount;
        private final PeriodUnit lengthUnit;
        /** True until the user chooses a length themselves (US27). */
        private final boolean defaultLength;
        /** First day the current length applies; after a change, the start of the next period. */
        private final LocalDate effectiveFrom;
        private final int minimumDays;
    }
}
