package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.expensemanagement.backend.BudgetManagement.Calendar.BudgetWindow;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;

/**
 * The editing rule, on its own (US31).
 *
 * <p>No database, no mocks: the rule is arithmetic and a clock, and this is where the examples from
 * the vision are pinned down - 6% of a week is about ten hours, 6% of a month about a day and three
 * quarters - so that changing the constants has to break a test on purpose.
 */
class BudgetWindowTests
{
	private static Budget budget(LocalDate start, long days)
	{
		Budget budget = new Budget();
		budget.setStartDate(start);
		budget.setEndDate(start.plusDays(days - 1));
		budget.setOverallAmount(BigDecimal.valueOf(100_000));
		return budget;
	}

	@Test
	@DisplayName("a week's window is just over ten hours")
	void aWeekGivesAboutTenHours()
	{
		LocalDate start = LocalDate.of(2026, 3, 2);
		LocalDateTime until = BudgetWindow.editableUntil(start, 7);

		assertThat(until).isEqualTo(start.atStartOfDay().plusMinutes(605));
		assertThat(until).isBefore(start.atTime(11, 0));
	}

	@Test
	@DisplayName("a month's window is just under two days")
	void aMonthGivesJustUnderTwoDays()
	{
		LocalDate start = LocalDate.of(2026, 3, 1);
		LocalDateTime until = BudgetWindow.editableUntil(start, 30);

		// 30 days * 6% = 1.8 days = 2592 minutes.
		assertThat(until).isEqualTo(start.atStartOfDay().plusMinutes(2_592));
		assertThat(until).isBefore(start.plusDays(2).atStartOfDay());
	}

	@Test
	@DisplayName("a longer plan gets a proportionally longer window")
	void aLongerPlanGetsALongerWindow()
	{
		LocalDate start = LocalDate.of(2026, 1, 1);

		LocalDateTime week = BudgetWindow.editableUntil(start, 7);
		LocalDateTime twoMonths = BudgetWindow.editableUntil(start, 60);

		assertThat(twoMonths).isAfter(week);
		// 60 * 6% = 3.6 days.
		assertThat(twoMonths).isEqualTo(start.atStartOfDay().plusMinutes(5_184));
	}

	@Test
	@DisplayName("the window is open right up to its last minute, and closed immediately after")
	void theWindowClosesToTheMinute()
	{
		LocalDate start = LocalDate.of(2026, 3, 2);
		Budget budget = budget(start, 7);
		LocalDateTime closesAt = BudgetWindow.editableUntil(budget);

		assertThat(BudgetWindow.isWindowOpen(budget, closesAt.minusMinutes(1))).isTrue();
		assertThat(BudgetWindow.isWindowOpen(budget, closesAt)).isFalse();
		assertThat(BudgetWindow.isWindowOpen(budget, closesAt.plusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("a plan that has not started is always editable")
	void beforeTheStartThereIsNoClock()
	{
		LocalDate start = LocalDate.of(2026, 3, 2);
		Budget budget = budget(start, 7);

		assertThat(BudgetWindow.isWindowOpen(budget, start.minusDays(30).atStartOfDay())).isTrue();
		assertThat(BudgetWindow.isWindowOpen(budget, start.atStartOfDay())).isTrue();
		assertThat(BudgetWindow.isWindowOpen(budget, start.atTime(0, 1))).isTrue();
	}

	@Test
	@DisplayName("three edits are allowed, the fourth is not")
	void theCapIsThree()
	{
		assertThat(BudgetWindow.hasEditsLeft(0)).isTrue();
		assertThat(BudgetWindow.hasEditsLeft(1)).isTrue();
		assertThat(BudgetWindow.hasEditsLeft(2)).isTrue();
		assertThat(BudgetWindow.hasEditsLeft(3)).isFalse();
		assertThat(BudgetWindow.hasEditsLeft(99)).isFalse();
	}

	@Test
	@DisplayName("the duration bounds are a week and a year")
	void theBoundsAreAWeekAndAYear()
	{
		assertThat(BudgetWindow.MINIMUM_DAYS).isEqualTo(7);
		assertThat(BudgetWindow.MAXIMUM_DAYS).isEqualTo(366);
		assertThat(BudgetWindow.EDIT_CAP).isEqualTo(3);
		assertThat(BudgetWindow.WINDOW_FRACTION).isEqualTo(0.06);
	}

	@Test
	@DisplayName("a budget's own length is what its window is made of")
	void theLengthComesFromTheBudgetItself()
	{
		Budget week = budget(LocalDate.of(2026, 5, 4), 7);
		assertThat(week.lengthInDays()).isEqualTo(7);

		// Two months of plan, not one period's worth: the window follows the dates on the record.
		Budget twoMonths = budget(LocalDate.of(2026, 5, 4), 61);
		assertThat(twoMonths.lengthInDays()).isEqualTo(61);
		assertThat(BudgetWindow.editableUntil(twoMonths))
				.isAfter(BudgetWindow.editableUntil(week));
	}

	@Test
	@DisplayName("the utility class refuses to be instantiated")
	void cannotBeInstantiated() throws Exception
	{
		var constructor = BudgetWindow.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		assertThatThrownBy(constructor::newInstance)
				.hasRootCauseInstanceOf(AssertionError.class);
	}
}
