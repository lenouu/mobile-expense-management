package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.BudgetManagement.Repositories.CategoryAllocationRepository;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceImplements.BudgetServiceImpl;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.CategoryManagement.Repositories.UserCategoryRepository;
import com.expensemanagement.backend.Exceptions.DuplicateResourceException;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodRepository;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class BudgetValidationUnitTests {

	@Mock
	private BudgetRepository budgetRepository;

	@Mock
	private CategoryAllocationRepository allocationRepository;

	@Mock
	private UserCategoryRepository userCategoryRepository;

	@Mock
	private FinancialPeriodRepository periodRepository;

	@Mock
	private UserRepository userRepository;

	private BudgetServiceImpl budgetService;

	private static final UUID USER_ID = id(1);
	private static final UUID OTHER_USER_ID = id(2);

	@BeforeEach
	void setUp() {
		budgetService = new BudgetServiceImpl(
				budgetRepository,
				allocationRepository,
				userCategoryRepository,
				periodRepository,
				userRepository
		);
	}

	@Test
	@DisplayName("Allocation amount must be greater than zero")
	void allocationAmountMustBeGreaterThanZero() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("500.00"));

		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, BigDecimal.ZERO, null))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Allocation amount must be greater than 0");

		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("-10.00"), null))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Allocation amount must be greater than 0");

		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, null, null))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Allocation amount must be greater than 0");
	}

	@Test
	@DisplayName("Provisional rule: Category allocations sum may be strictly lower than overall budget")
	void allocationsSumMayBeLowerThanOverallBudget() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("300.00"));

		// 300.00 (existing) + 400.00 (new) = 700.00 <= 1000.00 -> Valid!
		assertThatCode(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("400.00"), null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Category allocations sum may exactly equal overall budget")
	void allocationsSumCanExactlyEqualOverallBudget() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("600.00"));

		// 600.00 + 400.00 = 1000.00 == 1000.00 -> Valid!
		assertThatCode(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("400.00"), null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Category allocations sum cannot exceed overall budget")
	void allocationsSumExceedingOverallBudgetThrowsException() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("800.00"));

		// 800.00 + 250.00 = 1050.00 > 1000.00 -> Throws!
		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("250.00"), null))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Total category allocations (1050.00) cannot exceed overall budget (1000.00)");
	}

	@Test
	@DisplayName("Updating allocation excludes existing allocation amount (prevents double-counting)")
	void updateAllocationExcludesExistingAllocationFromSum() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		UUID existingAllocationId = id(42);
		// Existing allocations total 800.00, but excluding allocation 42 (which was 300.00), other allocations total 500.00
		when(allocationRepository.sumAllocationsByBudgetIdExcluding(id(10), existingAllocationId))
			.thenReturn(new BigDecimal("500.00"));

		// Updating allocation 42 to 450.00: 500.00 + 450.00 = 950.00 <= 1000.00 -> Valid!
		assertThatCode(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("450.00"), existingAllocationId))
			.doesNotThrowAnyException();

		// Updating allocation 42 to 550.00: 500.00 + 550.00 = 1050.00 > 1000.00 -> Throws!
		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("550.00"), existingAllocationId))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Total category allocations (1050.00) cannot exceed overall budget (1000.00)");
	}

	@Test
	@DisplayName("Adding allocation to budget through addOrUpdateAllocation enforces validation")
	void addOrUpdateAllocationEnforcesValidationAndSaves() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		UserCategory category = new UserCategory();
		category.setId(id(5));
		category.setName("Groceries");

		when(budgetRepository.findByIdAndUserId(id(10), USER_ID)).thenReturn(Optional.of(budget));
		when(userCategoryRepository.findByIdAndUserId(id(5), USER_ID)).thenReturn(Optional.of(category));
		when(allocationRepository.findByBudgetIdAndCategoryId(id(10), id(5))).thenReturn(Optional.empty());
		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("400.00"));

		CategoryAllocation savedAllocation = new CategoryAllocation();
		savedAllocation.setId(id(1));
		savedAllocation.setBudget(budget);
		savedAllocation.setCategory(category);
		savedAllocation.setAllocatedAmount(new BigDecimal("350.00"));

		when(allocationRepository.saveAndFlush(any(CategoryAllocation.class))).thenReturn(savedAllocation);

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(id(5))
				.allocatedAmount(new BigDecimal("350.00"))
				.build();

		BudgetResponses.AllocationDetails response = budgetService.addOrUpdateAllocation(USER_ID, id(10), request);
		assertThat(response.getAllocatedAmount()).isEqualByComparingTo("350.00");
		assertThat(response.getCategory().getName()).isEqualTo("Groceries");
	}

	@Test
	@DisplayName("Cannot allocate to non-existent budget or another user's budget")
	void cannotAllocateToAnotherUsersBudget() {
		when(budgetRepository.findByIdAndUserId(id(99), USER_ID)).thenReturn(Optional.empty());

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(id(5))
				.allocatedAmount(new BigDecimal("100.00"))
				.build();

		assertThatThrownBy(() -> budgetService.addOrUpdateAllocation(USER_ID, id(99), request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("No budget with id " + id(99));
	}

	@Test
	@DisplayName("Cannot allocate using another user's category")
	void cannotAllocateAnotherUsersCategory() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(id(10), USER_ID)).thenReturn(Optional.of(budget));
		when(userCategoryRepository.findByIdAndUserId(id(77), USER_ID)).thenReturn(Optional.empty());

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(id(77))
				.allocatedAmount(new BigDecimal("100.00"))
				.build();

		assertThatThrownBy(() -> budgetService.addOrUpdateAllocation(USER_ID, id(10), request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Category " + id(77) + " is not one of your categories");
	}

	@Test
	@DisplayName("Updating overall budget below current sum of allocations throws InvalidRequestException")
	void updateOverallBudgetBelowCurrentAllocationsFails() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(id(10), USER_ID)).thenReturn(Optional.of(budget));
		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("750.00"));

		BudgetRequests.UpdateBudget request = BudgetRequests.UpdateBudget.builder()
				.overallAmount(new BigDecimal("700.00"))
				.build();

		assertThatThrownBy(() -> budgetService.updateBudget(USER_ID, id(10), request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Overall budget (700.00) cannot be less than total current category allocations (750.00)");
	}

	@Test
	@DisplayName("Updating overall budget to amount greater than or equal to current allocations succeeds")
	void updateOverallBudgetAboveCurrentAllocationsSucceeds() {
		Budget budget = new Budget();
		budget.setId(id(10));
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(id(10), USER_ID)).thenReturn(Optional.of(budget));
		when(allocationRepository.sumAllocationsByBudgetId(id(10))).thenReturn(new BigDecimal("750.00"));
		when(budgetRepository.saveAndFlush(any(Budget.class))).thenReturn(budget);
		when(allocationRepository.findAllByBudgetId(id(10))).thenReturn(List.of());

		BudgetRequests.UpdateBudget request = BudgetRequests.UpdateBudget.builder()
				.overallAmount(new BigDecimal("800.00"))
				.build();

		BudgetResponses.BudgetDetails details = budgetService.updateBudget(USER_ID, id(10), request);
		assertThat(budget.getOverallAmount()).isEqualByComparingTo("800.00");
	}

	@Test
	@DisplayName("Creating budget for period not owned by user throws InvalidRequestException")
	void createBudgetWithUnownedPeriodFails() {
		when(periodRepository.findByIdAndUserId(id(999), USER_ID)).thenReturn(Optional.empty());

		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(id(999))
				.overallAmount(new BigDecimal("1500.00"))
				.build();

		assertThatThrownBy(() -> budgetService.createBudget(USER_ID, request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Financial period " + id(999) + " is not one of your periods");
	}

	@Test
	@DisplayName("Creating duplicate budget for same period throws DuplicateResourceException")
	void createDuplicateBudgetForSamePeriodFails() {
		FinancialPeriod period = new FinancialPeriod();
		period.setId(id(12));

		Budget existingBudget = new Budget();
		existingBudget.setId(id(10));

		when(periodRepository.findByIdAndUserId(id(12), USER_ID)).thenReturn(Optional.of(period));
		when(budgetRepository.findByUserIdAndPeriodId(USER_ID, id(12))).thenReturn(Optional.of(existingBudget));

		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(id(12))
				.overallAmount(new BigDecimal("1500.00"))
				.build();

		assertThatThrownBy(() -> budgetService.createBudget(USER_ID, request))
			.isInstanceOf(DuplicateResourceException.class)
			.hasMessage("A budget already exists for period id " + id(12));
	}

	private static UUID id(long value) {
		return new UUID(0L, value);
	}

}
