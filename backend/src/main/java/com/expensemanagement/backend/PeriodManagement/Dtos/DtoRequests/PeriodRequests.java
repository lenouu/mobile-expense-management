package com.expensemanagement.backend.PeriodManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Incoming payloads for financial periods. Same shape and style as {@code UserRequests}.
 */
public class PeriodRequests
{
    /**
     * Body of `PUT /periods/settings` - the user choosing how long their periods are (US27).
     *
     * <p>Only the coarse rules are checked here. Whether a length is at least one week and at most
     * a year depends on the unit, and the service owns that rule together with its exact message.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeLength {

        /** How many {@code unit}s one period lasts, e.g. 2 WEEKS. */
        @NotNull(message = "A length count is required")
        @Min(value = 1, message = "A financial period must last at least one day")
        private Integer count;

        @NotNull(message = "A period unit is required (DAYS, WEEKS or MONTHS)")
        private PeriodUnit unit;
    }
}
