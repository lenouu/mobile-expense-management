package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
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

import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Repositories.Projections.CategorySpendingProjection;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements.ExpenseServiceImpl;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class ExpenseAggregationUnitTests {

	@Mock
	private ExpenseRepository expenseRepository;

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private UserRepository userRepository;

	private ExpenseServiceImpl expenseService;

	private static final Long USER_ID = 42L;
	private static final Long OTHER_USER_ID = 99L;

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository);
	}

	@Test
	@DisplayName("calculateTotalSpending sums expenses correctly for the authenticated user")
	void calculateTotalSpendingReturnsSum() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(expenseRepository.sumAmountByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(new BigDecimal("1250.75"));

		BigDecimal total = expenseService.calculateTotalSpending(USER_ID, from, to);

		assertThat(total).isEqualByComparingTo("1250.75");
		verify(expenseRepository).sumAmountByUserIdAndDateBetween(USER_ID, from, to);
	}

	@Test
	@DisplayName("calculateTotalSpending returns BigDecimal.ZERO when no expenses exist (never null)")
	void calculateTotalSpendingReturnsZeroWhenEmpty() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(expenseRepository.sumAmountByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(null);

		BigDecimal total = expenseService.calculateTotalSpending(USER_ID, from, to);

		assertThat(total).isNotNull();
		assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	@DisplayName("findSpendingByCategory returns grouped spending mapped correctly")
	void findSpendingByCategoryReturnsGroupedCategories() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		CategorySpendingProjection p1 = new TestCategorySpendingProjection(1L, "Food & Groceries", "food", "#FF5733", new BigDecimal("450.00"));
		CategorySpendingProjection p2 = new TestCategorySpendingProjection(2L, "Transport", "car", "#33FF57", new BigDecimal("150.50"));

		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of(p1, p2));

		List<ExpenseResponses.CategorySpending> results = expenseService.findSpendingByCategory(USER_ID, from, to);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).getCategoryId()).isEqualTo(1L);
		assertThat(results.get(0).getCategoryName()).isEqualTo("Food & Groceries");
		assertThat(results.get(0).getCategoryIcon()).isEqualTo("food");
		assertThat(results.get(0).getCategoryColor()).isEqualTo("#FF5733");
		assertThat(results.get(0).getTotalSpent()).isEqualByComparingTo("450.00");

		assertThat(results.get(1).getCategoryId()).isEqualTo(2L);
		assertThat(results.get(1).getTotalSpent()).isEqualByComparingTo("150.50");
	}

	@Test
	@DisplayName("getSpendingSummary combines total and categories into a complete summary")
	void getSpendingSummaryCombinesTotalAndCategories() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(expenseRepository.sumAmountByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(new BigDecimal("600.50"));

		CategorySpendingProjection p1 = new TestCategorySpendingProjection(1L, "Food", "food", "#FF0000", new BigDecimal("600.50"));
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of(p1));

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(USER_ID, from, to);

		assertThat(summary.getFrom()).isEqualTo(from);
		assertThat(summary.getTo()).isEqualTo(to);
		assertThat(summary.getTotalSpent()).isEqualByComparingTo("600.50");
		assertThat(summary.getCategories()).hasSize(1);
		assertThat(summary.getCategories().get(0).getCategoryName()).isEqualTo("Food");
	}

	@Test
	@DisplayName("Invalid date range (from after to) throws InvalidRequestException")
	void invalidDateRangeThrowsException() {
		LocalDate from = LocalDate.of(2026, 10, 1);
		LocalDate to = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> expenseService.calculateTotalSpending(USER_ID, from, to))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("'from' must not be after 'to'");

		assertThatThrownBy(() -> expenseService.findSpendingByCategory(USER_ID, from, to))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("'from' must not be after 'to'");

		assertThatThrownBy(() -> expenseService.getSpendingSummary(USER_ID, from, to))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("'from' must not be after 'to'");
	}

	@Test
	@DisplayName("Omitting dates applies default wide bounds from DateRanges")
	void omittingDatesAppliesDefaultBounds() {
		when(expenseRepository.sumAmountByUserIdAndDateBetween(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31))))
			.thenReturn(new BigDecimal("200.00"));
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(eq(USER_ID), eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(9999, 12, 31))))
			.thenReturn(List.of());

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(USER_ID, null, null);

		assertThat(summary.getTotalSpent()).isEqualByComparingTo("200.00");
		assertThat(summary.getCategories()).isEmpty();
	}

	@Test
	@DisplayName("User isolation: queries are strictly filtered by authenticated userId")
	void queriesAreFilteredByAuthenticatedUserId() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		when(expenseRepository.sumAmountByUserIdAndDateBetween(OTHER_USER_ID, from, to))
			.thenReturn(new BigDecimal("50.00"));

		BigDecimal total = expenseService.calculateTotalSpending(OTHER_USER_ID, from, to);
		assertThat(total).isEqualByComparingTo("50.00");

		verify(expenseRepository).sumAmountByUserIdAndDateBetween(OTHER_USER_ID, from, to);
	}

	private record TestCategorySpendingProjection(
			Long categoryId,
			String categoryName,
			String categoryIcon,
			String categoryColor,
			BigDecimal totalSpent
	) implements CategorySpendingProjection {
		@Override public Long getCategoryId() { return categoryId; }
		@Override public String getCategoryName() { return categoryName; }
		@Override public String getCategoryIcon() { return categoryIcon; }
		@Override public String getCategoryColor() { return categoryColor; }
		@Override public BigDecimal getTotalSpent() { return totalSpent; }
	}
}
