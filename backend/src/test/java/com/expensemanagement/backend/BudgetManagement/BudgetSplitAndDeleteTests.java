package com.expensemanagement.backend.BudgetManagement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
 * US31 and US33: what a split may promise, and what deleting a plan does to the record.
 *
 * <p>Two rules are pinned here because both were wrong once:
 *
 * <ul>
 *   <li><b>US31</b> - allocations must be <em>at most</em> the budget, not equal to it. A user may
 *       plan 100 000 and split only 60 000 of it.</li>
 *   <li><b>US33</b> - deleting a budget must not delete the expenses recorded under it. They are
 *       the user's financial record; they are unlinked and stay in the ledger.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class BudgetSplitAndDeleteTests
{
	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aSplitMayBeLessThanTheTotalButNotMore() throws Exception
	{
		String token = registerAndLogin();
		String food = categoryId(token, "Food & Groceries");
		String transport = categoryId(token, "Transport");

		// 600 of 1000: allowed - 400 is deliberately left unallocated.
		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(plan("Partly split", 0, 29, "1000.00", food, "400.00", transport, "200.00")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.overallAmount").value(1000.00))
			.andExpect(jsonPath("$.totalAllocated").value(600.00))
			.andExpect(jsonPath("$.allocations", hasSize(2)));

		// 1200 of 1000: refused, and the message says by how much it goes over. Dated after the first
		// plan, so the "one budget at a time" rule does not refuse it before the split is checked.
		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(plan("Over split", 30, 59, "1000.00", food, "700.00", transport, "500.00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("more than the budget")));
	}

	@Test
	void deletingABudgetKeepsItsExpenses() throws Exception
	{
		String token = registerAndLogin();
		String food = categoryId(token, "Food & Groceries");
		String budgetId = createTestBudget(token, "To be deleted", 0, 6);
		String today = LocalDate.now().toString();

		String assigned = mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":700},\"date\":\"" + today + "\",\"categoryId\":\""
						+ food + "\",\"budgetId\":\"" + budgetId + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String assignedId = JsonPath.read(assigned, "$.id");

		mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":300},\"date\":\"" + today + "\",\"categoryId\":\""
						+ food + "\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/budgets/" + budgetId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());

		// Both expenses survive: the plan is gone, the money is not.
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2));

		// And the one that was assigned no longer belongs to anything.
		mockMvc.perform(get("/api/expenses/" + assignedId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount.amount").value(700.00))
			.andExpect(jsonPath("$.budget").doesNotExist());
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private String plan(String name, int startIn, int endIn, String total,
			String categoryA, String amountA, String categoryB, String amountB) {
		return "{\"name\":\"" + name + "\","
				+ "\"startDate\":\"" + LocalDate.now().plusDays(startIn) + "\","
				+ "\"endDate\":\"" + LocalDate.now().plusDays(endIn) + "\","
				+ "\"overallAmount\":" + total + ","
				+ "\"allocations\":["
				+ "{\"categoryId\":\"" + categoryA + "\",\"allocatedAmount\":" + amountA + "},"
				+ "{\"categoryId\":\"" + categoryB + "\",\"allocatedAmount\":" + amountB + "}"
				+ "]}";
	}

	private String createTestBudget(String token, String name, int startIn, int endIn) throws Exception {
		String body = mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\","
						+ "\"startDate\":\"" + LocalDate.now().plusDays(startIn) + "\","
						+ "\"endDate\":\"" + LocalDate.now().plusDays(endIn) + "\","
						+ "\"overallAmount\":10000}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		java.util.List<String> ids = JsonPath.read(body, "$.data[?(@.name == '" + name + "')].id");
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
			.andReturn().getResponse().getContentAsString();

		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}
}
