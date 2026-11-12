package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements.BudgetServiceImpl;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface.NotificationService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * The close-and-recap flow (US33), without a database.
 *
 * <p>Two things have to hold: a budget whose window has ended closes itself on the next read, and
 * the recap notification is written exactly once - not on every read, which is what a user would
 * notice as a stream of duplicates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetCloseTests
{
	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private BudgetRepository budgetRepository;
	@Mock
	private CategoryAllocationRepository allocationRepository;
	@Mock
	private UserCategoryRepository userCategoryRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private ExpenseRepository expenseRepository;
	@Mock
	private ExpenseService expenseService;
	@Mock
	private NotificationService notificationService;

	private BudgetServiceImpl budgetService;

	@BeforeEach
	void setUp()
	{
		budgetService = new BudgetServiceImpl(budgetRepository, allocationRepository, userCategoryRepository,
				userRepository, expenseRepository, expenseService, notificationService);
	}

	/** The projection the service reads: a total and a count, nothing more. */
	private ExpenseRepository.Totals totals(long spent, long count)
	{
		ExpenseRepository.Totals totals = org.mockito.Mockito.mock(ExpenseRepository.Totals.class);
		when(totals.getTotal()).thenReturn(BigDecimal.valueOf(spent));
		when(totals.getCount()).thenReturn(count);
		return totals;
	}
	/** A budget whose window ended a week ago, still marked ACTIVE as it would be before a read. */
	private Budget endedBudget(String name, int planned, int spent)
	{
		User user = new User();
		user.setId(USER_ID);

		Budget budget = new Budget();
		budget.setId(UUID.randomUUID());
		budget.setUser(user);
		budget.setName(name);
		budget.setStartDate(LocalDate.now().minusDays(20));
		budget.setEndDate(LocalDate.now().minusDays(6));
		budget.setOverallAmount(BigDecimal.valueOf(planned));
		budget.setStatus(BudgetStatus.ACTIVE);

		// Built before the when() below: stubbing a mock inside another stubbing is refused by Mockito.
		ExpenseRepository.Totals spentTotals = totals(spent, 3);

		when(budgetRepository.findByIdAndUserId(budget.getId(), USER_ID)).thenReturn(Optional.of(budget));
		when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(call -> call.getArgument(0));
		when(allocationRepository.findAllByBudgetId(budget.getId())).thenReturn(List.of());
		when(expenseRepository.totalsByBudget(USER_ID, budget.getId()))
				.thenReturn(spentTotals);
		when(expenseRepository.sumByCategoryForBudget(USER_ID, budget.getId())).thenReturn(List.of());

		return budget;
	}

	@Test
	@DisplayName("reading an ended budget closes it and writes one recap")
	void readingAnEndedBudgetClosesIt()
	{
		Budget budget = endedBudget("October plan", 100_000, 80_000);

		var details = budgetService.getBudget(USER_ID, budget.getId());

		assertThat(details.getStatus()).isEqualTo(BudgetStatus.CLOSED);
		assertThat(details.getVerdict()).isEqualTo("SUCCESS");
		assertThat(budget.getClosedAt()).isNotNull();

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(notificationService, times(1))
				.notify(eq(USER_ID), eq(NotificationKind.BUDGET_PERIOD_CLOSED), anyString(), body.capture());

		assertThat(body.getValue()).contains("You planned").contains("You stayed within it");
	}

	@Test
	@DisplayName("overspending reads as a loss in the recap")
	void overspendingSaysSo()
	{
		Budget budget = endedBudget(null, 100_000, 130_000);

		var details = budgetService.getBudget(USER_ID, budget.getId());

		assertThat(details.getVerdict()).isEqualTo("EXCEEDED");

		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(notificationService).notify(eq(USER_ID), eq(NotificationKind.BUDGET_PERIOD_CLOSED), anyString(),
				body.capture());
		assertThat(body.getValue()).contains("over");
	}

	@Test
	@DisplayName("reading it again does not write a second recap")
	void theRecapIsWrittenOnce()
	{
		Budget budget = endedBudget("October plan", 100_000, 80_000);

		budgetService.getBudget(USER_ID, budget.getId());
		budgetService.getBudget(USER_ID, budget.getId());
		budgetService.getBudget(USER_ID, budget.getId());

		verify(notificationService, times(1))
				.notify(any(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("a budget that has not ended is not closed, and writes nothing")
	void aRunningBudgetIsLeftAlone()
	{
		User user = new User();
		user.setId(USER_ID);

		Budget budget = new Budget();
		budget.setId(UUID.randomUUID());
		budget.setUser(user);
		budget.setStartDate(LocalDate.now());
		budget.setEndDate(LocalDate.now().plusDays(6));
		budget.setOverallAmount(BigDecimal.valueOf(30_000));
		budget.setStatus(BudgetStatus.ACTIVE);
		budget.setEditsUsed(0);

		ExpenseRepository.Totals noSpending = totals(0, 0);

		when(budgetRepository.findByIdAndUserId(budget.getId(), USER_ID)).thenReturn(Optional.of(budget));
		when(allocationRepository.findAllByBudgetId(budget.getId())).thenReturn(List.of());
		when(expenseRepository.totalsByBudget(USER_ID, budget.getId()))
				.thenReturn(noSpending);
		when(expenseRepository.sumByCategoryForBudget(USER_ID, budget.getId())).thenReturn(List.of());

		var details = budgetService.getBudget(USER_ID, budget.getId());

		assertThat(details.getStatus()).isEqualTo(BudgetStatus.ACTIVE);
		assertThat(budget.getClosedAt()).isNull();
		verify(notificationService, never()).notify(any(), any(), anyString(), anyString());
	}
}
