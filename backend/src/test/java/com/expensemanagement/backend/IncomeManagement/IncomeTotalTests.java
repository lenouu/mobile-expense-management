package com.expensemanagement.backend.IncomeManagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * US13 end to end: total income for a financial period via {@code GET /api/incomes/total}.
 * Every test registers its own user, so the shared database never leaks records between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class IncomeTotalTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	// ---- Acceptance Criterion 1: calculate total income ----

	@Test
	void singleIncomeReturnsThatAmountAsTotal() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);
		String periodEnd = currentPeriodEnd(token);

		create(token, "1500.00", periodStart, "Salary");

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1500.00))
			.andExpect(jsonPath("$.periodId").value(periodId))
			.andExpect(jsonPath("$.periodStartDate").value(periodStart))
			.andExpect(jsonPath("$.periodEndDate").value(periodEnd))
			.andExpect(jsonPath("$.recordCount").value(1));
	}

	@Test
	void multipleIncomesReturnTheirSum() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		create(token, "1000.00", periodStart, "Salary");
		create(token, "250.50", periodStart, "Freelance");
		create(token, "99.50", periodStart, "Refund");

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1350.00))
			.andExpect(jsonPath("$.recordCount").value(3));
	}

	@Test
	void periodWithNoRecordsReturnsZero() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.recordCount").value(0));
	}

	@Test
	void recordsOutsideThePeriodAreExcluded() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		// This record is inside the period.
		create(token, "500.00", periodStart, "Inside");

		// This record is outside the period (well before the start).
		create(token, "999.00", "2020-01-15", "Outside");

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(500.00))
			.andExpect(jsonPath("$.recordCount").value(1));
	}

	@Test
	void anotherUsersIncomeIsExcluded() throws Exception {
		String ownerToken = registerAndLogin();
		String otherToken = registerAndLogin();

		Long ownerPeriodId = currentPeriodId(ownerToken);
		String ownerPeriodStart = currentPeriodStart(ownerToken);

		// Both create income on the same date.
		create(ownerToken, "100.00", ownerPeriodStart, "Owner income");
		create(otherToken, "999.00", ownerPeriodStart, "Other income");

		// Owner's total only includes their own.
		mockMvc.perform(get("/api/incomes/total").param("periodId", ownerPeriodId.toString())
				.header(HttpHeaders.AUTHORIZATION, ownerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(100.00))
			.andExpect(jsonPath("$.recordCount").value(1));

		// The other user cannot access owner's period.
		mockMvc.perform(get("/api/incomes/total").param("periodId", ownerPeriodId.toString())
				.header(HttpHeaders.AUTHORIZATION, otherToken))
			.andExpect(status().isNotFound());
	}

	@Test
	void decimalAmountsAreSummedAccurately() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		// Amounts that would fail with floating-point: 0.1 + 0.2 ≠ 0.3 in IEEE 754.
		create(token, "0.10", periodStart, "A");
		create(token, "0.20", periodStart, "B");

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0.30));
	}

	// ---- Acceptance Criterion 2: recalculate after modifications ----

	@Test
	void creatingAnIncomeUpdatesTheTotal() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		// Start with zero.
		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(0));

		// Add an income.
		create(token, "200.00", periodStart, "Salary");

		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(200.00))
			.andExpect(jsonPath("$.recordCount").value(1));
	}

	@Test
	void editingAnIncomeUpdatesTheTotal() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		Integer id = create(token, "300.00", periodStart, "Salary");

		// Total is 300.
		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(300.00));

		// Edit to 500.
		mockMvc.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("500.00", periodStart, "Salary revised", null)))
			.andExpect(status().isOk());

		// Total is now 500.
		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(500.00))
			.andExpect(jsonPath("$.recordCount").value(1));
	}

	@Test
	void deletingAnIncomeUpdatesTheTotal() throws Exception {
		String token = registerAndLogin();
		Long periodId = currentPeriodId(token);
		String periodStart = currentPeriodStart(token);

		Integer id1 = create(token, "100.00", periodStart, "A");
		Integer id2 = create(token, "200.00", periodStart, "B");

		// Total is 300.
		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(300.00))
			.andExpect(jsonPath("$.recordCount").value(2));

		// Delete the first income.
		mockMvc.perform(delete("/api/incomes/" + id1).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());

		// Total is now 200.
		mockMvc.perform(get("/api/incomes/total").param("periodId", periodId.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.total").value(200.00))
			.andExpect(jsonPath("$.recordCount").value(1));
	}

	// ---- Error cases ----

	@Test
	void nonExistentPeriodReturnsNotFound() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/incomes/total").param("periodId", "999999999")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void missingPeriodIdReturns400() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/incomes/total")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isBadRequest());
	}

	@Test
	void totalRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/incomes/total").param("periodId", "1"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void existingCrudStillWorks() throws Exception {
		String token = registerAndLogin();
		String periodStart = currentPeriodStart(token);

		Integer id = create(token, "42.00", periodStart, "Salary");

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(42.00));

		mockMvc.perform(delete("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound());
	}

	// ---- helpers ----

	private Integer create(String token, String amount, String date, String source) throws Exception {
		String body = mockMvc
			.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson(amount, date, source, null)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private Long currentPeriodId(String token) throws Exception {
		String body = mockMvc
			.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String currentPeriodStart(String token) throws Exception {
		String body = mockMvc
			.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.startDate");
	}

	private String currentPeriodEnd(String token) throws Exception {
		String body = mockMvc
			.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.endDate");
	}

	private static String incomeJson(String amount, String date, String source, String description) {
		return "{\"amount\":" + amount + ",\"date\":\"" + date + "\",\"source\":\"" + source + "\""
				+ (description != null ? ",\"description\":\"" + description + "\"" : "") + "}";
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
