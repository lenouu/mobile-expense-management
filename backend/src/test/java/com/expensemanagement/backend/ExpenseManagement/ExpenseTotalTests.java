package com.expensemanagement.backend.ExpenseManagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodSettingsRepository;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US39 end to end: {@code GET /api/expenses/total} sums a user's expenses for a financial period.
 * Every test registers its own user, so records never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class ExpenseTotalTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FinancialPeriodService periodService;

	@Autowired
	private FinancialPeriodSettingsRepository settingsRepository;

	@Test
	void theTotalIsTheExactSumOfTheCurrentPeriodsExpenses() throws Exception {
		User user = registerAndLogin();
		String today = LocalDate.now().toString();
		Integer food = categoryId(user.token(), "Food & Groceries");
		create(user.token(), "12.50", today, food);
		create(user.token(), "0.25", today, food);
		create(user.token(), "100", today, categoryId(user.token(), "Transport"));

		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(112.75))
			.andExpect(jsonPath("$.count").value(3))
			.andExpect(jsonPath("$.period.sequence").value(1))
			.andExpect(jsonPath("$.period.status").value("CURRENT"))
			.andExpect(jsonPath("$.period.startDate").value(today));
	}

	@Test
	void aPeriodWithoutExpensesTotalsZero() throws Exception {
		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, registerAndLogin().token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0.0))
			.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void onlyExpensesDatedInsideThePeriodCountBothEndsIncluded() throws Exception {
		User user = registerAndLogin();
		LocalDate today = LocalDate.now();
		// Weekly periods since 20 days ago: [t-20, t-14], [t-13, t-7], [t-6, t].
		FinancialPeriodSettings settings = periodService.settings(user.id());
		settings.setLengthCount(1);
		settings.setLengthUnit(PeriodUnit.WEEKS);
		settings.setAnchorDate(today.minusDays(20));
		settingsRepository.save(settings);
		Long middle = periodService.containing(user.id(), today.minusDays(10)).orElseThrow().getId();

		Integer other = categoryId(user.token(), "Other");
		create(user.token(), "1", today.minusDays(14).toString(), other); // last day of the previous period
		create(user.token(), "10", today.minusDays(13).toString(), other); // first day
		create(user.token(), "20", today.minusDays(7).toString(), other); // last day
		create(user.token(), "1000", today.minusDays(6).toString(), other); // first day of the next period

		mockMvc
			.perform(get("/api/expenses/total").param("periodId", middle.toString())
				.header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(30))
			.andExpect(jsonPath("$.count").value(2))
			.andExpect(jsonPath("$.period.sequence").value(2))
			.andExpect(jsonPath("$.period.status").value("PAST"))
			.andExpect(jsonPath("$.period.startDate").value(today.minusDays(13).toString()))
			.andExpect(jsonPath("$.period.endDate").value(today.minusDays(7).toString()));

		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(jsonPath("$.total").value(1000))
			.andExpect(jsonPath("$.period.sequence").value(3));
	}

	@Test
	void editingOrDeletingAnExpenseIsReflectedImmediately() throws Exception {
		User user = registerAndLogin();
		String today = LocalDate.now().toString();
		Integer food = categoryId(user.token(), "Food & Groceries");
		Integer wrong = create(user.token(), "500", today, food);
		create(user.token(), "20", today, food);

		mockMvc
			.perform(put("/api/expenses/" + wrong).header(HttpHeaders.AUTHORIZATION, user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("50", today, food)))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(jsonPath("$.total").value(70))
			.andExpect(jsonPath("$.count").value(2));

		mockMvc.perform(delete("/api/expenses/" + wrong).header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(jsonPath("$.total").value(20))
			.andExpect(jsonPath("$.count").value(1));
	}

	@Test
	void otherUsersExpensesAndPeriodsAreNeverCounted() throws Exception {
		User owner = registerAndLogin();
		User other = registerAndLogin();
		String today = LocalDate.now().toString();
		create(owner.token(), "75", today, categoryId(owner.token(), "Health"));
		create(other.token(), "5", today, categoryId(other.token(), "Health"));

		mockMvc.perform(get("/api/expenses/total").header(HttpHeaders.AUTHORIZATION, other.token()))
			.andExpect(jsonPath("$.total").value(5))
			.andExpect(jsonPath("$.count").value(1));

		// Someone else's period, or one that does not exist: 404, nothing revealed.
		Long ownersPeriod = periodService.current(owner.id()).getId();
		mockMvc
			.perform(get("/api/expenses/total").param("periodId", ownersPeriod.toString())
				.header(HttpHeaders.AUTHORIZATION, other.token()))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(get("/api/expenses/total").param("periodId", "999999999")
				.header(HttpHeaders.AUTHORIZATION, other.token()))
			.andExpect(status().isNotFound());
	}

	@Test
	void theTotalRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/expenses/total")).andExpect(status().isUnauthorized());
	}

	private Integer create(String token, String amount, String date, Integer categoryId) throws Exception {
		String body = mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson(amount, date, categoryId)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String expenseJson(String amount, String date, Integer categoryId) {
		return "{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"categoryId\":" + categoryId + "}";
	}

	private Integer categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
		return ids.get(0);
	}

	private record User(Long id, String token) {
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
		Integer id = JsonPath.read(registered, "$.id");
		return new User(id.longValue(), "Bearer " + JsonPath.read(body, "$.accessToken"));
	}

}
