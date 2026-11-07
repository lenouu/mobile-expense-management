package com.expensemanagement.backend.ExpenseManagement;

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
 * US22: View expense details integration tests.
 *
 * Covers:
 * - AC 1: User can open details of an expense they own and see all its fields.
 * - AC 2: Requesting non-existent expense returns 404.
 * - AC 3: Requesting another user's expense returns 404; unauthenticated requests return 401.
 * - AC 4: Invalid ID format (non-numeric or non-positive) returns 400.
 */
@org.junit.jupiter.api.condition.EnabledIf("isDockerAvailable")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseDetailsIntegrationTests {

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
	@DisplayName("AC 1: A user can open the details of an expense they own and see all its fields")
	void givenExistingExpense_whenUserRequestsDetails_thenAllFieldsAreReturned() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");

		Integer id = create(token, "45.50", "2026-11-05", food, "Weekly groceries");

		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.amount").value(45.5))
				.andExpect(jsonPath("$.date").value("2026-11-05"))
				.andExpect(jsonPath("$.description").value("Weekly groceries"))
				.andExpect(jsonPath("$.category.id").value(food))
				.andExpect(jsonPath("$.category.name").value("Food & Groceries"))
				.andExpect(jsonPath("$.category.color").value(notNullValue()))
				.andExpect(jsonPath("$.createdAt").value(notNullValue()))
				.andExpect(jsonPath("$.updatedAt").value(notNullValue()));
	}

	@Test
	@DisplayName("AC 2: Requesting a non-existent expense returns 404")
	void givenNonExistentExpense_whenRequested_returns404() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/999999").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	@DisplayName("AC 3: Requesting another user's expense returns 404 and does not leak data")
	void givenOtherUserExpense_whenRequested_returns404WithoutDataLeak() throws Exception {
		String userA = registerAndLogin();
		String userB = registerAndLogin();

		Integer categoryA = categoryId(userA, "Food & Groceries");
		Integer expenseA = create(userA, "100.00", "2026-11-01", categoryA, "Secret User A Expense");

		// User B attempts to view User A's expense
		mockMvc.perform(get("/api/expenses/" + expenseA).header(HttpHeaders.AUTHORIZATION, userB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	@DisplayName("AC 3: Unauthenticated requests without token are rejected with 401")
	void unauthenticatedRequest_returns401() throws Exception {
		mockMvc.perform(get("/api/expenses/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("AC 4: An invalid non-numeric ID format is rejected with 400 Bad Request")
	void invalidNonNumericId_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/not-a-number").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_parameter"));
	}

	@Test
	@DisplayName("AC 4: A non-positive ID (<= 0) is rejected with 400 Bad Request")
	void nonPositiveId_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/0").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_request"));

		mockMvc.perform(get("/api/expenses/-1").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.error").value("invalid_request"));
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
		String email = "det-" + suffix + "@example.com";
		String username = "det_" + suffix;

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
