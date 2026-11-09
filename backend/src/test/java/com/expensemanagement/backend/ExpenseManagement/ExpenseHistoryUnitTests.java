package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.ExpenseManagement.Controllers.ExpenseController;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Mappers.ExpenseMappers;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements.ExpenseServiceImpl;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.PageResponse;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * US21: View expense history unit tests.
 *
 * Covers:
 * - AC 1: Expenses are listed with amount, category, date, and description, most recent first.
 * - AC 2: Empty state returns an empty list without error when no expenses are recorded.
 * - Safe default: Scoped strictly to the authenticated user ID.
 * - Safe default: Ordering by expense date descending, with createdAt descending as tiebreaker.
 * - Safe default: Missing/null category maps safely without throwing NullPointerException.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseHistoryUnitTests {

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

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
	private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID EXPENSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository, periodService);
		expenseController = new ExpenseController(expenseService);
	}

	@Test
	@DisplayName("AC 1: When user has recorded expenses, they are listed with amount, category, date, description, and id")
	void listExpenses_whenExpensesExist_returnsAllRequiredFields() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());

		User user = new User();
		user.setId(USER_ID);

		UserCategory category = new UserCategory();
		category.setId(CATEGORY_ID);
		category.setName("Groceries");
		category.setIcon("cart");
		category.setColor("#01916D");

		Expense expense = new Expense();
		expense.setId(EXPENSE_ID);
		expense.setUser(user);
		expense.setCategory(category);
		expense.setAmount(new BigDecimal("45.50"));
		expense.setCurrency("XAF");
		expense.setDate(LocalDate.of(2026, 11, 5));
		expense.setDescription("Weekly shopping");
		expense.setCreatedAt(LocalDateTime.of(2026, 11, 5, 10, 30));
		expense.setUpdatedAt(LocalDateTime.of(2026, 11, 5, 10, 30));

		Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "date", "createdAt", "id"));
		when(expenseRepository.search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), eq(pageable)))
				.thenReturn(new PageImpl<>(List.of(expense), pageable, 1));

		PageResponse<ExpenseResponses.Details> result = expenseController.list(jwt, null, null, null, null, null, pageable);

		assertThat(result.data()).hasSize(1);
		ExpenseResponses.Details details = result.data().get(0);
		assertThat(details.getId()).isEqualTo(EXPENSE_ID);
		assertThat(details.getAmount().amount()).isEqualByComparingTo(new BigDecimal("45.50"));
		assertThat(details.getAmount().currency()).isEqualTo("XAF");
		assertThat(details.getDate()).isEqualTo(LocalDate.of(2026, 11, 5));
		assertThat(details.getDescription()).isEqualTo("Weekly shopping");
		assertThat(details.getCategory()).isNotNull();
		assertThat(details.getCategory().getId()).isEqualTo(CATEGORY_ID);
		assertThat(details.getCategory().getName()).isEqualTo("Groceries");
		assertThat(details.getCategory().getIcon()).isEqualTo("cart");
		assertThat(details.getCategory().getColor()).isEqualTo("#01916D");
	}

	@Test
	@DisplayName("AC 2: When no expenses have been recorded, returns an empty list rather than an error")
	void listExpenses_whenNoExpensesExist_returnsEmptyListWithoutError() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());
		Pageable pageable = PageRequest.of(0, 20);

		when(expenseRepository.search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), eq(pageable)))
				.thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

		PageResponse<ExpenseResponses.Details> result = expenseController.list(jwt, null, null, null, null, null, pageable);

		assertThat(result.data()).isEmpty();
		assertThat(result.totalElements()).isEqualTo(0L);
	}

	@Test
	@DisplayName("Safe default: Request is strictly scoped to the authenticated user ID from JWT sub claim")
	void listExpenses_scopesToAuthenticatedUserOnly() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());
		Pageable pageable = PageRequest.of(0, 20);

		when(expenseRepository.search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), eq(pageable)))
				.thenReturn(Page.empty(pageable));

		expenseController.list(jwt, null, null, null, null, null, pageable);

		verify(expenseRepository).search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), eq(pageable));
	}

	@Test
	@DisplayName("Safe default: Missing/null category on expense maps safely without throwing NullPointerException")
	void toDetails_whenCategoryIsNull_rendersSafelyWithoutNpe() {
		Expense expense = new Expense();
		expense.setId(EXPENSE_ID);
		expense.setAmount(new BigDecimal("19.99"));
		expense.setCurrency("XAF");
		expense.setDate(LocalDate.of(2026, 11, 1));
		expense.setDescription("No category expense");
		expense.setCategory(null); // category missing or deleted

		ExpenseResponses.Details details = ExpenseMappers.toDetails(expense);

		assertThat(details).isNotNull();
		assertThat(details.getId()).isEqualTo(EXPENSE_ID);
		assertThat(details.getAmount().amount()).isEqualByComparingTo(new BigDecimal("19.99"));
		assertThat(details.getAmount().currency()).isEqualTo("XAF");
		assertThat(details.getDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(details.getDescription()).isEqualTo("No category expense");
		assertThat(details.getCategory()).isNull();
	}

	@Test
	@DisplayName("Safe default: Ordering sorts by date descending, createdAt descending, and id descending")
	void listExpenses_passesExpectedSortPropertiesToRepository() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());

		Pageable pageableWithSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "date", "createdAt", "id"));
		when(expenseRepository.search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), eq(pageableWithSort)))
				.thenReturn(Page.empty(pageableWithSort));

		expenseController.list(jwt, null, null, null, null, null, pageableWithSort);

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(expenseRepository).search(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31)), isNull(), isNull(), captor.capture());

		Sort sort = captor.getValue().getSort();
		assertThat(sort.getOrderFor("date")).isNotNull();
		assertThat(sort.getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(sort.getOrderFor("createdAt")).isNotNull();
		assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
		assertThat(sort.getOrderFor("id")).isNotNull();
		assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
	}
}
