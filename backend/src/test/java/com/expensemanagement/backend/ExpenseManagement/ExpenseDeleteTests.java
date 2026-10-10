package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * US24 end to end: deleting an expense with {@code DELETE /api/expenses/{id}}. The basic delete and
 * the "someone else's expense" case are in {@link ExpenseControllerTests}; these cover the rest of
 * the acceptance criteria. Every test registers its own users, so records never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseDeleteTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deletingRemovesExactlyThatExpenseAndKeepsEverythingElse() throws Exception {
		String token = registerAndLogin();
		String otherUser = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer wrong = create(token, "999", "2026-09-10", food);
		Integer kept = create(token, "12.50", "2026-09-11", food);
		Integer othersExpense = create(otherUser, "40", "2026-09-10", categoryId(otherUser, "Food & Groceries"));

		mockMvc.perform(delete("/api/expenses/" + wrong).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());

		// Gone from the list and the count: it no longer weighs on anything computed from expenses.
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].id").value(kept))
			.andExpect(jsonPath("$.content[0].amount").value(12.5))
			.andExpect(jsonPath("$.page.totalElements").value(1));
		mockMvc.perform(get("/api/expenses/" + wrong).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound());

		// Another user's expenses are untouched.
		mockMvc.perform(get("/api/expenses/" + othersExpense).header(HttpHeaders.AUTHORIZATION, otherUser))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(40));

		// The category stays: deleting an expense never deletes the category it was filed under.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$[*].id").value(hasItem(food)));
	}

	@Test
	void deletingTheSameExpenseTwiceIsNotFoundTheSecondTime() throws Exception {
		String token = registerAndLogin();
		Integer id = create(token, "5", "2026-09-10", categoryId(token, "Other"));

		mockMvc.perform(delete("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void deletingAnExpenseThatDoesNotExistIsNotFound() throws Exception {
		mockMvc.perform(delete("/api/expenses/999999999").header(HttpHeaders.AUTHORIZATION, registerAndLogin()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void deletingRequiresAToken() throws Exception {
		String token = registerAndLogin();
		Integer id = create(token, "5", "2026-09-10", categoryId(token, "Other"));

		mockMvc.perform(delete("/api/expenses/" + id)).andExpect(status().isUnauthorized());

		// Still there.
		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk());
	}

	private Integer create(String token, String amount, String date, Integer categoryId) throws Exception {
		String body = mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"categoryId\":" + categoryId + "}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private Integer categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
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
