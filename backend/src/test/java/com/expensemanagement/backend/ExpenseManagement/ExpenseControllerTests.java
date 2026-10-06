package com.expensemanagement.backend.ExpenseManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * S2-TECH-2 end to end for expenses. Every test registers its own users, so the shared database
 * never leaks records between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseControllerTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void userCanCreateReadUpdateAndDeleteAnExpense() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		Integer transport = categoryId(token, "Transport");

		String created = mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("12.50", "2026-09-15", food, "  Market  ")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.amount").value(12.5))
			.andExpect(jsonPath("$.date").value("2026-09-15"))
			.andExpect(jsonPath("$.description").value("Market"))
			.andExpect(jsonPath("$.category.id").value(food))
			.andExpect(jsonPath("$.category.name").value("Food & Groceries"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(12.5));

		// Full replace: the omitted description is cleared.
		mockMvc
			.perform(put("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("30", "2026-09-16", transport, null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(30))
			.andExpect(jsonPath("$.date").value("2026-09-16"))
			.andExpect(jsonPath("$.category.name").value("Transport"))
			.andExpect(jsonPath("$.description").doesNotExist());

		mockMvc.perform(delete("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound());
	}

	@Test
	void listIsNewestFirstAndFilterableByDate() throws Exception {
		String token = registerAndLogin();
		Integer food = categoryId(token, "Food & Groceries");
		create(token, "1", "2026-08-01", food);
		create(token, "2", "2026-09-01", food);
		create(token, "3", "2026-09-20", food);

		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(3)))
			.andExpect(jsonPath("$.content[0].date").value("2026-09-20"))
			.andExpect(jsonPath("$.content[2].date").value("2026-08-01"))
			.andExpect(jsonPath("$.page.totalElements").value(3));

		mockMvc
			.perform(get("/api/expenses").param("from", "2026-09-01")
				.param("to", "2026-09-30")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.content", hasSize(2)));

		mockMvc
			.perform(get("/api/expenses").param("from", "2026-10-01")
				.param("to", "2026-09-01")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		String token = registerAndLogin();
		String tomorrow = LocalDate.now().plusDays(1).toString();

		mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":0,\"date\":\"" + tomorrow + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("validation_failed"))
			.andExpect(jsonPath("$.fieldErrors.amount").exists())
			.andExpect(jsonPath("$.fieldErrors.date").exists())
			.andExpect(jsonPath("$.fieldErrors.categoryId").exists());

		mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("1.999", "2026-09-01", categoryId(token, "Other"), null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.amount").exists());
	}

	@Test
	void usersCannotReachEachOthersExpensesOrCategories() throws Exception {
		String owner = registerAndLogin();
		String other = registerAndLogin();
		Integer ownersCategory = categoryId(owner, "Health");
		Integer id = create(owner, "40", "2026-09-10", ownersCategory);

		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(put("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, other)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("1", "2026-09-10", categoryId(other, "Health"), null)))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.content", hasSize(0)));

		// Filing an expense under someone else's category is refused.
		mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, other)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("5", "2026-09-10", ownersCategory, null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));

		// The owner's expense is untouched.
		mockMvc.perform(get("/api/expenses/" + id).header(HttpHeaders.AUTHORIZATION, owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(40));
	}

	@Test
	void expensesRequireAToken() throws Exception {
		mockMvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
	}

	private Integer create(String token, String amount, String date, Integer categoryId) throws Exception {
		String body = mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson(amount, date, categoryId, null)))
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
