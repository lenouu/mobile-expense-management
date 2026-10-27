package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.ExpenseManagement.Controllers.ExpenseController;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;

@ExtendWith(MockitoExtension.class)
class ExpenseAggregationControllerTests {

	@Mock
	private ExpenseService expenseService;

	@Mock
	private Jwt jwt;

	private ExpenseController expenseController;

	private static final Long USER_ID = 100L;

	@BeforeEach
	void setUp() {
		expenseController = new ExpenseController(expenseService);
	}

	@Test
	@DisplayName("GET /api/expenses/spending delegates to service with caller's userId and parameters")
	void spendingEndpointDelegatesToServiceWithCallerUserId() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		ExpenseResponses.SpendingSummary expectedSummary = ExpenseResponses.SpendingSummary.builder()
				.from(from)
				.to(to)
				.totalSpent(new BigDecimal("750.25"))
				.categories(List.of(
						ExpenseResponses.CategorySpending.builder()
								.categoryId(1L)
								.categoryName("Food")
								.categoryIcon("food-icon")
								.categoryColor("#FF0000")
								.totalSpent(new BigDecimal("500.00"))
								.build()
				))
				.build();

		when(expenseService.getSpendingSummary(USER_ID, from, to)).thenReturn(expectedSummary);

		ExpenseResponses.SpendingSummary actual = expenseController.spending(jwt, from, to);

		assertThat(actual).isNotNull();
		assertThat(actual.getTotalSpent()).isEqualByComparingTo("750.25");
		assertThat(actual.getCategories()).hasSize(1);
		assertThat(actual.getCategories().get(0).getCategoryName()).isEqualTo("Food");
		verify(expenseService).getSpendingSummary(USER_ID, from, to);
	}

	@Test
	@DisplayName("GET /api/expenses/spending with null dates forwards nulls to service")
	void spendingEndpointWithNullDatesForwardsNulls() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		ExpenseResponses.SpendingSummary expectedSummary = ExpenseResponses.SpendingSummary.builder()
				.totalSpent(BigDecimal.ZERO)
				.categories(List.of())
				.build();

		when(expenseService.getSpendingSummary(USER_ID, null, null)).thenReturn(expectedSummary);

		ExpenseResponses.SpendingSummary actual = expenseController.spending(jwt, null, null);

		assertThat(actual.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(expenseService).getSpendingSummary(USER_ID, null, null);
	}
}
