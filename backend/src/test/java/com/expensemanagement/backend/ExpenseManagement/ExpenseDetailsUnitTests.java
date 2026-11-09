package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.ExpenseManagement.Controllers.ExpenseController;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Mappers.ExpenseMappers;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements.ExpenseServiceImpl;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * US22: View expense details unit tests.
 *
 * Acceptance Criteria Covered:
 * - AC 1: User can view details of an expense they own with all fields: amount, date, category, description, createdAt, updatedAt.
 * - AC 2: Requesting non-existent expense throws ResourceNotFoundException (404).
 * - AC 3: Requesting another user's expense returns 404 and does not leak data.
 * - AC 6: Missing/deleted category renders safely without throwing NullPointerException.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseDetailsUnitTests {

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
	private static final UUID EXPENSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository, periodService);
		expenseController = new ExpenseController(expenseService);
	}

	@Test
	@DisplayName("AC 1: Successfully returns all fields of an expense owned by the authenticated user")
	void getExpense_whenExistsAndOwned_returnsAllDetails() {
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
		expense.setAmount(new BigDecimal("75.25"));
		expense.setCurrency("XAF");
		expense.setDate(LocalDate.of(2026, 11, 7));
		expense.setDescription("Supermarket bulk run");
		expense.setCreatedAt(LocalDateTime.of(2026, 11, 7, 9, 15, 0));
		expense.setUpdatedAt(LocalDateTime.of(2026, 11, 7, 9, 30, 0));

		when(expenseRepository.findByIdAndUserId(eq(EXPENSE_ID), eq(USER_ID))).thenReturn(Optional.of(expense));

		ExpenseResponses.Details details = expenseController.get(jwt, EXPENSE_ID);

		assertThat(details).isNotNull();
		assertThat(details.getId()).isEqualTo(EXPENSE_ID);
		assertThat(details.getAmount().amount()).isEqualByComparingTo(new BigDecimal("75.25"));
		assertThat(details.getAmount().currency()).isEqualTo("XAF");
		assertThat(details.getDate()).isEqualTo(LocalDate.of(2026, 11, 7));
		assertThat(details.getDescription()).isEqualTo("Supermarket bulk run");
		assertThat(details.getCategory()).isNotNull();
		assertThat(details.getCategory().getId()).isEqualTo(CATEGORY_ID);
		assertThat(details.getCategory().getName()).isEqualTo("Groceries");
		assertThat(details.getCategory().getIcon()).isEqualTo("cart");
		assertThat(details.getCategory().getColor()).isEqualTo("#01916D");
		assertThat(details.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 15, 0));
		assertThat(details.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 30, 0));
	}

	@Test
	@DisplayName("AC 2: Requesting a non-existent expense throws ResourceNotFoundException (404)")
	void getExpense_whenNonExistent_throwsResourceNotFoundException() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());
		UUID nonExistentId = UUID.randomUUID();
		when(expenseRepository.findByIdAndUserId(eq(nonExistentId), eq(USER_ID))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> expenseController.get(jwt, nonExistentId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No expense with id " + nonExistentId);
	}

	@Test
	@DisplayName("AC 3: Requesting another user's expense returns 404 and does not leak data")
	void getExpense_whenOwnedByAnotherUser_throwsResourceNotFoundException() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());
		UUID otherUserExpenseId = UUID.randomUUID();
		// The repository query findByIdAndUserId scopes to the caller's USER_ID
		when(expenseRepository.findByIdAndUserId(eq(otherUserExpenseId), eq(USER_ID))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> expenseController.get(jwt, otherUserExpenseId))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No expense with id " + otherUserExpenseId);

		verify(expenseRepository).findByIdAndUserId(eq(otherUserExpenseId), eq(USER_ID));
	}

	@Test
	@DisplayName("AC 6: Missing or deleted category maps safely to null category without throwing NullPointerException")
	void getExpense_whenCategoryIsNull_rendersSafelyWithoutNpe() {
		when(jwt.getSubject()).thenReturn(USER_ID.toString());

		Expense expense = new Expense();
		expense.setId(EXPENSE_ID);
		expense.setAmount(new BigDecimal("12.00"));
		expense.setCurrency("XAF");
		expense.setDate(LocalDate.of(2026, 11, 6));
		expense.setDescription("Coffee without category");
		expense.setCategory(null); // category deleted or not present
		expense.setCreatedAt(LocalDateTime.now());
		expense.setUpdatedAt(LocalDateTime.now());

		when(expenseRepository.findByIdAndUserId(eq(EXPENSE_ID), eq(USER_ID))).thenReturn(Optional.of(expense));

		ExpenseResponses.Details details = expenseController.get(jwt, EXPENSE_ID);

		assertThat(details).isNotNull();
		assertThat(details.getId()).isEqualTo(EXPENSE_ID);
		assertThat(details.getCategory()).isNull();
		assertThat(details.getAmount().amount()).isEqualByComparingTo(new BigDecimal("12.00"));
		assertThat(details.getAmount().currency()).isEqualTo("XAF");
		assertThat(details.getDescription()).isEqualTo("Coffee without category");
	}
}
