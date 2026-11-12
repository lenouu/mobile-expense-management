package com.expensemanagement.backend.PeriodManagement;

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
 * US27 end to end: {@code PUT /api/periods/settings} changes how long the caller's periods run.
 *
 * <p>The rules under test are the ones the endpoint's own documentation promises: the length is a
 * count of days, weeks or months; it must cover at least one week and no more than a year; and the
 * period in progress keeps its dates, with the new length applying from the next one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class PeriodSettingsTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aNewAccountStartsOnTheDefaultLength() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lengthCount").value(1))
			.andExpect(jsonPath("$.lengthUnit").value("MONTHS"))
			.andExpect(jsonPath("$.defaultLength").value(true))
			.andExpect(jsonPath("$.minimumDays").value(7));
	}

	@Test
	void changingTheLengthAppliesFromTheNextPeriodAndLeavesThisOneAlone() throws Exception {
		String token = registerAndLogin();

		String before = currentPeriod(token);
		String currentEnd = JsonPath.read(before, "$.endDate");
		String currentStart = JsonPath.read(before, "$.startDate");

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":2,\"unit\":\"WEEKS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lengthCount").value(2))
			.andExpect(jsonPath("$.lengthUnit").value("WEEKS"))
			.andExpect(jsonPath("$.defaultLength").value(false))
			// A change only takes effect at the next boundary, which is the day after this period.
			.andExpect(jsonPath("$.effectiveFrom").value(LocalDate.parse(currentEnd).plusDays(1).toString()));

		mockMvc.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.startDate").value(currentStart))
			.andExpect(jsonPath("$.endDate").value(currentEnd));
	}

	@Test
	void exactlyOneWeekIsAllowed() throws Exception {
		String token = registerAndLogin();

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":7,\"unit\":\"DAYS\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.lengthCount").value(7))
			.andExpect(jsonPath("$.lengthUnit").value("DAYS"));
	}

	@Test
	void shorterThanOneWeekIsRefused() throws Exception {
		String token = registerAndLogin();

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":6,\"unit\":\"DAYS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_request"));
	}

	@Test
	void longerThanOneYearIsRefused() throws Exception {
		String token = registerAndLogin();

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":13,\"unit\":\"MONTHS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_request"));
	}

	@Test
	void aMissingUnitOrANonPositiveCountIsAValidationFailure() throws Exception {
		String token = registerAndLogin();

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":2}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.fields.unit").exists());

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":0,\"unit\":\"WEEKS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.fields.count").exists());
	}

	@Test
	void theSettingsArePerAccount() throws Exception {
		String mine = registerAndLogin();
		String other = registerAndLogin();

		mockMvc
			.perform(put("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, mine)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":2,\"unit\":\"WEEKS\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.lengthCount").value(1))
			.andExpect(jsonPath("$.lengthUnit").value("MONTHS"))
			.andExpect(jsonPath("$.defaultLength").value(true));
	}

	@Test
	void changingTheLengthRequiresAToken() throws Exception {
		mockMvc
			.perform(put("/api/periods/settings").contentType(MediaType.APPLICATION_JSON)
				.content("{\"count\":2,\"unit\":\"WEEKS\"}"))
			.andExpect(status().isUnauthorized());
	}

	private String currentPeriod(String token) throws Exception {
		return mockMvc.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
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
