package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
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

	private static final Long USER_ID = 1L;
	private static final Long OTHER_USER_ID = 2L;

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
		budget.setId(10L);
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
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("300.00"));

		// 300.00 (existing) + 400.00 (new) = 700.00 <= 1000.00 -> Valid!
		assertThatCode(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("400.00"), null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Category allocations sum may exactly equal overall budget")
	void allocationsSumCanExactlyEqualOverallBudget() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("600.00"));

		// 600.00 + 400.00 = 1000.00 == 1000.00 -> Valid!
		assertThatCode(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("400.00"), null))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Category allocations sum cannot exceed overall budget")
	void allocationsSumExceedingOverallBudgetThrowsException() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("800.00"));

		// 800.00 + 250.00 = 1050.00 > 1000.00 -> Throws!
		assertThatThrownBy(() -> budgetService.validateAllocationAgainstBudget(budget, new BigDecimal("250.00"), null))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Total category allocations (1050.00) cannot exceed overall budget (1000.00)");
	}

	@Test
	@DisplayName("Updating allocation excludes existing allocation amount (prevents double-counting)")
	void updateAllocationExcludesExistingAllocationFromSum() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		Long existingAllocationId = 42L;
		// Existing allocations total 800.00, but excluding allocation 42 (which was 300.00), other allocations total 500.00
		when(allocationRepository.sumAllocationsByBudgetIdExcluding(10L, existingAllocationId))
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
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		UserCategory category = new UserCategory();
		category.setId(5L);
		category.setName("Groceries");

		when(budgetRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(budget));
		when(userCategoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
		when(allocationRepository.findByBudgetIdAndCategoryId(10L, 5L)).thenReturn(Optional.empty());
		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("400.00"));

		CategoryAllocation savedAllocation = new CategoryAllocation();
		savedAllocation.setId(1L);
		savedAllocation.setBudget(budget);
		savedAllocation.setCategory(category);
		savedAllocation.setAllocatedAmount(new BigDecimal("350.00"));

		when(allocationRepository.saveAndFlush(any(CategoryAllocation.class))).thenReturn(savedAllocation);

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(5L)
				.allocatedAmount(new BigDecimal("350.00"))
				.build();

		BudgetResponses.AllocationDetails response = budgetService.addOrUpdateAllocation(USER_ID, 10L, request);
		assertThat(response.getAllocatedAmount()).isEqualByComparingTo("350.00");
		assertThat(response.getCategory().getName()).isEqualTo("Groceries");
	}

	@Test
	@DisplayName("Cannot allocate to non-existent budget or another user's budget")
	void cannotAllocateToAnotherUsersBudget() {
		when(budgetRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(5L)
				.allocatedAmount(new BigDecimal("100.00"))
				.build();

		assertThatThrownBy(() -> budgetService.addOrUpdateAllocation(USER_ID, 99L, request))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("No budget with id 99");
	}

	@Test
	@DisplayName("Cannot allocate using another user's category")
	void cannotAllocateAnotherUsersCategory() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(budget));
		when(userCategoryRepository.findByIdAndUserId(77L, USER_ID)).thenReturn(Optional.empty());

		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(77L)
				.allocatedAmount(new BigDecimal("100.00"))
				.build();

		assertThatThrownBy(() -> budgetService.addOrUpdateAllocation(USER_ID, 10L, request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Category 77 is not one of your categories");
	}

	@Test
	@DisplayName("Updating overall budget below current sum of allocations throws InvalidRequestException")
	void updateOverallBudgetBelowCurrentAllocationsFails() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(budget));
		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("750.00"));

		BudgetRequests.UpdateBudget request = BudgetRequests.UpdateBudget.builder()
				.overallAmount(new BigDecimal("700.00"))
				.build();

		assertThatThrownBy(() -> budgetService.updateBudget(USER_ID, 10L, request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Overall budget (700.00) cannot be less than total current category allocations (750.00)");
	}

	@Test
	@DisplayName("Updating overall budget to amount greater than or equal to current allocations succeeds")
	void updateOverallBudgetAboveCurrentAllocationsSucceeds() {
		Budget budget = new Budget();
		budget.setId(10L);
		budget.setOverallAmount(new BigDecimal("1000.00"));

		when(budgetRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(budget));
		when(allocationRepository.sumAllocationsByBudgetId(10L)).thenReturn(new BigDecimal("750.00"));
		when(budgetRepository.saveAndFlush(any(Budget.class))).thenReturn(budget);
		when(allocationRepository.findAllByBudgetId(10L)).thenReturn(List.of());

		BudgetRequests.UpdateBudget request = BudgetRequests.UpdateBudget.builder()
				.overallAmount(new BigDecimal("800.00"))
				.build();

		BudgetResponses.BudgetDetails details = budgetService.updateBudget(USER_ID, 10L, request);
		assertThat(budget.getOverallAmount()).isEqualByComparingTo("800.00");
	}

	@Test
	@DisplayName("Creating budget for period not owned by user throws InvalidRequestException")
	void createBudgetWithUnownedPeriodFails() {
		when(periodRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(999L)
				.overallAmount(new BigDecimal("1500.00"))
				.build();

		assertThatThrownBy(() -> budgetService.createBudget(USER_ID, request))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("Financial period 999 is not one of your periods");
	}

	@Test
	@DisplayName("Creating duplicate budget for same period throws DuplicateResourceException")
	void createDuplicateBudgetForSamePeriodFails() {
		FinancialPeriod period = new FinancialPeriod();
		period.setId(12L);

		Budget existingBudget = new Budget();
		existingBudget.setId(10L);

		when(periodRepository.findByIdAndUserId(12L, USER_ID)).thenReturn(Optional.of(period));
		when(budgetRepository.findByUserIdAndPeriodId(USER_ID, 12L)).thenReturn(Optional.of(existingBudget));

		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(12L)
				.overallAmount(new BigDecimal("1500.00"))
				.build();

		assertThatThrownBy(() -> budgetService.createBudget(USER_ID, request))
			.isInstanceOf(DuplicateResourceException.class)
			.hasMessage("A budget already exists for period id 12");
	}
}
