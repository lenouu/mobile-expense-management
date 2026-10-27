package com.expensemanagement.backend.PeriodManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.expensemanagement.backend.PeriodManagement.Calendar.PeriodCalendar;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;

/**
 * S4-TECH-1: the period date arithmetic on its own - no Spring, no database.
 */
class PeriodCalendarTests {

	@Test
	void weeklyPeriodsFollowEachOtherWithoutGapsOrOverlap() {
		LocalDate anchor = LocalDate.of(2026, 10, 5);

		assertThat(PeriodCalendar.startOf(anchor, 2, PeriodUnit.WEEKS, 0)).isEqualTo("2026-10-05");
		assertThat(PeriodCalendar.endOf(anchor, 2, PeriodUnit.WEEKS, 0)).isEqualTo("2026-10-18");
		assertThat(PeriodCalendar.startOf(anchor, 2, PeriodUnit.WEEKS, 1)).isEqualTo("2026-10-19");
		assertThat(PeriodCalendar.lengthInDays(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-18")))
			.isEqualTo(14);
	}

	@Test
	void monthlyPeriodsDoNotDriftAfterAShortMonth() {
		LocalDate anchor = LocalDate.of(2026, 1, 31);

		assertThat(PeriodCalendar.endOf(anchor, 1, PeriodUnit.MONTHS, 0)).isEqualTo("2026-02-27");
		assertThat(PeriodCalendar.startOf(anchor, 1, PeriodUnit.MONTHS, 1)).isEqualTo("2026-02-28");
		// Back to the 31st, not stuck on the 28th.
		assertThat(PeriodCalendar.startOf(anchor, 1, PeriodUnit.MONTHS, 2)).isEqualTo("2026-03-31");
	}

	@Test
	void leapYearsAreHandled() {
		LocalDate anchor = LocalDate.of(2028, 1, 31);

		assertThat(PeriodCalendar.startOf(anchor, 1, PeriodUnit.MONTHS, 1)).isEqualTo("2028-02-29");
		assertThat(PeriodCalendar.endOf(anchor, 1, PeriodUnit.MONTHS, 0)).isEqualTo("2028-02-28");
	}

	@Test
	void indexOfFindsThePeriodAtEveryBoundary() {
		LocalDate anchor = LocalDate.of(2026, 1, 31);

		assertThat(PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 1, 31))).isZero();
		assertThat(PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 2, 27))).isZero();
		assertThat(PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 2, 28))).isEqualTo(1);
		assertThat(PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 3, 30))).isEqualTo(1);
		assertThat(PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 3, 31))).isEqualTo(2);
		assertThat(PeriodCalendar.indexOf(anchor, 10, PeriodUnit.DAYS, LocalDate.of(2026, 2, 10))).isEqualTo(1);

		assertThatThrownBy(() -> PeriodCalendar.indexOf(anchor, 1, PeriodUnit.MONTHS, LocalDate.of(2026, 1, 30)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theMinimumIsOneWeek() {
		assertThat(PeriodCalendar.isAtLeastOneWeek(6, PeriodUnit.DAYS)).isFalse();
		assertThat(PeriodCalendar.isAtLeastOneWeek(7, PeriodUnit.DAYS)).isTrue();
		assertThat(PeriodCalendar.isAtLeastOneWeek(1, PeriodUnit.WEEKS)).isTrue();
		assertThat(PeriodCalendar.isAtLeastOneWeek(1, PeriodUnit.MONTHS)).isTrue();
		assertThat(PeriodCalendar.isAtLeastOneWeek(0, PeriodUnit.WEEKS)).isFalse();
		assertThat(PeriodCalendar.isAtLeastOneWeek(-1, PeriodUnit.MONTHS)).isFalse();
	}

	@Test
	void theOpeningWindowIsRoundedUpToWholeDays() {
		LocalDate start = LocalDate.of(2026, 10, 1);

		// 6% of 30 days = 1.8 -> 2 days: the 1st and the 2nd.
		assertThat(PeriodCalendar.openingWindowEnd(start, LocalDate.of(2026, 10, 30), 6)).isEqualTo("2026-10-02");
		// 6% of 7 days = 0.42 -> still one full day.
		assertThat(PeriodCalendar.openingWindowEnd(start, LocalDate.of(2026, 10, 7), 6)).isEqualTo("2026-10-01");
		// 6% of 365 days = 21.9 -> 22 days.
		assertThat(PeriodCalendar.openingWindowEnd(start, LocalDate.of(2027, 9, 30), 6)).isEqualTo("2026-10-22");

		assertThatThrownBy(() -> PeriodCalendar.openingWindowEnd(start, start.plusDays(6), 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
