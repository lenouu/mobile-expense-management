package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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
 * US21: View expense history integration tests.
 *
 * Acceptance Criteria:
 * 1. Given the user has recorded expenses, when they open the history screen,
 *    then expenses are listed with amount, category, date and description, most recent first.
 * 2. Given no expenses have been recorded yet, then the screen shows an empty state rather than an error.
 *
 * Additional safe defaults:
 * - Only the authenticated user's own expenses are returned; unauthenticated requests are rejected.
 * - Ordering is by expense date descending, with created time descending as tiebreaker.
 */
@org.junit.jupiter.api.condition.EnabledIf("isDockerAvailable")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseHistoryIntegrationTests {

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
	@DisplayName("AC 1: Recorded expenses are listed with amount, category, date, description, and id - newest first")
	void givenRecordedExpenses_whenUserViewsHistory_thenExpensesAreListedWithAllFieldsAndMostRecentFirst() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		// Record three expenses on different dates
		create(token, "15.00", "2026-09-01", food, "Supermarket");
		create(token, "45.50", "2026-09-15", transport, "Bus monthly pass");
		create(token, "8.25", "2026-09-28", food, "Bakery breakfast");

		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(3)))
				// Item 0: newest date (2026-09-28)
				.andExpect(jsonPath("$.content[0].id").isNumber())
				.andExpect(jsonPath("$.content[0].amount").value(8.25))
				.andExpect(jsonPath("$.content[0].date").value("2026-09-28"))
				.andExpect(jsonPath("$.content[0].description").value("Bakery breakfast"))
				.andExpect(jsonPath("$.content[0].category.id").value(food))
				.andExpect(jsonPath("$.content[0].category.name").value("Food & Groceries"))
				.andExpect(jsonPath("$.content[0].category.color").value(notNullValue()))
				// Item 1: middle date (2026-09-15)
				.andExpect(jsonPath("$.content[1].amount").value(45.5))
				.andExpect(jsonPath("$.content[1].date").value("2026-09-15"))
				.andExpect(jsonPath("$.content[1].description").value("Bus monthly pass"))
				.andExpect(jsonPath("$.content[1].category.id").value(transport))
				// Item 2: oldest date (2026-09-01)
				.andExpect(jsonPath("$.content[2].amount").value(15.0))
				.andExpect(jsonPath("$.content[2].date").value("2026-09-01"))
				.andExpect(jsonPath("$.content[2].description").value("Supermarket"))
				.andExpect(jsonPath("$.page.totalElements").value(3));
	}

	@Test
	@DisplayName("AC 2: When no expenses have been recorded yet, returns an empty list (200 OK) rather than an error")
	void givenNoExpenses_whenUserViewsHistory_thenEmptyListReturnedWithoutError() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.page.totalElements").value(0))
				.andExpect(jsonPath("$.page.totalPages").value(0));
	}

	@Test
	@DisplayName("Safe default: Same date expenses use created time descending as tiebreaker")
	void givenSameDateExpenses_thenCreatedAtTieBreakerOrdersMostRecentFirst() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");

		// Both recorded on the same date: 2026-10-10
		Integer firstId = create(token, "10.00", "2026-10-10", food, "First created");
		Integer secondId = create(token, "20.00", "2026-10-10", food, "Second created");

		// Second created should come first because createdAt is descending
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.content[0].id").value(secondId))
				.andExpect(jsonPath("$.content[0].description").value("Second created"))
				.andExpect(jsonPath("$.content[1].id").value(firstId))
				.andExpect(jsonPath("$.content[1].description").value("First created"));
	}

	@Test
	@DisplayName("Safe default: Unauthenticated requests to view expenses are rejected with 401 Unauthorized")
	void unauthenticatedRequestIsRejectedWith401() throws Exception {
		mockMvc.perform(get("/api/expenses"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Safe default: Only the authenticated user's own expenses are returned (user isolation)")
	void onlyAuthenticatedUsersOwnExpensesAreReturned() throws Exception {
		String userA = registerAndLogin();
		String userB = registerAndLogin();

		Integer categoryA = categoryId(userA, "Health");
		create(userA, "100.00", "2026-11-01", categoryA, "User A medical expense");

		// User A sees their expense
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, userA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].description").value("User A medical expense"));

		// User B cannot see User A's expense
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, userB))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content", hasSize(0)))
				.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	// ─── Helpers ──────────────────────────────────────────────────────────────────

	private Integer create(String token, String amount, String date, Integer categoryId, String description) throws Exception {
		String body = mockMvc
				.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(expenseJson(amount, date, categoryId, description)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String expenseJson(String amount, String date, Integer categoryId, String description) {
		return "{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"categoryId\":" + categoryId
				+ (description != null ? ",\"description\":\"" + description + "\"" : "") + "}";
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
