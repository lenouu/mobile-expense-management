package com.expensemanagement.backend.ExpenseManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Repositories.ExpenseRepository;
import com.expensemanagement.backend.ExpenseManagement.Repositories.Projections.CategorySpendingProjection;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceImplements.ExpenseServiceImpl;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class ExpenseAggregationUnitTests {

	@Mock
	private ExpenseRepository expenseRepository;

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FinancialPeriodService financialPeriodService;

	@Mock
	private BudgetRepository budgetRepository;

	@Mock
	private ExpenseRepository.Totals totals;

	@Mock
	private User user;

	private ExpenseServiceImpl expenseService;

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
	private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
	private static final UUID FOOD_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID TRANSPORT_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@BeforeEach
	void setUp() {
		expenseService = new ExpenseServiceImpl(expenseRepository, userCategoryRepository, userRepository,
			financialPeriodService, budgetRepository);
	}

	@Test
	@DisplayName("calculateTotalSpending sums expenses correctly for the authenticated user")
	void calculateTotalSpendingReturnsSum() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(USER_ID, "EUR");
		when(expenseRepository.totalsBetween(USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(new BigDecimal("1250.75"));

		Money total = expenseService.calculateTotalSpending(USER_ID, from, to);

		assertMoney(total, "1250.75", "EUR");
		verify(userRepository).findById(USER_ID);
		verify(expenseRepository).totalsBetween(USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
	}

	@Test
	@DisplayName("calculateTotalSpending returns zero Money when no expenses exist (never null)")
	void calculateTotalSpendingReturnsZeroWhenEmpty() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(USER_ID, "XAF");
		when(expenseRepository.totalsBetween(USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(BigDecimal.ZERO);

		Money total = expenseService.calculateTotalSpending(USER_ID, from, to);

		assertMoney(total, "0", "XAF");
	}

	@Test
	@DisplayName("findSpendingByCategory returns grouped spending mapped correctly")
	void findSpendingByCategoryReturnsGroupedCategories() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(USER_ID, "USD");
		CategorySpendingProjection p1 = new TestCategorySpendingProjection(FOOD_CATEGORY_ID, "Food & Groceries", "food", "#FF5733", new BigDecimal("450.00"));
		CategorySpendingProjection p2 = new TestCategorySpendingProjection(TRANSPORT_CATEGORY_ID, "Transport", "car", "#33FF57", new BigDecimal("150.50"));

		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of(p1, p2));

		List<ExpenseResponses.CategorySpending> results = expenseService.findSpendingByCategory(USER_ID, from, to);

		assertThat(results).hasSize(2);
		assertThat(results.get(0).getCategoryId()).isEqualTo(FOOD_CATEGORY_ID);
		assertThat(results.get(0).getCategoryName()).isEqualTo("Food & Groceries");
		assertThat(results.get(0).getCategoryIcon()).isEqualTo("food");
		assertThat(results.get(0).getCategoryColor()).isEqualTo("#FF5733");
		assertMoney(results.get(0).getTotalSpent(), "450.00", "USD");

		assertThat(results.get(1).getCategoryId()).isEqualTo(TRANSPORT_CATEGORY_ID);
		assertThat(results.get(1).getCategoryName()).isEqualTo("Transport");
		assertThat(results.get(1).getCategoryIcon()).isEqualTo("car");
		assertThat(results.get(1).getCategoryColor()).isEqualTo("#33FF57");
		assertMoney(results.get(1).getTotalSpent(), "150.50", "USD");
		verify(userRepository).findById(USER_ID);
		verify(expenseRepository).findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
	}

	@Test
	@DisplayName("getSpendingSummary combines total and categories into a complete summary")
	void getSpendingSummaryCombinesTotalAndCategories() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(USER_ID, "EUR");
		when(expenseRepository.totalsBetween(USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(new BigDecimal("600.50"));

		CategorySpendingProjection p1 = new TestCategorySpendingProjection(FOOD_CATEGORY_ID, "Food", "food", "#FF0000", new BigDecimal("600.50"));
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of(p1));

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(USER_ID, from, to);

		assertThat(summary.getFrom()).isEqualTo(from);
		assertThat(summary.getTo()).isEqualTo(to);
		assertMoney(summary.getTotalSpent(), "600.50", "EUR");
		assertThat(summary.getCategories()).hasSize(1);
		assertThat(summary.getCategories().get(0).getCategoryId()).isEqualTo(FOOD_CATEGORY_ID);
		assertThat(summary.getCategories().get(0).getCategoryName()).isEqualTo("Food");
		assertMoney(summary.getCategories().get(0).getTotalSpent(), "600.50", "EUR");
		verify(userRepository).findById(USER_ID);
		verify(expenseRepository).totalsBetween(USER_ID, from, to);
		verify(expenseRepository).findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
	}

	@Test
	@DisplayName("getSpendingSummary returns zero Money and an empty category list when no expenses exist")
	void getSpendingSummaryReturnsZeroWhenEmpty() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(USER_ID, "USD");
		when(expenseRepository.totalsBetween(USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(BigDecimal.ZERO);
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of());

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(USER_ID, from, to);

		assertThat(summary.getFrom()).isEqualTo(from);
		assertThat(summary.getTo()).isEqualTo(to);
		assertMoney(summary.getTotalSpent(), "0", "USD");
		assertThat(summary.getCategories()).isEmpty();
		verify(userRepository).findById(USER_ID);
		verify(expenseRepository).totalsBetween(USER_ID, from, to);
		verify(expenseRepository).findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
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

		verifyNoInteractions(userRepository, expenseRepository, userCategoryRepository,
			financialPeriodService, budgetRepository);
	}

	@Test
	@DisplayName("Omitting dates applies default wide bounds from DateRanges")
	void omittingDatesAppliesDefaultBounds() {
		LocalDate from = LocalDate.of(1900, 1, 1);
		LocalDate to = LocalDate.of(9999, 12, 31);

		stubUser(USER_ID, "XAF");
		when(expenseRepository.totalsBetween(USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(new BigDecimal("200.00"));
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to))
			.thenReturn(List.of());

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(USER_ID, null, null);

		assertThat(summary.getFrom()).isNull();
		assertThat(summary.getTo()).isNull();
		assertMoney(summary.getTotalSpent(), "200.00", "XAF");
		assertThat(summary.getCategories()).isEmpty();
		verify(userRepository).findById(USER_ID);
		verify(expenseRepository).totalsBetween(USER_ID, from, to);
		verify(expenseRepository).findCategorySpendingByUserIdAndDateBetween(USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
	}

	@Test
	@DisplayName("User isolation: queries are strictly filtered by authenticated userId")
	void queriesAreFilteredByAuthenticatedUserId() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		stubUser(OTHER_USER_ID, "XAF");
		when(expenseRepository.totalsBetween(OTHER_USER_ID, from, to)).thenReturn(totals);
		when(totals.getTotal()).thenReturn(new BigDecimal("50.00"));
		when(expenseRepository.findCategorySpendingByUserIdAndDateBetween(OTHER_USER_ID, from, to))
			.thenReturn(List.of(new TestCategorySpendingProjection(FOOD_CATEGORY_ID, "Food", "food", "#FF0000", new BigDecimal("50.00"))));

		ExpenseResponses.SpendingSummary summary = expenseService.getSpendingSummary(OTHER_USER_ID, from, to);

		assertMoney(summary.getTotalSpent(), "50.00", "XAF");
		assertThat(summary.getCategories()).hasSize(1);
		assertThat(summary.getCategories().get(0).getCategoryId()).isEqualTo(FOOD_CATEGORY_ID);
		assertMoney(summary.getCategories().get(0).getTotalSpent(), "50.00", "XAF");
		verify(userRepository).findById(OTHER_USER_ID);
		verify(expenseRepository).totalsBetween(OTHER_USER_ID, from, to);
		verify(expenseRepository).findCategorySpendingByUserIdAndDateBetween(OTHER_USER_ID, from, to);
		verifyNoMoreInteractions(userRepository, expenseRepository);
	}

	private void stubUser(UUID userId, String baseCurrency) {
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(user.getBaseCurrency()).thenReturn(baseCurrency);
	}

	private static void assertMoney(Money money, String amount, String currency) {
		assertThat(money).isNotNull();
		assertThat(money.amount()).isEqualByComparingTo(amount);
		assertThat(money.currency()).isEqualTo(currency);
	}

	private record TestCategorySpendingProjection(
			UUID categoryId,
			String categoryName,
			String categoryIcon,
			String categoryColor,
			BigDecimal totalSpent
	) implements CategorySpendingProjection {
		@Override public UUID getCategoryId() { return categoryId; }
		@Override public String getCategoryName() { return categoryName; }
		@Override public String getCategoryIcon() { return categoryIcon; }
		@Override public String getCategoryColor() { return categoryColor; }
		@Override public BigDecimal getTotalSpent() { return totalSpent; }
	}
}
