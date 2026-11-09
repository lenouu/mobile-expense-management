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
 * - AC 2: Requesting non-existent expense returns 404 (code: not_found).
 * - AC 3: Requesting another user's expense returns 404; unauthenticated requests return 401.
 * - AC 4: Invalid ID format (invalid UUID) returns 400 (code: invalid_parameter).
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
		String food = categoryId(token, "Food & Groceries");

		String id = create(token, "45.50", "2026-11-05", food, "Weekly groceries");

		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.amount.amount").value(45.5))
				.andExpect(jsonPath("$.amount.currency").value("XAF"))
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

		mockMvc.perform(get("/api/expenses/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	@DisplayName("AC 3: Requesting another user's expense returns 404 and does not leak data")
	void givenOtherUserExpense_whenRequested_returns404WithoutDataLeak() throws Exception {
		String userA = registerAndLogin();
		String userB = registerAndLogin();

		String categoryA = categoryId(userA, "Food & Groceries");
		String expenseA = create(userA, "100.00", "2026-11-01", categoryA, "Secret User A Expense");

		// User B attempts to view User A's expense
		mockMvc.perform(get("/api/expenses/" + expenseA).header(HttpHeaders.AUTHORIZATION, userB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	@DisplayName("AC 3: Unauthenticated requests without token are rejected with 401")
	void unauthenticatedRequest_returns401() throws Exception {
		mockMvc.perform(get("/api/expenses/" + UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("AC 4: An invalid UUID format is rejected with 400 Bad Request (invalid_parameter)")
	void invalidUuidFormat_returns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/expenses/not-a-valid-uuid").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("invalid_parameter"));
	}

	private String create(String token, String amount, String date, String categoryId, String description)
			throws Exception {
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

	private static String expenseJson(String amount, String date, String categoryId, String description) {
		return "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"XAF\"},\"date\":\"" + date + "\",\"categoryId\":\""
				+ categoryId + "\"" + (description != null ? ",\"description\":\"" + description + "\"" : "") + "}";
	}

	private String categoryId(String token, String name) throws Exception {
		String json = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		java.util.List<String> ids = JsonPath.read(json, "$[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private String registerAndLogin() throws Exception {
		String userName = "det." + UUID.randomUUID().toString().substring(0, 8);
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
