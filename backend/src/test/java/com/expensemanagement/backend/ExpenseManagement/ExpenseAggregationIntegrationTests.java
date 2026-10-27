package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * S8-TECH-1 integration tests for total and per-category spending aggregations.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseAggregationIntegrationTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void spendingSummaryAggregatesTotalsAndCategoriesCorrectly() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		// Record expenses
		createExpense(token, "50.00", "2026-09-10", food);
		createExpense(token, "30.00", "2026-09-15", food);
		createExpense(token, "20.00", "2026-09-20", transport);

		// Expense outside date range
		createExpense(token, "100.00", "2026-10-05", food);

		// Query for September 2026: total should be 50 + 30 + 20 = 100.00
		mockMvc.perform(get("/api/expenses/spending?from=2026-09-01&to=2026-09-30")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2026-09-01"))
			.andExpect(jsonPath("$.to").value("2026-09-30"))
			.andExpect(jsonPath("$.totalSpent").value(100.00))
			.andExpect(jsonPath("$.categories", hasSize(2)))
			.andExpect(jsonPath("$.categories[0].categoryName").value("Food & Groceries"))
			.andExpect(jsonPath("$.categories[0].totalSpent").value(80.00))
			.andExpect(jsonPath("$.categories[1].categoryName").value("Transport"))
			.andExpect(jsonPath("$.categories[1].totalSpent").value(20.00));
	}

	@Test
	void emptySpendingReturnsZeroTotalAndEmptyList() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/spending?from=2026-01-01&to=2026-01-31")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalSpent").value(0))
			.andExpect(jsonPath("$.categories", hasSize(0)));
	}

	@Test
	void invalidDateRangeReturnsBadRequest() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/spending?from=2026-09-30&to=2026-09-01")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void spendingEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/expenses/spending")).andExpect(status().isUnauthorized());
	}

	private void createExpense(String token, String amount, String date, Integer categoryId) throws Exception {
		mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"categoryId\":" + categoryId + "}"))
			.andExpect(status().isCreated());
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
