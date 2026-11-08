package com.expensemanagement.backend.IncomeManagement;

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
 * S2-TECH-2 end to end for incomes. Every test registers its own users, so the shared database
 * never leaks records between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class IncomeControllerTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void userCanCreateReadUpdateAndDeleteAnIncome() throws Exception {
		String token = registerAndLogin();

		String created = mockMvc
			.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("1500", "2026-09-30", "  Salary  ", "September")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.amount.amount").value(1500))
			.andExpect(jsonPath("$.date").value("2026-09-30"))
			.andExpect(jsonPath("$.source").value("Salary"))
			.andExpect(jsonPath("$.description").value("September"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.source").value("Salary"));

		mockMvc
			.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("1600.75", "2026-09-29", "Salary + bonus", null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount.amount").value(1600.75))
			.andExpect(jsonPath("$.source").value("Salary + bonus"))
			.andExpect(jsonPath("$.description").doesNotExist());

		mockMvc.perform(delete("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound());
	}

	@Test
	void listIsNewestFirstAndFilterableByDate() throws Exception {
		String token = registerAndLogin();
		create(token, "100", "2026-07-31");
		create(token, "200", "2026-08-31");
		create(token, "300", "2026-09-30");

		mockMvc.perform(get("/api/incomes").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(3)))
			.andExpect(jsonPath("$.data[0].date").value("2026-09-30"))
			.andExpect(jsonPath("$.totalElements").value(3));

		mockMvc
			.perform(get("/api/incomes").param("from", "2026-08-01").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.data", hasSize(2)));
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		String tomorrow = LocalDate.now().plusDays(1).toString();
		mockMvc
			.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, registerAndLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":-5},\"date\":\"" + tomorrow + "\",\"source\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.fields['amount.amount']").exists())
			.andExpect(jsonPath("$.fields.date").exists())
			.andExpect(jsonPath("$.fields.source").exists());
	}

	@Test
	void usersCannotReachEachOthersIncomes() throws Exception {
		String owner = registerAndLogin();
		String other = registerAndLogin();
		String id = create(owner, "900", "2026-09-30");

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, other)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("1", "2026-09-30", "Hack", null)))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/incomes").header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.data", hasSize(0)));

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, owner))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount.amount").value(900));
	}

	@Test
	void incomesRequireAToken() throws Exception {
		mockMvc.perform(get("/api/incomes")).andExpect(status().isUnauthorized());
	}

	private String create(String token, String amount, String date) throws Exception {
		String body = mockMvc
			.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson(amount, date, "Salary", null)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String incomeJson(String amount, String date, String source, String description) {
		return "{\"amount\":{\"amount\":" + amount + "},\"date\":\"" + date + "\",\"source\":\"" + source + "\""
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
