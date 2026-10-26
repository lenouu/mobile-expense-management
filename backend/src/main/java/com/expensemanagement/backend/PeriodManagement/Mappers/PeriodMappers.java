package com.expensemanagement.backend.PeriodManagement.Mappers;

import com.expensemanagement.backend.PeriodManagement.Calendar.PeriodCalendar;
import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;

import java.time.LocalDate;

public final class PeriodMappers
{
    private PeriodMappers()
    {
        throw new AssertionError("PeriodMappers is a static utility class and cannot be instantiated");
    }

    /** {@code today} decides the status, so callers pass the same date they computed with. */
    public static PeriodResponses.Period toPeriod(FinancialPeriod period, LocalDate today)
    {
        PeriodResponses.Status status = period.getEndDate().isBefore(today)
                ? PeriodResponses.Status.PAST
                : period.getStartDate().isAfter(today) ? PeriodResponses.Status.UPCOMING : PeriodResponses.Status.CURRENT;

        return PeriodResponses.Period.builder()
                .id(period.getId())
                .sequence(period.getSequence())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .days(PeriodCalendar.lengthInDays(period.getStartDate(), period.getEndDate()))
                .lengthCount(period.getLengthCount())
                .lengthUnit(period.getLengthUnit())
                .status(status)
                .build();
    }

    public static PeriodResponses.Settings toSettings(FinancialPeriodSettings settings)
    {
        return PeriodResponses.Settings.builder()
                .lengthCount(settings.getLengthCount())
                .lengthUnit(settings.getLengthUnit())
                .defaultLength(settings.isDefaultLength())
                .effectiveFrom(settings.getAnchorDate())
                .minimumDays(PeriodCalendar.MINIMUM_DAYS)
                .build();
    }
}
