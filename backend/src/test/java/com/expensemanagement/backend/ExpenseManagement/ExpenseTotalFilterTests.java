package com.expensemanagement.backend.ExpenseManagement;

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

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US39 revisited: {@code GET /api/expenses/total} accepts the same window and filters as the
 * listing, so the number above a list always describes that list.
 *
 * <p>Section 6.2 of the vision separates the financial period from the range you happen to be
 * looking at, which is why a total asked for with dates alone reports no period - and why the
 * window and the filters have to agree with the listing rather than approximate it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseTotalFilterTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aWindowSumsOnlyTheExpensesInsideIt() throws Exception {
		User user = registerAndLogin();
		String food = categoryId(user.token(), "Food & Groceries");
		String today = LocalDate.now().toString();
		String threeDaysAgo = LocalDate.now().minusDays(3).toString();
		String thirtyDaysAgo = LocalDate.now().minusDays(30).toString();

		create(user.token(), "100", today, food);
		create(user.token(), "200", threeDaysAgo, food);
		create(user.token(), "999", thirtyDaysAgo, food);

		mockMvc
			.perform(get("/api/expenses/total").param("from", threeDaysAgo).param("to", today)
				.header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total.amount").value(300))
			.andExpect(jsonPath("$.count").value(2))
			.andExpect(jsonPath("$.from").value(threeDaysAgo))
			.andExpect(jsonPath("$.to").value(today))
			// A window given as dates is not a financial period: the field is present and null.
			.andExpect(jsonPath("$.period").value(nullValue()));
	}

	@Test
	void aCategoryFilterNarrowsTheTotal() throws Exception {
		User user = registerAndLogin();
		String food = categoryId(user.token(), "Food & Groceries");
		String transport = categoryId(user.token(), "Transport");
		String today = LocalDate.now().toString();

		create(user.token(), "40", today, food);
		create(user.token(), "60", today, transport);

		mockMvc
			.perform(get("/api/expenses/total").param("categoryId", food)
				.header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total.amount").value(40))
			.andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void aTextFilterNarrowsTheTotal() throws Exception {
		User user = registerAndLogin();
		String food = categoryId(user.token(), "Food & Groceries");
		String today = LocalDate.now().toString();

		createWithDescription(user.token(), "25", today, food, "lunch with the team");
		createWithDescription(user.token(), "75", today, food, "taxi home");

		mockMvc
			.perform(get("/api/expenses/total").param("q", "lunch")
				.header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total.amount").value(25))
			.andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void theTotalAgreesWithTheListingForTheSameQuery() throws Exception {
		User user = registerAndLogin();
		String food = categoryId(user.token(), "Food & Groceries");
		String today = LocalDate.now().toString();
		String weekAgo = LocalDate.now().minusDays(6).toString();

		createWithDescription(user.token(), "10", today, food, "coffee");
		createWithDescription(user.token(), "20", today, food, "coffee beans");
		createWithDescription(user.token(), "30", today, food, "bread");

		String listBody = mockMvc
			.perform(get("/api/expenses").param("from", weekAgo).param("to", today).param("q", "coffee")
				.param("size", "50").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		List<Double> amounts = JsonPath.read(listBody, "$.data[*].amount.amount");
		double listed = amounts.stream().mapToDouble(Double::doubleValue).sum();

		String totalBody = mockMvc
			.perform(get("/api/expenses/total").param("from", weekAgo).param("to", today)
				.param("q", "coffee").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		double total = ((Number) JsonPath.read(totalBody, "$.total.amount")).doubleValue();
		int count = ((Number) JsonPath.read(totalBody, "$.count")).intValue();

		org.junit.jupiter.api.Assertions.assertEquals(listed, total, 0.001);
		org.junit.jupiter.api.Assertions.assertEquals(amounts.size(), count);
	}

	@Test
	void withoutAnyParametersTheCurrentPeriodIsUsed() throws Exception {
		String token = registerAndLogin().token();

		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.period.status").value("CURRENT"))
			.andExpect(jsonPath("$.from").exists())
			.andExpect(jsonPath("$.to").exists());
	}

	@Test
	void anInvertedWindowIsARefusedRequest() throws Exception {
		String token = registerAndLogin().token();
		String today = LocalDate.now().toString();
		String weekAgo = LocalDate.now().minusDays(6).toString();

		mockMvc
			.perform(get("/api/expenses/total").param("from", today).param("to", weekAgo)
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aWindowWithNothingInItTotalsZero() throws Exception {
		String token = registerAndLogin().token();

		mockMvc
			.perform(get("/api/expenses/total").param("from", "1999-01-01").param("to", "1999-01-31")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total.amount").value(0.0))
			.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void anotherUsersExpensesAreNeverCounted() throws Exception {
		User owner = registerAndLogin();
		User other = registerAndLogin();
		String today = LocalDate.now().toString();
		create(owner.token(), "500", today, categoryId(owner.token(), "Health"));
		create(other.token(), "5", today, categoryId(other.token(), "Health"));

		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, other.token()))
			.andExpect(jsonPath("$.total.amount").value(5))
			.andExpect(jsonPath("$.count").value(1));
	}

	private String create(String token, String amount, String date, String categoryId) throws Exception {
		return createWithDescription(token, amount, date, categoryId, null);
	}

	private String createWithDescription(String token, String amount, String date, String categoryId,
			String description) throws Exception {
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
		String descriptionField = description == null ? "" : ",\"description\":\"" + description + "\"";
		return "{\"amount\":{\"amount\":" + amount + "},\"date\":\"" + date + "\",\"categoryId\":\"" + categoryId
				+ "\"" + descriptionField + "}";
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").param("size", "50")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$.data[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private record User(UUID id, String token) {
	}

	private User registerAndLogin() throws Exception {
		String userName = "user." + UUID.randomUUID().toString().substring(0, 8);
		String registered = mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String body = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usernameOrEmail\":\"" + userName + "\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String id = JsonPath.read(registered, "$.id");
		return new User(UUID.fromString(id), "Bearer " + JsonPath.read(body, "$.accessToken"));
	}
}
