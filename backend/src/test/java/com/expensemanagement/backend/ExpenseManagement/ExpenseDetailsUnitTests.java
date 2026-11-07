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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
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
 * Acceptance Criteria & Defaults Covered:
 * - AC 1: User can view details of an expense they own with all fields: amount, date, category, description, createdAt, updatedAt.
 * - AC 2: Requesting non-existent expense returns 404 (ResourceNotFoundException).
 * - AC 3: Requesting another user's expense returns 404 and does not leak data.
 * - AC 4: Invalid ID format (e.g. non-positive ID <= 0) returns 400 (InvalidRequestException).
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

	private static final Long USER_ID = 42L;
	private static final Long OTHER_USER_ID = 999L;

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository, periodService);
		expenseController = new ExpenseController(expenseService);
	}

	@Test
	@DisplayName("AC 1: Successfully returns all fields of an expense owned by the authenticated user")
	void getExpense_whenExistsAndOwned_returnsAllDetails() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		User user = new User();
		user.setId(USER_ID);

		UserCategory category = new UserCategory();
		category.setId(10L);
		category.setName("Groceries");
		category.setIcon("cart");
		category.setColor("#01916D");

		Expense expense = new Expense();
		expense.setId(100L);
		expense.setUser(user);
		expense.setCategory(category);
		expense.setAmount(new BigDecimal("75.25"));
		expense.setDate(LocalDate.of(2026, 11, 7));
		expense.setDescription("Supermarket bulk run");
		expense.setCreatedAt(LocalDateTime.of(2026, 11, 7, 9, 15, 0));
		expense.setUpdatedAt(LocalDateTime.of(2026, 11, 7, 9, 30, 0));

		when(expenseRepository.findByIdAndUserId(eq(100L), eq(USER_ID))).thenReturn(Optional.of(expense));

		ExpenseResponses.Details details = expenseController.get(jwt, 100L);

		assertThat(details).isNotNull();
		assertThat(details.getId()).isEqualTo(100L);
		assertThat(details.getAmount()).isEqualTo(new BigDecimal("75.25"));
		assertThat(details.getDate()).isEqualTo(LocalDate.of(2026, 11, 7));
		assertThat(details.getDescription()).isEqualTo("Supermarket bulk run");
		assertThat(details.getCategory()).isNotNull();
		assertThat(details.getCategory().getId()).isEqualTo(10L);
		assertThat(details.getCategory().getName()).isEqualTo("Groceries");
		assertThat(details.getCategory().getIcon()).isEqualTo("cart");
		assertThat(details.getCategory().getColor()).isEqualTo("#01916D");
		assertThat(details.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 15, 0));
		assertThat(details.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 7, 9, 30, 0));
	}

	@Test
	@DisplayName("AC 2: Requesting a non-existent expense throws ResourceNotFoundException (404)")
	void getExpense_whenNonExistent_throwsResourceNotFoundException() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		when(expenseRepository.findByIdAndUserId(eq(9999L), eq(USER_ID))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> expenseController.get(jwt, 9999L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No expense with id 9999");
	}

	@Test
	@DisplayName("AC 3: Requesting another user's expense returns 404 and does not leak data")
	void getExpense_whenOwnedByAnotherUser_throwsResourceNotFoundException() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		// The repository query findByIdAndUserId scopes to the caller's USER_ID
		when(expenseRepository.findByIdAndUserId(eq(200L), eq(USER_ID))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> expenseController.get(jwt, 200L))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessageContaining("No expense with id 200");

		verify(expenseRepository).findByIdAndUserId(eq(200L), eq(USER_ID));
	}

	@Test
	@DisplayName("AC 4: Non-positive ID values (<= 0) throw InvalidRequestException (400)")
	void getExpense_whenIdIsZeroOrNegative_throwsInvalidRequestException() {
		assertThatThrownBy(() -> expenseController.get(jwt, 0L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Expense id must be a positive number");

		assertThatThrownBy(() -> expenseController.get(jwt, -5L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Expense id must be a positive number");
	}

	@Test
	@DisplayName("AC 6: Missing or deleted category maps safely to null category without throwing NullPointerException")
	void getExpense_whenCategoryIsNull_rendersSafelyWithoutNpe() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		Expense expense = new Expense();
		expense.setId(101L);
		expense.setAmount(new BigDecimal("12.00"));
		expense.setDate(LocalDate.of(2026, 11, 6));
		expense.setDescription("Coffee without category");
		expense.setCategory(null); // category deleted or not present
		expense.setCreatedAt(LocalDateTime.now());
		expense.setUpdatedAt(LocalDateTime.now());

		when(expenseRepository.findByIdAndUserId(eq(101L), eq(USER_ID))).thenReturn(Optional.of(expense));

		ExpenseResponses.Details details = expenseController.get(jwt, 101L);

		assertThat(details).isNotNull();
		assertThat(details.getId()).isEqualTo(101L);
		assertThat(details.getCategory()).isNull();
		assertThat(details.getAmount()).isEqualTo(new BigDecimal("12.00"));
		assertThat(details.getDescription()).isEqualTo("Coffee without category");
	}
}
