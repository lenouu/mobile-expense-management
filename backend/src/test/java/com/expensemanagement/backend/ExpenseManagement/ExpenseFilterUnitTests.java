package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.ExpenseManagement.Controllers.ExpenseController;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements.ExpenseServiceImpl;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * US25: Filter expenses by category and date unit tests.
 *
 * Covers:
 * - AC 1: Filter by category only.
 * - AC 2: Filter by both category and date range combined.
 * - AC 3: No matching expenses returns clear empty result without error.
 * - Safe defaults: Date range only, open-ended range, inclusive bounds, invalid date range (from > to),
 *   non-positive category ID, and strict user isolation.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseFilterUnitTests {

	@Mock
	private ExpenseRepository expenseRepository;

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FinancialPeriodService periodService;

	@Mock
	private Jwt jwt;

	private ExpenseServiceImpl expenseService;
	private ExpenseController expenseController;

	private static final Long USER_ID = 42L;
	private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
	private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository, periodService);
		expenseController = new ExpenseController(expenseService);
	}

	@Test
	@DisplayName("AC 1: When user filters by category only, only expenses in that category are returned")
	void listExpenses_whenFilteredByCategoryOnly_returnsCategoryExpenses() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		Long categoryId = 10L;
		UserCategory category = new UserCategory();
		category.setId(categoryId);
		category.setName("Groceries");

		Expense expense = new Expense();
		expense.setId(1L);
		expense.setAmount(new BigDecimal("35.00"));
		expense.setDate(LocalDate.of(2026, 11, 2));
		expense.setCategory(category);
		expense.setDescription("Supermarket");
		expense.setCreatedAt(LocalDateTime.now());
		expense.setUpdatedAt(LocalDateTime.now());

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "date", "createdAt", "id"));
		when(expenseRepository.findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(EARLIEST), eq(LATEST), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(expense), pageable, 1));

		PagedModel<ExpenseResponses.Details> result = expenseController.list(jwt, null, null, categoryId, pageable);

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).getCategory().getId()).isEqualTo(categoryId);
		verify(expenseRepository).findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(EARLIEST), eq(LATEST), eq(pageable));
	}

	@Test
	@DisplayName("AC 2: When user filters by both category and date range, both conditions apply together")
	void listExpenses_whenFilteredByCategoryAndDateRange_appliesBothConditions() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		Long categoryId = 15L;
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		Pageable pageable = PageRequest.of(0, 20);
		when(expenseRepository.findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(from), eq(to), eq(pageable)))
				.thenReturn(Page.empty(pageable));

		PagedModel<ExpenseResponses.Details> result = expenseController.list(jwt, from, to, categoryId, pageable);

		assertThat(result.getContent()).isEmpty();
		verify(expenseRepository).findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(from), eq(to), eq(pageable));
	}

	@Test
	@DisplayName("AC 3: When no expenses match the filters, returns an empty list without error")
	void listExpenses_whenNoMatches_returnsEmptyListWithoutError() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		Long categoryId = 99L;
		Pageable pageable = PageRequest.of(0, 20);
		when(expenseRepository.findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(EARLIEST), eq(LATEST), eq(pageable)))
				.thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

		PagedModel<ExpenseResponses.Details> result = expenseController.list(jwt, null, null, categoryId, pageable);

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getMetadata()).isNotNull();
		assertThat(result.getMetadata().totalElements()).isEqualTo(0L);
	}

	@Test
	@DisplayName("Safe default: Filter by date range only delegates to date-between query")
	void listExpenses_whenFilteredByDateRangeOnly_delegatesToDateBetweenQuery() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 10, 31);
		Pageable pageable = PageRequest.of(0, 20);

		when(expenseRepository.findAllByUserIdAndDateBetween(eq(USER_ID), eq(from), eq(to), eq(pageable)))
				.thenReturn(Page.empty(pageable));

		PagedModel<ExpenseResponses.Details> result = expenseController.list(jwt, from, to, null, pageable);

		assertThat(result.getContent()).isEmpty();
		verify(expenseRepository).findAllByUserIdAndDateBetween(eq(USER_ID), eq(from), eq(to), eq(pageable));
	}

	@Test
	@DisplayName("Safe default: Open-ended date range (from only or to only) fills the missing bound")
	void listExpenses_withOpenEndedDateRange_fillsMissingBound() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		LocalDate from = LocalDate.of(2026, 10, 1);
		Pageable pageable = PageRequest.of(0, 20);

		when(expenseRepository.findAllByUserIdAndDateBetween(eq(USER_ID), eq(from), eq(LATEST), eq(pageable)))
				.thenReturn(Page.empty(pageable));

		expenseController.list(jwt, from, null, null, pageable);

		verify(expenseRepository).findAllByUserIdAndDateBetween(eq(USER_ID), eq(from), eq(LATEST), eq(pageable));
	}

	@Test
	@DisplayName("Safe default: Start date after end date throws InvalidRequestException (400)")
	void listExpenses_whenFromIsAfterTo_throwsInvalidRequestException() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		LocalDate from = LocalDate.of(2026, 11, 10);
		LocalDate to = LocalDate.of(2026, 11, 1);
		Pageable pageable = PageRequest.of(0, 20);

		assertThatThrownBy(() -> expenseController.list(jwt, from, to, null, pageable))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("'from' must not be after 'to'");
	}

	@Test
	@DisplayName("Safe default: Non-positive category ID throws InvalidRequestException (400)")
	void listExpenses_whenCategoryIdIsNonPositive_throwsInvalidRequestException() {
		Pageable pageable = PageRequest.of(0, 20);

		assertThatThrownBy(() -> expenseController.list(jwt, null, null, 0L, pageable))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Category ID must be a positive number");

		assertThatThrownBy(() -> expenseController.list(jwt, null, null, -1L, pageable))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Category ID must be a positive number");
	}

	@Test
	@DisplayName("Safe default: Filter is strictly scoped to authenticated user ID")
	void listExpenses_scopesToAuthenticatedUserOnly() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		Long categoryId = 5L;
		Pageable pageable = PageRequest.of(0, 20);
		when(expenseRepository.findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(EARLIEST), eq(LATEST), eq(pageable)))
				.thenReturn(Page.empty(pageable));

		expenseController.list(jwt, null, null, categoryId, pageable);

		verify(expenseRepository).findAllByUserIdAndCategoryIdAndDateBetween(eq(USER_ID), eq(categoryId), eq(EARLIEST), eq(LATEST), eq(pageable));
	}
}
