package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
		UUID food = categoryId(token, "Food & Groceries");
		UUID transport = categoryId(token, "Transport");
		LocalDate today = LocalDate.now();
		LocalDate from = today.minusDays(30);
		LocalDate to = today.minusDays(1);

		// Include both date-range boundaries and one interior expense.
		createExpense(token, "50.00", from, food);
		createExpense(token, "30.00", from.plusDays(14), food);
		createExpense(token, "20.00", to, transport);

		// Expenses before and after the range must not contribute.
		createExpense(token, "100.00", from.minusDays(1), food);
		createExpense(token, "200.00", today, transport);

		mockMvc.perform(get("/api/expenses/spending")
				.param("from", from.toString())
				.param("to", to.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value(from.toString()))
			.andExpect(jsonPath("$.to").value(to.toString()))
			.andExpect(jsonPath("$.totalSpent.amount").value(100.00))
			.andExpect(jsonPath("$.totalSpent.currency").value("XAF"))
			.andExpect(jsonPath("$.categories", hasSize(2)))
			.andExpect(jsonPath("$.categories[0].categoryId").value(food.toString()))
			.andExpect(jsonPath("$.categories[0].categoryName").value("Food & Groceries"))
			.andExpect(jsonPath("$.categories[0].totalSpent.amount").value(80.00))
			.andExpect(jsonPath("$.categories[0].totalSpent.currency").value("XAF"))
			.andExpect(jsonPath("$.categories[1].categoryId").value(transport.toString()))
			.andExpect(jsonPath("$.categories[1].categoryName").value("Transport"))
			.andExpect(jsonPath("$.categories[1].totalSpent.amount").value(20.00))
			.andExpect(jsonPath("$.categories[1].totalSpent.currency").value("XAF"));
	}

	@Test
	void emptySpendingReturnsZeroTotalAndEmptyList() throws Exception {
		String token = registerAndLogin();
		LocalDate to = LocalDate.now();
		LocalDate from = to.minusDays(30);

		mockMvc.perform(get("/api/expenses/spending")
				.param("from", from.toString())
				.param("to", to.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value(from.toString()))
			.andExpect(jsonPath("$.to").value(to.toString()))
			.andExpect(jsonPath("$.totalSpent.amount").value(0))
			.andExpect(jsonPath("$.totalSpent.currency").value("XAF"))
			.andExpect(jsonPath("$.categories", hasSize(0)));
	}

	@Test
	void invalidDateRangeReturnsBadRequest() throws Exception {
		String token = registerAndLogin();
		LocalDate from = LocalDate.now();

		mockMvc.perform(get("/api/expenses/spending")
				.param("from", from.toString())
				.param("to", from.minusDays(1).toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_request"));
	}

	@Test
	void spendingSummaryExcludesOtherUsersExpensesAndCategories() throws Exception {
		String token = registerAndLogin();
		String otherToken = registerAndLogin();
		UUID food = categoryId(token, "Food & Groceries");
		UUID otherFood = categoryId(otherToken, "Food & Groceries");
		LocalDate date = LocalDate.now().minusDays(1);

		createExpense(token, "10.00", date, food);
		createExpense(otherToken, "900.00", date, otherFood);

		mockMvc.perform(get("/api/expenses/spending")
				.param("from", date.toString())
				.param("to", date.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalSpent.amount").value(10.00))
			.andExpect(jsonPath("$.totalSpent.currency").value("XAF"))
			.andExpect(jsonPath("$.categories", hasSize(1)))
			.andExpect(jsonPath("$.categories[0].categoryId").value(food.toString()))
			.andExpect(jsonPath("$.categories[0].categoryName").value("Food & Groceries"))
			.andExpect(jsonPath("$.categories[0].totalSpent.amount").value(10.00))
			.andExpect(jsonPath("$.categories[0].totalSpent.currency").value("XAF"));
	}

	@Test
	void spendingEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/expenses/spending")).andExpect(status().isUnauthorized());
	}

	private void createExpense(String token, String amount, LocalDate date, UUID categoryId) throws Exception {
		mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"XAF\"},\"date\":\"" + date
						+ "\",\"categoryId\":\"" + categoryId + "\"}"))
			.andExpect(status().isCreated());
	}

	private UUID categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories")
				.param("q", name)
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$.content[?(@.name == '" + name + "')].id");
		return UUID.fromString(ids.get(0));
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
