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
     * <p>Only the coarse bounds are checked here: a count has to be a positive whole number. The
     * rule that actually matters is unit-dependent - {@code count × unit.minimumDays} must be at
     * least seven days, and at most one year - so it lives in the service, together with the
     * message that names the real limit. A {@code @Min} on the count cannot express it.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeLength {

        /** How many {@code unit}s one period lasts, e.g. 2 WEEKS. */
        @NotNull(message = "A length count is required")
        @Min(value = 1, message = "A length must be at least 1")
        private Integer count;

        @NotNull(message = "A period unit is required (DAYS, WEEKS or MONTHS)")
        private PeriodUnit unit;
    }
}
