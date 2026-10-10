package com.expensemanagement.backend.IncomeManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * US14 end to end: editing an income with {@code PUT /api/incomes/{id}}. The happy path and the
 * "someone else's income" case are in {@link IncomeControllerTests}; these cover the rest of the
 * acceptance criteria. Every test registers its own user, so records never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class IncomeEditTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void editReplacesTheRecordEverywhereAndRecordsWhenItHappened() throws Exception {
		String token = registerAndLogin();
		String created = create(token, "100", "2026-09-01", "Salary");
		Integer id = JsonPath.read(created, "$.id");
		String createdAt = JsonPath.read(created, "$.createdAt");

		// Make sure the edit lands on a later clock tick than the creation.
		Thread.sleep(20);

		String edited = mockMvc
			.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("1000", "2026-09-02", "Salary", "Was missing a zero")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.amount").value(1000))
			.andExpect(jsonPath("$.date").value("2026-09-02"))
			.andExpect(jsonPath("$.description").value("Was missing a zero"))
			// The creation time is kept; only updatedAt moves.
			.andExpect(jsonPath("$.createdAt").value(createdAt))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(LocalDateTime.parse(JsonPath.read(edited, "$.updatedAt")))
			.isAfter(LocalDateTime.parse(createdAt));

		// The list holds the corrected record only - the old amount is gone, not duplicated.
		mockMvc.perform(get("/api/incomes").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].id").value(id))
			.andExpect(jsonPath("$.content[0].amount").value(1000));
	}

	@Test
	void invalidEditIsRejectedAndLeavesTheIncomeUnchanged() throws Exception {
		String token = registerAndLogin();
		Integer id = JsonPath.read(create(token, "250", "2026-09-10", "Freelance"), "$.id");
		String tomorrow = LocalDate.now().plusDays(1).toString();

		mockMvc
			.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":0,\"date\":\"" + tomorrow + "\",\"source\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("validation_failed"))
			.andExpect(jsonPath("$.fieldErrors.amount").exists())
			.andExpect(jsonPath("$.fieldErrors.date").exists())
			.andExpect(jsonPath("$.fieldErrors.source").exists());

		mockMvc
			.perform(put("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("10.555", "2026-09-10", "Freelance", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.amount").exists());

		mockMvc.perform(get("/api/incomes/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(250))
			.andExpect(jsonPath("$.date").value("2026-09-10"))
			.andExpect(jsonPath("$.source").value("Freelance"));
	}

	@Test
	void editingAnIncomeThatDoesNotExistIsNotFound() throws Exception {
		mockMvc
			.perform(put("/api/incomes/999999999").header(HttpHeaders.AUTHORIZATION, registerAndLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("10", "2026-09-10", "Salary", null)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("not_found"));
	}

	@Test
	void editingRequiresAToken() throws Exception {
		mockMvc
			.perform(put("/api/incomes/1").contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson("10", "2026-09-10", "Salary", null)))
			.andExpect(status().isUnauthorized());
	}

	private String create(String token, String amount, String date, String source) throws Exception {
		return mockMvc
			.perform(post("/api/incomes").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(incomeJson(amount, date, source, null)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
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
