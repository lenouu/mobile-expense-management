package com.expensemanagement.backend.AnalyticsManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.ResultActions;

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US50 / S10-TECH-1 end to end: {@code GET /api/analytics/summary}. Every test registers its own
 * users, so records never leak between tests. The budget-selection rules are covered without a
 * database in {@link AnalyticsBudgetSectionTests}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class AnalyticsSummaryTests {

	private static final String PASSWORD = "Password123!";

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theWindowShowsIncomeExpensesAndTheirDifference() throws Exception {
		String token = registerAndLogin();
		String food = categoryId(token, "Food & Groceries");
		income(token, "100000", TODAY.minusDays(3));
		expense(token, "30000", TODAY.minusDays(2), food);
		expense(token, "5000", TODAY.minusDays(1), food);

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.current.income.amount").value(100000))
			.andExpect(jsonPath("$.current.expenses.amount").value(35000))
			.andExpect(jsonPath("$.current.net.amount").value(65000))
			.andExpect(jsonPath("$.current.savingsRate").value(65.0))
			.andExpect(jsonPath("$.current.incomeCount").value(1))
			.andExpect(jsonPath("$.current.expenseCount").value(2))
			.andExpect(jsonPath("$.currency").value("XAF"))
			// One point per day of the window.
			.andExpect(jsonPath("$.daily", hasSize(7)))
			.andExpect(jsonPath("$.byCategory[0].name").value("Food & Groceries"))
			.andExpect(jsonPath("$.byCategory[0].total.amount").value(35000));
	}

	@Test
	void spendingMoreThanTheIncomeShowsADebt() throws Exception {
		String token = registerAndLogin();
		income(token, "10000", TODAY.minusDays(2));
		expense(token, "25000", TODAY.minusDays(1), categoryId(token, "Other"));

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(jsonPath("$.current.net.amount").value(-15000));
	}

	@Test
	void theWindowIsComparedWithTheSameNumberOfDaysBeforeIt() throws Exception {
		String token = registerAndLogin();
		String other = categoryId(token, "Other");
		// Previous window: TODAY-13 .. TODAY-7. Current window: TODAY-6 .. TODAY.
		expense(token, "10000", TODAY.minusDays(10), other);
		expense(token, "15000", TODAY.minusDays(3), other);

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(jsonPath("$.previousFrom").value(TODAY.minusDays(13).toString()))
			.andExpect(jsonPath("$.previousTo").value(TODAY.minusDays(7).toString()))
			.andExpect(jsonPath("$.previous.expenses.amount").value(10000))
			.andExpect(jsonPath("$.current.expenses.amount").value(15000))
			.andExpect(jsonPath("$.change.expensePercent").value(50.0));
	}

	@Test
	void withARunningBudgetTheDashboardShowsIt() throws Exception {
		String token = registerAndLogin();
		String running = createBudget(token, "This month", 0, 29, "100000");
		// A plan that has not started yet is not the budget in force.
		createBudget(token, "Next month", 40, 69, "90000");
		expense(token, "20000", TODAY, categoryId(token, "Other"), running);

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(jsonPath("$.budget.id").value(running))
			.andExpect(jsonPath("$.budget.planned.amount").value(100000))
			.andExpect(jsonPath("$.budget.spent.amount").value(20000))
			.andExpect(jsonPath("$.budget.remaining.amount").value(80000));
	}

	@Test
	void withoutARunningBudgetTheSectionIsLeftOut() throws Exception {
		String token = registerAndLogin();
		createBudget(token, "Next month", 40, 69, "90000");

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void invalidWindowsAreRefused() throws Exception {
		String token = registerAndLogin();

		summary(token, TODAY, TODAY.minusDays(1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_request"));
		summary(token, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 12, 31))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_request"));
		mockMvc.perform(get("/api/analytics/summary").param("from", TODAY.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
	}

	@Test
	void anotherUsersMoneyIsNeverIncluded() throws Exception {
		String token = registerAndLogin();
		String someoneElse = registerAndLogin();
		income(someoneElse, "500000", TODAY.minusDays(1));
		expense(someoneElse, "40000", TODAY.minusDays(1), categoryId(someoneElse, "Other"));
		createBudget(someoneElse, "Theirs", 0, 29, "100000");

		summary(token, TODAY.minusDays(6), TODAY)
			.andExpect(jsonPath("$.current.income.amount").value(0))
			.andExpect(jsonPath("$.current.expenses.amount").value(0))
			.andExpect(jsonPath("$.byCategory", hasSize(0)))
			.andExpect(jsonPath("$.bySource", hasSize(0)))
			.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void theSummaryRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/analytics/summary").param("from", TODAY.minusDays(6).toString())
				.param("to", TODAY.toString()))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions summary(String token, LocalDate from, LocalDate to) throws Exception {
		return mockMvc.perform(get("/api/analytics/summary")
			.param("from", from.toString())
			.param("to", to.toString())
			.header(HttpHeaders.AUTHORIZATION, token));
	}

	private void income(String token, String amount, LocalDate date) throws Exception {
		mockMvc.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":" + amount + "},\"date\":\"" + date + "\",\"source\":\"Salary\"}"))
			.andExpect(status().isCreated());
	}

	private void expense(String token, String amount, LocalDate date, String categoryId) throws Exception {
		expense(token, amount, date, categoryId, null);
	}

	private void expense(String token, String amount, LocalDate date, String categoryId, String budgetId)
			throws Exception {
		mockMvc.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":" + amount + "},\"date\":\"" + date + "\",\"categoryId\":\""
						+ categoryId + "\"" + (budgetId == null ? "" : ",\"budgetId\":\"" + budgetId + "\"") + "}"))
			.andExpect(status().isCreated());
	}

	private String createBudget(String token, String name, int startIn, int endIn, String amount) throws Exception {
		String body = mockMvc.perform(post("/api/budgets").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"startDate\":\"" + TODAY.plusDays(startIn) + "\","
						+ "\"endDate\":\"" + TODAY.plusDays(endIn) + "\",\"overallAmount\":" + amount + "}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$.data[?(@.name == '" + name + "')].id");
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
