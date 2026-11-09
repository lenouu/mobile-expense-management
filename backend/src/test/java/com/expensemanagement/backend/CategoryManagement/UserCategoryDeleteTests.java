package com.expensemanagement.backend.CategoryManagement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * US19 end to end: deleting an unused category with {@code DELETE /api/categories/{id}}.
 * Rejects deletion if any active expenses are linked to the category (422 Unprocessable Entity).
 * Every test registers its own users, so records never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class UserCategoryDeleteTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deletingUnusedCategoryRemovesIt() throws Exception {
		String token = registerAndLogin();
		String categoryId = createCategory(token, "Temporary Category");

		// Category is present.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem(categoryId)));

		// Delete unused category.
		mockMvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNoContent());

		// Category is gone.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(not(hasItem(categoryId))));
	}

	@Test
	void deletingCategoryWithLinkedExpensesReturns422UnprocessableEntity() throws Exception {
		String token = registerAndLogin();
		String categoryId = createCategory(token, "Active Category");

		// Create an expense linked to this category.
		createExpense(token, "25.00", "2026-09-15", categoryId);

		// Attempt to delete category with active expenses -> 422 Unprocessable Entity.
		mockMvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("business_rule_violation"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("is still used by")));

		// Category still exists.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem(categoryId)));
	}

	@Test
	void deletingCategoryTwiceReturns404NotFoundTheSecondTime() throws Exception {
		String token = registerAndLogin();
		String categoryId = createCategory(token, "Double Delete");

		mockMvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("not_found"));
	}

	@Test
	void deletingCategoryOfAnotherUserReturns404NotFound() throws Exception {
		String ownerToken = registerAndLogin();
		String otherToken = registerAndLogin();

		String categoryId = createCategory(ownerToken, "Owner Category");

		// Other user cannot delete owner's category.
		mockMvc.perform(delete("/api/categories/" + categoryId).header(HttpHeaders.AUTHORIZATION, otherToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("not_found"));

		// Category still exists for owner.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, ownerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem(categoryId)));
	}

	@Test
	void deletingCategoryRequiresToken() throws Exception {
		String token = registerAndLogin();
		String categoryId = createCategory(token, "Unprotected Attempt");

		mockMvc.perform(delete("/api/categories/" + categoryId))
				.andExpect(status().isUnauthorized());

		// Still exists.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem(categoryId)));
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private String createCategory(String token, String name) throws Exception {
		String body = mockMvc
				.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"icon\":\"tag\",\"color\":\"#1F9D55\"}"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createExpense(String token, String amount, String date, String categoryId) throws Exception {
		String body = mockMvc
				.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"XAF\"},\"date\":\"" + date + "\",\"categoryId\":\"" + categoryId + "\"}"))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.id");
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
