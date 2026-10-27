package com.expensemanagement.backend.Monitoring;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.expensemanagement.backend.Monitoring.Entities.ErrorLog;
import com.expensemanagement.backend.Monitoring.Repositories.ErrorLogRepository;
import com.expensemanagement.backend.Seeds.UserSeeds;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US10 end to end: real JWTs from {@code POST /api/auth/login} for the seeded accounts, against a
 * Testcontainers PostgreSQL.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.seed.enabled=true", "app.monitoring.simulate-error-enabled=true" })
@AutoConfigureMockMvc
class AdminMonitoringControllerTests {

	private static final String ADMIN = "admin.amina";

	private static final String USER = "sara.mansouri";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ErrorLogRepository errorLogRepository;

	@BeforeEach
	void clearErrorLogs() {
		errorLogRepository.deleteAll();
	}

	@Test
	void healthReportsApplicationAndDatabaseStatusToAdmin() throws Exception {
		mockMvc.perform(get("/api/admin/health").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.applicationName").value("expense-management-backend"))
			.andExpect(jsonPath("$.database.status").value("UP"))
			.andExpect(jsonPath("$.memory.status").value("UP"))
			.andExpect(jsonPath("$.disk.status").value("UP"))
			.andExpect(jsonPath("$.uptimeSeconds").value(greaterThanOrEqualTo(0)))
			.andExpect(jsonPath("$.errorsLast24Hours").value(0));
	}

	@Test
	void monitoringEndpointsRequireAToken() throws Exception {
		mockMvc.perform(get("/api/admin/health")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/logs/errors")).andExpect(status().isUnauthorized());
	}

	@Test
	void monitoringEndpointsRejectRegularUser() throws Exception {
		String userToken = bearer(USER);
		mockMvc.perform(get("/api/admin/health").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/logs/errors").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void unexpectedErrorIsRecordedAndListed() throws Exception {
		String adminToken = bearer(ADMIN);

		mockMvc.perform(post("/api/admin/monitoring/simulate-error").header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("internal_error"))
			.andExpect(jsonPath("$.message").value("An unexpected error occurred"));

		mockMvc.perform(get("/api/admin/logs/errors").header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].id").isNumber())
			.andExpect(jsonPath("$.content[0].occurredAt").isNotEmpty())
			.andExpect(jsonPath("$.content[0].httpMethod").value("POST"))
			.andExpect(jsonPath("$.content[0].path").value("/api/admin/monitoring/simulate-error"))
			.andExpect(jsonPath("$.content[0].status").value(500))
			.andExpect(jsonPath("$.content[0].exceptionType").value("java.lang.IllegalStateException"))
			.andExpect(jsonPath("$.content[0].message").value("Simulated error for monitoring test"))
			.andExpect(jsonPath("$.content[0].stackTrace").isNotEmpty())
			.andExpect(jsonPath("$.page.number").value(0))
			.andExpect(jsonPath("$.page.size").value(20))
			.andExpect(jsonPath("$.page.totalElements").value(1));

		mockMvc.perform(get("/api/admin/health").header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(jsonPath("$.errorsLast24Hours").value(1));
	}

	@Test
	void clientErrorsAreNotRecorded() throws Exception {
		String adminToken = bearer(ADMIN);

		// 405: wrong method on an existing endpoint keeps its status and is not a server error.
		mockMvc.perform(post("/api/admin/health").header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(get("/api/admin/logs/errors").param("from", "not-a-date")
			.header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/admin/logs/errors").header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(jsonPath("$.page.totalElements").value(0));
	}

	@Test
	void errorLogsAreFilteredByDateRange() throws Exception {
		errorLogRepository.save(new ErrorLog(Instant.parse("2026-01-10T10:00:00Z"), "GET", "/a", 500,
				"java.lang.RuntimeException", "old", null));
		errorLogRepository.save(new ErrorLog(Instant.parse("2026-03-10T10:00:00Z"), "GET", "/b", 500,
				"java.lang.RuntimeException", "new", null));

		mockMvc
			.perform(get("/api/admin/logs/errors").param("from", "2026-02-01T00:00:00Z")
				.param("to", "2026-04-01T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].message").value("new"));
	}

	@Test
	void errorLogsArePaginatedNewestFirst() throws Exception {
		for (int day = 1; day <= 3; day++) {
			errorLogRepository.save(new ErrorLog(Instant.parse("2026-05-0" + day + "T10:00:00Z"), "GET", "/x", 500,
					"java.lang.RuntimeException", "day " + day, null));
		}
		String adminToken = bearer(ADMIN);

		mockMvc
			.perform(get("/api/admin/logs/errors").param("page", "0")
				.param("size", "2")
				.header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(2)))
			.andExpect(jsonPath("$.content[0].message").value("day 3"))
			.andExpect(jsonPath("$.page.totalElements").value(3))
			.andExpect(jsonPath("$.page.totalPages").value(2));

		mockMvc
			.perform(get("/api/admin/logs/errors").param("page", "1")
				.param("size", "2")
				.header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].message").value("day 1"));
	}

	@Test
	void fromAfterToIsRejected() throws Exception {
		mockMvc
			.perform(get("/api/admin/logs/errors").param("from", "2026-05-01T00:00:00Z")
				.param("to", "2026-04-01T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	private String bearer(String userName) throws Exception {
		String body = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"usernameOrEmail\":\"" + userName + "\",\"password\":\"" + UserSeeds.DEFAULT_PASSWORD
						+ "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

}
