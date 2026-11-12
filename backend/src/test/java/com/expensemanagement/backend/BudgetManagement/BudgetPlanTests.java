package com.expensemanagement.backend.BudgetManagement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US28-US33: a budget is a self-dated plan, one at a time, whose split has to add up.
 *
 * <p>The rules under test are the ones that make a goal mean something: the window is the user's,
 * no two plans may cover the same days, a split must complete the total, edits stop after 6% or
 * three changes, and deleting a running plan takes its expenses with it.
 *
 * <p>The clock is moved by editing a budget's dates through the repository, which is how an ended
 * window is created without waiting for one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class BudgetPlanTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BudgetRepository budgetRepository;

	@Test
	void aPlanWithoutASplitIsAlsoValid() throws Exception {
		String token = registerAndLogin();

		String body = mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\""
						+ LocalDate.now().plusDays(6) + "\",\"overallAmount\":30000}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.allocations", hasSize(0)))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andReturn().getResponse().getContentAsString();

		String id = JsonPath.read(body, "$.id");
		mockMvc.perform(get("/api/budgets/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.name").doesNotExist())
			.andExpect(jsonPath("$.overallAmount").value(30000.00));
	}

	@Test
	void windowsMayNotOverlap() throws Exception {
		String token = registerAndLogin();

		createTestBudget(token, "First", 0, 6);

		// Starting on the last day of the first plan: an overlap.
		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + LocalDate.now().plusDays(6) + "\",\"endDate\":\""
						+ LocalDate.now().plusDays(13) + "\",\"overallAmount\":20000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("overlap another budget")));

		// Starting the day after it ends: fine.
		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + LocalDate.now().plusDays(7) + "\",\"endDate\":\""
						+ LocalDate.now().plusDays(13) + "\",\"overallAmount\":20000}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("UPCOMING"));
	}

	@Test
	void aWindowMustStartTodayOrLaterAndLastAtLeastAWeek() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + LocalDate.now().minusDays(1) + "\",\"endDate\":\""
						+ LocalDate.now().plusDays(10) + "\",\"overallAmount\":1000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("cannot start in the past")));

		mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"" + LocalDate.now() + "\",\"endDate\":\""
						+ LocalDate.now().plusDays(3) + "\",\"overallAmount\":1000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("at least 7 days")));
	}

	@Test
	void threeEditsAreAllowedAndTheFourthIsRefused() throws Exception {
		String token = registerAndLogin();
		String id = createTestBudget(token, "Editable", 0, 6);

		for (int edit = 1; edit <= 3; edit++) {
			mockMvc.perform(put("/api/budgets/" + id)
					.header(HttpHeaders.AUTHORIZATION, token)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"overallAmount\":" + (10000 + edit * 1000) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.editsUsed").value(edit));
		}

		mockMvc.perform(put("/api/budgets/" + id)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"overallAmount\":20000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("used its 3 edits")));
	}

	@Test
	void anExpenseJoinsABudgetOnlyInsideItsWindow() throws Exception {
		String token = registerAndLogin();
		String food = categoryId(token, "Food & Groceries");

		// A budget that starts in eight days - after the one created below ends, since only one
		// budget may run at a time: today's expense is outside it.
		String future = createTestBudget(token, "Starting soon", 8, 14);

		String today = LocalDate.now().toString();
		mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":500},\"date\":\"" + today + "\",\"categoryId\":\""
						+ food + "\",\"budgetId\":\"" + future + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("outside the budget's window")));

		// A budget that starts today: the same expense is accepted and counted.
		String running = createTestBudget(token, "Starting today", 0, 6);

		String created = mockMvc.perform(post("/api/expenses")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":500},\"date\":\"" + today + "\",\"categoryId\":\""
						+ food + "\",\"budgetId\":\"" + running + "\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.budget.id").value(running))
			.andReturn().getResponse().getContentAsString();

		String expenseId = JsonPath.read(created, "$.id");

		// Counting is by assignment, not by date: the budget's spending follows the link.
		mockMvc.perform(get("/api/budgets/" + running).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.spent").value(500.00))
			.andExpect(jsonPath("$.expenseCount").value(1))
			.andExpect(jsonPath("$.net").value(9500.00));

		// And it can be taken out again, because the budget is still open.
		mockMvc.perform(put("/api/expenses/" + expenseId)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":500},\"date\":\"" + today + "\",\"categoryId\":\""
						+ food + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.budget").doesNotExist());

		mockMvc.perform(get("/api/budgets/" + running).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.spent").value(0.00));
	}

	@Test
	void aFinishedBudgetCannotBeDeletedAndKeepsItsVerdict() throws Exception {
		String token = registerAndLogin();
		String id = createTestBudget(token, "Ended", 0, 6);

		// Move the window into the past, as if a week had gone by.
		Budget budget = budgetRepository.findById(UUID.fromString(id)).orElseThrow();
		budget.setStartDate(LocalDate.now().minusDays(10));
		budget.setEndDate(LocalDate.now().minusDays(4));
		budget.setStatus(com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus.ACTIVE);
		budget.setClosedAt(null);
		budgetRepository.saveAndFlush(budget);

		// Reading closes it, with a verdict, and announces it once.
		mockMvc.perform(get("/api/budgets/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.verdict").value("SUCCESS"))
			.andExpect(jsonPath("$.closedAt").value(notNullValue()));

		mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.unread").value(1));

		mockMvc.perform(delete("/api/budgets/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("cannot be deleted")));

		mockMvc.perform(put("/api/budgets/" + id)
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"overallAmount\":99999}"))
			.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/** A JSON body for {@code POST /budgets}, with an optional two-category split. */
	private String plan(String name, int startIn, int endIn, String total,
			String categoryA, String amountA, String categoryB, String amountB) {
		return "{\"name\":\"" + name + "\","
				+ "\"startDate\":\"" + LocalDate.now().plusDays(startIn) + "\","
				+ "\"endDate\":\"" + LocalDate.now().plusDays(endIn) + "\","
				+ "\"overallAmount\":" + total + ","
				+ "\"allocations\":["
				+ "{\"categoryId\":\"" + categoryA + "\",\"allocatedAmount\":" + amountA + "},"
				+ "{\"categoryId\":\"" + categoryB + "\",\"allocatedAmount\":" + amountB + "}"
				+ "]}";
	}

	private String createTestBudget(String token, String name, int startIn, int endIn) throws Exception {
		String body = mockMvc.perform(post("/api/budgets")
				.header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\","
						+ "\"startDate\":\"" + LocalDate.now().plusDays(startIn) + "\","
						+ "\"endDate\":\"" + LocalDate.now().plusDays(endIn) + "\","
						+ "\"overallAmount\":10000}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		java.util.List<String> ids = JsonPath.read(body, "$.data[?(@.name == '" + name + "')].id");
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
			.andReturn().getResponse().getContentAsString();

		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}
}
