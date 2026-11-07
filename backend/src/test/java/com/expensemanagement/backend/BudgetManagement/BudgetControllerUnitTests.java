package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.expensemanagement.backend.BudgetManagement.Controllers.BudgetController;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetService;

@ExtendWith(MockitoExtension.class)
class BudgetControllerUnitTests {

	@Mock
	private BudgetService budgetService;

	@Mock
	private Jwt jwt;

	private BudgetController budgetController;

	private static final Long USER_ID = 5L;

	@BeforeEach
	void setUp() {
		budgetController = new BudgetController(budgetService);
	}

	@Test
	void createDelegatesToServiceWithCallerUserId() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(1L)
				.overallAmount(new BigDecimal("1000.00"))
				.build();

		BudgetResponses.BudgetDetails expected = BudgetResponses.BudgetDetails.builder()
				.id(10L)
				.overallAmount(new BigDecimal("1000.00"))
				.build();
		when(budgetService.createBudget(USER_ID, request)).thenReturn(expected);

		BudgetResponses.BudgetDetails result = budgetController.create(jwt, request);
		assertThat(result.getId()).isEqualTo(10L);
		verify(budgetService).createBudget(USER_ID, request);
	}

	@Test
	void allocateCategoryDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(2L)
				.allocatedAmount(new BigDecimal("300.00"))
				.build();

		BudgetResponses.AllocationDetails expected = BudgetResponses.AllocationDetails.builder()
				.id(20L)
				.budgetId(10L)
				.allocatedAmount(new BigDecimal("300.00"))
				.build();
		when(budgetService.addOrUpdateAllocation(USER_ID, 10L, request)).thenReturn(expected);

		BudgetResponses.AllocationDetails result = budgetController.allocateCategory(jwt, 10L, request);
		assertThat(result.getAllocatedAmount()).isEqualByComparingTo("300.00");
		verify(budgetService).addOrUpdateAllocation(USER_ID, 10L, request);
	}

	@Test
	void updateAllocationDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		CategoryAllocationRequests.UpdateAllocation request = CategoryAllocationRequests.UpdateAllocation.builder()
				.allocatedAmount(new BigDecimal("450.00"))
				.build();

		BudgetResponses.AllocationDetails expected = BudgetResponses.AllocationDetails.builder()
				.id(20L)
				.budgetId(10L)
				.allocatedAmount(new BigDecimal("450.00"))
				.build();
		when(budgetService.updateAllocation(USER_ID, 10L, 20L, request)).thenReturn(expected);

		BudgetResponses.AllocationDetails result = budgetController.updateAllocation(jwt, 10L, 20L, request);
		assertThat(result.getAllocatedAmount()).isEqualByComparingTo("450.00");
		verify(budgetService).updateAllocation(USER_ID, 10L, 20L, request);
	}

	@Test
	void deleteAllocationDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		budgetController.deleteAllocation(jwt, 10L, 20L);
		verify(budgetService).deleteAllocation(USER_ID, 10L, 20L);
	}

	@Test
	void getAllocationsDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		when(budgetService.getAllocations(USER_ID, 10L)).thenReturn(List.of());

		List<BudgetResponses.AllocationDetails> result = budgetController.getAllocations(jwt, 10L);
		assertThat(result).isEmpty();
		verify(budgetService).getAllocations(USER_ID, 10L);
	}
}
