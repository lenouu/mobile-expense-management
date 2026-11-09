package com.expensemanagement.backend.BudgetManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

	private static final UUID USER_ID = id(5);

	@BeforeEach
	void setUp() {
		budgetController = new BudgetController(budgetService);
	}

	@Test
	void createDelegatesToServiceWithCallerUserId() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		BudgetRequests.CreateBudget request = BudgetRequests.CreateBudget.builder()
				.periodId(id(1))
				.overallAmount(new BigDecimal("1000.00"))
				.build();

		BudgetResponses.BudgetDetails expected = BudgetResponses.BudgetDetails.builder()
				.id(id(10))
				.overallAmount(new BigDecimal("1000.00"))
				.build();
		when(budgetService.createBudget(USER_ID, request)).thenReturn(expected);

		BudgetResponses.BudgetDetails result = budgetController.create(jwt, request);
		assertThat(result.getId()).isEqualTo(id(10));
		verify(budgetService).createBudget(USER_ID, request);
	}

	@Test
	void allocateCategoryDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		CategoryAllocationRequests.SaveAllocation request = CategoryAllocationRequests.SaveAllocation.builder()
				.categoryId(id(2))
				.allocatedAmount(new BigDecimal("300.00"))
				.build();

		BudgetResponses.AllocationDetails expected = BudgetResponses.AllocationDetails.builder()
				.id(id(20))
				.budgetId(id(10))
				.allocatedAmount(new BigDecimal("300.00"))
				.build();
		when(budgetService.addOrUpdateAllocation(USER_ID, id(10), request)).thenReturn(expected);

		BudgetResponses.AllocationDetails result = budgetController.allocateCategory(jwt, id(10), request);
		assertThat(result.getAllocatedAmount()).isEqualByComparingTo("300.00");
		verify(budgetService).addOrUpdateAllocation(USER_ID, id(10), request);
	}

	@Test
	void updateAllocationDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		CategoryAllocationRequests.UpdateAllocation request = CategoryAllocationRequests.UpdateAllocation.builder()
				.allocatedAmount(new BigDecimal("450.00"))
				.build();

		BudgetResponses.AllocationDetails expected = BudgetResponses.AllocationDetails.builder()
				.id(id(20))
				.budgetId(id(10))
				.allocatedAmount(new BigDecimal("450.00"))
				.build();
		when(budgetService.updateAllocation(USER_ID, id(10), id(20), request)).thenReturn(expected);

		BudgetResponses.AllocationDetails result = budgetController.updateAllocation(jwt, id(10), id(20), request);
		assertThat(result.getAllocatedAmount()).isEqualByComparingTo("450.00");
		verify(budgetService).updateAllocation(USER_ID, id(10), id(20), request);
	}

	@Test
	void deleteAllocationDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));

		budgetController.deleteAllocation(jwt, id(10), id(20));
		verify(budgetService).deleteAllocation(USER_ID, id(10), id(20));
	}

	@Test
	void getAllocationsDelegatesToService() {
		when(jwt.getSubject()).thenReturn(String.valueOf(USER_ID));
		when(budgetService.getAllocations(USER_ID, id(10))).thenReturn(List.of());

		List<BudgetResponses.AllocationDetails> result = budgetController.getAllocations(jwt, id(10));
		assertThat(result).isEmpty();
		verify(budgetService).getAllocations(USER_ID, id(10));
	}

	private static UUID id(long value) {
		return new UUID(0L, value);
	}

}
