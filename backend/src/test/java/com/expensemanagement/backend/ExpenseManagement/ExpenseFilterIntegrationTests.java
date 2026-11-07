package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
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
 * US25: Filter expenses by category and date integration tests.
 *
 * Covers:
 * - AC 1: Category filter returns only expenses in that category.
 * - AC 2: Category and date range combined returns only matching expenses.
 * - AC 3: No matching expenses returns clear empty list (200 OK).
 * - Safe defaults:
 *   - Date range only with inclusive bounds.
 *   - Open-ended date range.
 *   - Invalid range (from after to) returns 400.
 *   - Invalid category ID (non-numeric or non-positive) returns 400.
 *   - Another user's category returns no results and never leaks data.
 *   - Unauthenticated request returns 401.
 */
@org.junit.jupiter.api.condition.EnabledIf("isDockerAvailable")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseFilterIntegrationTests {

	static boolean isDockerAvailable() {
		try {
			return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable t) {
			return false;
		}
	}

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("AC 1: When user filters by category only, only expenses in that category are shown")
	void filterByCategoryOnly_returnsOnlyMatchingCategoryExpenses() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		create(token, "20.00", "2026-09-10", food, "Food expense 1");
		create(token, "30.00", "2026-09-15", transport, "Transport expense");
		create(token, "40.00", "2026-09-20", food, "Food expense 2");

		mockMvc.perform(get("/api/expenses?categoryId=" + food).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].category.id").value(food))
				.andExpect(jsonPath("$.content[1].category.id").value(food))
				.andExpect(jsonPath("$.page.totalElements").value(2));
	}

	@Test
	@DisplayName("AC 2: When user filters by category AND date range, both conditions apply together")
	void filterByCategoryAndDateRange_appliesBothConditions() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		// Food in range
		create(token, "25.00", "2026-09-15", food, "Food in September");
		// Food outside range
		create(token, "35.00", "2026-08-20", food, "Food in August");
		// Transport in range (different category)
		create(token, "50.00", "2026-09-18", transport, "Transport in September");

		mockMvc.perform(get("/api/expenses?categoryId=" + food + "&from=2026-09-01&to=2026-09-30")
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].description").value("Food in September"))
				.andExpect(jsonPath("$.content[0].category.id").value(food))
				.andExpect(jsonPath("$.page.totalElements").value(1));
	}

	@Test
	@DisplayName("AC 3: When no expense matches the filters, returns an empty list without error")
	void filterWithNoMatches_returnsEmptyListWithoutError() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");

		create(token, "15.00", "2026-09-10", food, "September food");

		// Query for October
		mockMvc.perform(get("/api/expenses?categoryId=" + food + "&from=2026-10-01&to=2026-10-31")
						.header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("Safe default: Filter by date range only with inclusive bounds")
	void filterByDateRangeOnly_inclusiveBounds() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");

		create(token, "10.00", "2026-10-01", food, "Start bound");
		create(token, "20.00", "2026-10-15", food, "Middle");
		create(token, "30.00", "2026-10-31", food, "End bound");
		create(token, "40.00", "2026-11-01", food, "Outside bound");

		mockMvc.perform(get("/api/expenses?from=2026-10-01&to=2026-10-31").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.page.totalElements").value(3));
	}

	@Test
	@DisplayName("Safe default: Start date after end date returns 400 Bad Request")
	void filterWithStartAfterEnd_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses?from=2026-11-15&to=2026-11-01").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	@DisplayName("Safe default: Malformed non-numeric category ID returns 400 Bad Request")
	void filterWithNonNumericCategoryId_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses?categoryId=abc").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_parameter"));
	}

	@Test
	@DisplayName("Safe default: Non-positive category ID returns 400 Bad Request")
	void filterWithNonPositiveCategoryId_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses?categoryId=0").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_request"));

		mockMvc.perform(get("/api/expenses?categoryId=-5").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	@DisplayName("Safe default: Filter by another user's category returns no results and never leaks data")
	void filterByAnotherUserCategory_returnsNoResultsWithoutLeak() throws Exception {
		String userA = registerAndLogin();
		String userB = registerAndLogin();

		Integer catA = categoryId(userA, "Food & Groceries");
		create(userA, "100.00", "2026-10-10", catA, "User A Secret Expense");

		// User B filters by User A's category ID
		mockMvc.perform(get("/api/expenses?categoryId=" + catA).header(HttpHeaders.AUTHORIZATION, userB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	@DisplayName("Safe default: Unauthenticated request is rejected with 401")
	void unauthenticatedRequest_returns401() throws Exception {
		mockMvc.perform(get("/api/expenses?categoryId=1"))
				.andExpect(status().isUnauthorized());
	}

	private Integer create(String token, String amount, String date, Integer categoryId, String description)
			throws Exception {
		String response = mockMvc
				.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "amount": %s,
								  "date": "%s",
								  "categoryId": %d,
								  "description": "%s"
								}
								""".formatted(amount, date, categoryId, description)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private Integer categoryId(String token, String name) throws Exception {
		String json = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(json, "$[?(@.name == '" + name + "')].id.min()");
	}

	private String registerAndLogin() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String email = "filt-" + suffix + "@example.com";
		String username = "filt_" + suffix;

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(email, username, PASSWORD))).andExpect(status().isCreated());

		return mockMvc
				.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "usernameOrEmail": "%s",
								  "password": "%s"
								}
								""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getHeader(HttpHeaders.AUTHORIZATION);
	}
}
