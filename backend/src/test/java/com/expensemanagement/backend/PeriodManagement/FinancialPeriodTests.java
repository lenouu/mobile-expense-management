package com.expensemanagement.backend.PeriodManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriodSettings;
import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import com.expensemanagement.backend.PeriodManagement.Repositories.FinancialPeriodSettingsRepository;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * S4-TECH-1 end to end: the period service the budget, savings and debt stories will call, and
 * the read endpoints. Every test registers its own user, so periods never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class FinancialPeriodTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FinancialPeriodService periodService;

	@Autowired
	private FinancialPeriodSettingsRepository settingsRepository;

	@Test
	void aNewUserStartsWithOneCalendarMonthFromSignUp() throws Exception {
		User user = registerAndLogin();
		LocalDate today = LocalDate.now();

		mockMvc.perform(get("/api/periods/current").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sequence").value(1))
			.andExpect(jsonPath("$.startDate").value(today.toString()))
			.andExpect(jsonPath("$.endDate").value(today.plusMonths(1).minusDays(1).toString()))
			.andExpect(jsonPath("$.lengthCount").value(1))
			.andExpect(jsonPath("$.lengthUnit").value("MONTHS"))
			.andExpect(jsonPath("$.status").value("CURRENT"));

		mockMvc.perform(get("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultLength").value(true))
			.andExpect(jsonPath("$.minimumDays").value(7));
	}

	@Test
	void pastPeriodsAreCreatedOnDemandWithoutGaps() throws Exception {
		User user = registerAndLogin();
		// As if the account had been created on 31 January 2026 with monthly periods.
		FinancialPeriodSettings settings = periodService.settings(user.id());
		settings.setAnchorDate(LocalDate.of(2026, 1, 31));
		settingsRepository.save(settings);

		FinancialPeriod march = periodService.containing(user.id(), LocalDate.of(2026, 3, 15)).orElseThrow();
		assertThat(march.getSequence()).isEqualTo(2);
		assertThat(march.getStartDate()).isEqualTo("2026-02-28");
		assertThat(march.getEndDate()).isEqualTo("2026-03-30");

		FinancialPeriod first = periodService.previous(march).orElseThrow();
		assertThat(first.getStartDate()).isEqualTo("2026-01-31");
		assertThat(first.getEndDate().plusDays(1)).isEqualTo(march.getStartDate());
		assertThat(periodService.previous(first)).isEmpty();

		// Before the first period: no period, rather than an invented one.
		assertThat(periodService.containing(user.id(), LocalDate.of(2026, 1, 30))).isEmpty();

		// The history runs from the current period back to the first, newest first.
		String body = mockMvc.perform(get("/api/periods").param("size", "100").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].status").value("CURRENT"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer total = JsonPath.read(body, "$.page.totalElements");
		Integer firstSequenceListed = JsonPath.read(body, "$.content[0].sequence");
		assertThat(total).isEqualTo(firstSequenceListed);
		String oldest = JsonPath.read(body, "$.content[" + (total - 1) + "].startDate");
		assertThat(oldest).isEqualTo("2026-01-31");
	}

	@Test
	void aLengthChangeKeepsTheCurrentPeriodAndAppliesFromTheNextOne() throws Exception {
		User user = registerAndLogin();
		FinancialPeriod current = periodService.current(user.id());
		// Created ahead of time with the old length, e.g. for a debt carry-forward.
		periodService.next(current);

		FinancialPeriodSettings settings = periodService.changeLength(user.id(), 2, PeriodUnit.WEEKS);
		assertThat(settings.isDefaultLength()).isFalse();
		assertThat(settings.getAnchorDate()).isEqualTo(current.getEndDate().plusDays(1));

		FinancialPeriod stillCurrent = periodService.current(user.id());
		assertThat(stillCurrent.getId()).isEqualTo(current.getId());
		assertThat(stillCurrent.getEndDate()).isEqualTo(current.getEndDate());

		FinancialPeriod next = periodService.next(stillCurrent);
		assertThat(next.getSequence()).isEqualTo(current.getSequence() + 1);
		assertThat(next.getStartDate()).isEqualTo(current.getEndDate().plusDays(1));
		assertThat(next.getEndDate()).isEqualTo(next.getStartDate().plusDays(13));
		assertThat(next.getLengthUnit()).isEqualTo(PeriodUnit.WEEKS);

		mockMvc.perform(get("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, user.token()))
			.andExpect(jsonPath("$.lengthCount").value(2))
			.andExpect(jsonPath("$.lengthUnit").value("WEEKS"))
			.andExpect(jsonPath("$.defaultLength").value(false))
			.andExpect(jsonPath("$.effectiveFrom").value(next.getStartDate().toString()));
	}

	@Test
	void lengthsShorterThanOneWeekOrLongerThanOneYearAreRefused() throws Exception {
		Long userId = registerAndLogin().id();

		assertThatThrownBy(() -> periodService.changeLength(userId, 6, PeriodUnit.DAYS))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("at least one week");
		assertThatThrownBy(() -> periodService.changeLength(userId, 0, PeriodUnit.WEEKS))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> periodService.changeLength(userId, 13, PeriodUnit.MONTHS))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("at most one year");

		// Exactly one week is fine, in either unit.
		assertThat(periodService.changeLength(userId, 7, PeriodUnit.DAYS).getLengthCount()).isEqualTo(7);
		assertThat(periodService.changeLength(userId, 1, PeriodUnit.WEEKS).getLengthUnit()).isEqualTo(PeriodUnit.WEEKS);
	}

	@Test
	void theOpeningWindowCoversTheFirstDaysOfThePeriod() throws Exception {
		FinancialPeriod current = periodService.current(registerAndLogin().id());
		LocalDate windowEnd = periodService.openingWindowEnd(current, 6);

		assertThat(periodService.isInOpeningWindow(current, 6, current.getStartDate())).isTrue();
		assertThat(periodService.isInOpeningWindow(current, 6, windowEnd)).isTrue();
		assertThat(periodService.isInOpeningWindow(current, 6, windowEnd.plusDays(1))).isFalse();
		assertThat(periodService.isInOpeningWindow(current, 6, current.getStartDate().minusDays(1))).isFalse();
	}

	@Test
	void datesFarInTheFutureAreRefused() throws Exception {
		Long userId = registerAndLogin().id();

		assertThatThrownBy(() -> periodService.containing(userId, LocalDate.now().plusYears(2)))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void eachUserOnlySeesTheirOwnPeriodsAndATokenIsRequired() throws Exception {
		User first = registerAndLogin();
		User second = registerAndLogin();
		periodService.changeLength(first.id(), 2, PeriodUnit.WEEKS);

		mockMvc.perform(get("/api/periods/settings").header(HttpHeaders.AUTHORIZATION, second.token()))
			.andExpect(jsonPath("$.lengthUnit").value("MONTHS"))
			.andExpect(jsonPath("$.defaultLength").value(true));
		mockMvc.perform(get("/api/periods").header(HttpHeaders.AUTHORIZATION, second.token()))
			.andExpect(jsonPath("$.page.totalElements").value(1));

		mockMvc.perform(get("/api/periods/current")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/periods")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/periods/settings")).andExpect(status().isUnauthorized());
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
