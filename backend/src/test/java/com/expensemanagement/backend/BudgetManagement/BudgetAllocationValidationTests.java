package com.expensemanagement.backend.BudgetManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * S4-TECH-2: Allocation vs overall budget validation logic.
 *
 * Enforces that:
 * 1. Category allocations may sum to less than or equal to the overall budget.
 * 2. Total category allocations cannot exceed the overall budget.
 * 3. Updating an existing allocation recalculates the total without double-counting.
 * 4. Overall budget cannot be updated below the current sum of category allocations.
 * 5. Users can only manage their own budgets and categories.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class BudgetAllocationValidationTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allocationsSumMayBeLowerThanOverallBudget() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		Long budgetId = createBudget(token, periodId, "1000.00");

		// Allocation 1: 300.00 (Total: 300.00 <= 1000.00)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + food + ",\"allocatedAmount\":300.00}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.allocatedAmount").value(300.00));

		// Allocation 2: 400.00 (Total: 700.00 <= 1000.00)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + transport + ",\"allocatedAmount\":400.00}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.allocatedAmount").value(400.00));

		// Verify overall budget details: sum is 700, remaining unallocated is 300
		mockMvc.perform(get("/api/budgets/" + budgetId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overallAmount").value(1000.00))
			.andExpect(jsonPath("$.totalAllocated").value(700.00))
			.andExpect(jsonPath("$.remainingUnallocated").value(300.00))
			.andExpect(jsonPath("$.allocations", hasSize(2)));
	}

	@Test
	void allocationsSumCannotExceedOverallBudget() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");
		Integer housing = categoryId(token, "Housing");

		Long budgetId = createBudget(token, periodId, "1000.00");

		// Allocate 600.00
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + food + ",\"allocatedAmount\":600.00}"))
			.andExpect(status().isCreated());

		// Allocate 300.00 (Total: 900.00)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + transport + ",\"allocatedAmount\":300.00}"))
			.andExpect(status().isCreated());

		// Attempt to allocate 150.00 (Total would be 1050.00 > 1000.00) -> MUST FAIL (400)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + housing + ",\"allocatedAmount\":150.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("invalid_request"))
			.andExpect(jsonPath("$.message").value("Total category allocations (1050.00) cannot exceed overall budget (1000.00)"));
	}

	@Test
	void allocationsSumCanExactlyEqualOverallBudget() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		Long budgetId = createBudget(token, periodId, "1000.00");

		// Allocate 600.00
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + food + ",\"allocatedAmount\":600.00}"))
			.andExpect(status().isCreated());

		// Allocate 400.00 (Total: exactly 1000.00 == 1000.00)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + transport + ",\"allocatedAmount\":400.00}"))
			.andExpect(status().isCreated());

		// Verify remainingUnallocated is 0.00
		mockMvc.perform(get("/api/budgets/" + budgetId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overallAmount").value(1000.00))
			.andExpect(jsonPath("$.totalAllocated").value(1000.00))
			.andExpect(jsonPath("$.remainingUnallocated").value(0.00));
	}

	@Test
	void updateAllocationDoesNotDoubleCountExistingAmount() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		Long budgetId = createBudget(token, periodId, "1000.00");

		// Food: 400.00
		String foodResp = mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + food + ",\"allocatedAmount\":400.00}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Integer foodAllocId = JsonPath.read(foodResp, "$.id");

		// Transport: 400.00 (Total = 800.00)
		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + transport + ",\"allocatedAmount\":400.00}"))
			.andExpect(status().isCreated());

		// Update Food from 400.00 to 500.00 (New total: (800 - 400) + 500 = 900.00 <= 1000.00)
		// If double counting occurred: 800 + 500 = 1300 > 1000 and it would incorrectly fail.
		mockMvc.perform(put("/api/budgets/" + budgetId + "/allocations/" + foodAllocId)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"allocatedAmount\":500.00}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.allocatedAmount").value(500.00));

		// Update Food to 700.00 (New total: (900 - 500) + 700 = 1100.00 > 1000.00) -> MUST FAIL (400)
		mockMvc.perform(put("/api/budgets/" + budgetId + "/allocations/" + foodAllocId)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"allocatedAmount\":700.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("invalid_request"))
			.andExpect(jsonPath("$.message").value("Total category allocations (1100.00) cannot exceed overall budget (1000.00)"));
	}

	@Test
	void updatingOverallBudgetBelowCurrentAllocationsFails() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		Long budgetId = createBudget(token, periodId, "1000.00");

		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + food + ",\"allocatedAmount\":400.00}"))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/budgets/" + budgetId + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + transport + ",\"allocatedAmount\":350.00}"))
			.andExpect(status().isCreated());

		// Total allocations is 750.00. Attempt to lower budget to 700.00 -> MUST FAIL (400)
		mockMvc.perform(put("/api/budgets/" + budgetId)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"overallAmount\":700.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("invalid_request"));

		// Lowering budget to 800.00 (>= 750.00) -> SUCCEEDS (200)
		mockMvc.perform(put("/api/budgets/" + budgetId)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"overallAmount\":800.00}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overallAmount").value(800.00))
			.andExpect(jsonPath("$.totalAllocated").value(750.00))
			.andExpect(jsonPath("$.remainingUnallocated").value(50.00));
	}

	@Test
	void userCannotAllocateToAnotherUsersBudgetOrUseAnotherUsersCategory() throws Exception {
		String user1Token = registerAndLogin();
		String user2Token = registerAndLogin();

		Long period1 = currentPeriodId(user1Token);
		Long budget1 = createBudget(user1Token, period1, "1000.00");

		Integer user2Category = categoryId(user2Token, "Food & Groceries");

		// User 2 cannot access or allocate to User 1's budget (404)
		mockMvc.perform(post("/api/budgets/" + budget1 + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, user2Token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + user2Category + ",\"allocatedAmount\":200.00}"))
			.andExpect(status().isNotFound());

		// User 1 cannot use User 2's category in User 1's budget (400)
		mockMvc.perform(post("/api/budgets/" + budget1 + "/allocations")
				.header(HttpHeaders.AUTHORIZATION, user1Token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + user2Category + ",\"allocatedAmount\":200.00}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void budgetEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/budgets/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/budgets").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	private Long createBudget(String token, Long periodId, String overallAmount) throws Exception {
		String body = mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"periodId\":" + periodId + ",\"overallAmount\":" + overallAmount + "}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(body, "$.id");
		return id.longValue();
	}

	private Long currentPeriodId(String token) throws Exception {
		String body = mockMvc.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(body, "$.id");
		return id.longValue();
	}

	private Integer categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		java.util.List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private String registerAndLogin() throws Exception {
		String userName = "user." + UUID.randomUUID().toString().substring(0, 8);
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}"))
			.andExpect(status().isCreated());
		String body = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usernameOrEmail\":\"" + userName + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}
}
