package com.expensemanagement.backend.AnalyticsManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.expensemanagement.backend.AnalyticsManagement.Dtos.AnalyticsResponses;
import com.expensemanagement.backend.AnalyticsManagement.Services.AnalyticsServiceImpl;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeRepository;
import com.expensemanagement.backend.IncomeManagement.Repositories.IncomeSourceRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * US50 AC2 without a database: the dashboard's budget section shows the budget running today -
 * ACTIVE or FIXED - and is left out when there is none. Also the window checks on the endpoint.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsBudgetSectionTests {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final LocalDate TODAY = LocalDate.now();

	@Mock
	private ExpenseRepository expenseRepository;

	@Mock
	private IncomeRepository incomeRepository;

	@Mock
	private IncomeSourceRepository incomeSourceRepository;

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private BudgetRepository budgetRepository;

	@Mock
	private UserRepository userRepository;

	private AnalyticsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AnalyticsServiceImpl(expenseRepository, incomeRepository, incomeSourceRepository,
				userCategoryRepository, budgetRepository, userRepository);

		User user = new User();
		user.setId(USER_ID);
		user.setBaseCurrency("XAF");
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

		// An empty window: no income, no spending. Built first, then stubbed, as Mockito requires.
		ExpenseRepository.Totals nothing = totals(0);
		when(expenseRepository.totalsMatching(eq(USER_ID), any(), any(), any(), any())).thenReturn(nothing);
		when(incomeRepository.sumByUserIdAndDateBetween(eq(USER_ID), any(), any())).thenReturn(BigDecimal.ZERO);
	}

	@Test
	void aRunningBudgetThatCanStillBeEditedIsShown() {
		Budget running = budget("Running", BudgetStatus.ACTIVE, -2, 27, 100_000, 30_000);
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of(running));

		AnalyticsResponses.BudgetSnapshot shown = summary().getBudget();

		assertThat(shown).isNotNull();
		assertThat(shown.getId()).isEqualTo(running.getId());
		assertThat(shown.getPlanned().getAmount()).isEqualByComparingTo("100000");
		assertThat(shown.getSpent().getAmount()).isEqualByComparingTo("30000");
		assertThat(shown.getRemaining().getAmount()).isEqualByComparingTo("70000");
		assertThat(shown.getDaysLeft()).isEqualTo(27);
	}

	@Test
	void aRunningBudgetPastItsEditWindowIsStillShown() {
		// FIXED is how a budget spends most of its life: running, but no longer editable.
		Budget fixed = budget("Fixed", BudgetStatus.FIXED, -10, 19, 50_000, 0);
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of(fixed));

		AnalyticsResponses.BudgetSnapshot shown = summary().getBudget();

		assertThat(shown).isNotNull();
		assertThat(shown.getId()).isEqualTo(fixed.getId());
		assertThat(shown.getStatus()).isEqualTo("FIXED");
	}

	@Test
	void anUpcomingBudgetIsNotTheBudgetInForceEvenWhenItIsNewer() {
		// Newest first, as the repository returns them: the upcoming plan comes before the running one.
		Budget upcoming = budget("Next month", BudgetStatus.UPCOMING, 30, 59, 80_000, 0);
		Budget running = budget("This month", BudgetStatus.FIXED, -5, 24, 60_000, 0);
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of(upcoming, running));

		assertThat(summary().getBudget().getId()).isEqualTo(running.getId());
	}

	@Test
	void withOnlyAnUpcomingBudgetTheSectionIsLeftOut() {
		Budget upcoming = budget("Next month", BudgetStatus.UPCOMING, 30, 59, 80_000, 0);
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of(upcoming));

		assertThat(summary().getBudget()).isNull();
	}

	@Test
	void withNoBudgetTheSectionIsLeftOut() {
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of());

		assertThat(summary().getBudget()).isNull();
	}

	@Test
	void aBudgetWhoseWindowHasEndedIsNotShownEvenBeforeItsStatusIsRefreshed() {
		// The status is refreshed when the budget itself is read, so it can still say ACTIVE.
		Budget ended = budget("Last month", BudgetStatus.ACTIVE, -40, -11, 70_000, 0);
		Budget closed = budget("Older", BudgetStatus.CLOSED, -80, -41, 70_000, 0);
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of(ended, closed));

		assertThat(summary().getBudget()).isNull();
	}

	@Test
	void aReversedWindowIsRefused() {
		assertThatThrownBy(() -> service.summary(USER_ID, TODAY, TODAY.minusDays(1)))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("after the end date");
	}

	@Test
	void aWindowOfAYearIsAcceptedButLongerIsRefused() {
		when(budgetRepository.findAllByUserIdNewestFirst(USER_ID)).thenReturn(List.of());

		assertThat(service.summary(USER_ID, TODAY.minusDays(365), TODAY).getDaily()).hasSize(366);

		assertThatThrownBy(() -> service.summary(USER_ID, TODAY.minusDays(366), TODAY))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("at most 366 days");
		assertThatThrownBy(() -> service.summary(USER_ID, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 12, 31)))
			.isInstanceOf(InvalidRequestException.class);
	}

	private AnalyticsResponses.Summary summary() {
		return service.summary(USER_ID, TODAY.minusDays(6), TODAY);
	}

	/** A budget running from {@code startIn} to {@code endIn} days from today, with that much spent on it. */
	private Budget budget(String name, BudgetStatus status, int startIn, int endIn, long planned, long spent) {
		Budget budget = new Budget();
		budget.setId(UUID.randomUUID());
		budget.setName(name);
		budget.setStatus(status);
		budget.setStartDate(TODAY.plusDays(startIn));
		budget.setEndDate(TODAY.plusDays(endIn));
		budget.setOverallAmount(BigDecimal.valueOf(planned));

		ExpenseRepository.Totals spentTotals = totals(spent);
		when(expenseRepository.totalsByBudget(USER_ID, budget.getId())).thenReturn(spentTotals);
		return budget;
	}

	private static ExpenseRepository.Totals totals(long total) {
		ExpenseRepository.Totals totals = Mockito.mock(ExpenseRepository.Totals.class);
		when(totals.getTotal()).thenReturn(BigDecimal.valueOf(total));
		when(totals.getCount()).thenReturn(0L);
		return totals;
	}

}
