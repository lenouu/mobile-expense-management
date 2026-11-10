package com.expensemanagement.backend.CategoryManagement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * The shape and behaviour of {@code GET /api/categories}: a page envelope, a name filter, and
 * isolation between accounts.
 *
 * <p>The paged envelope is the contract every listing in the API already used, so these tests pin
 * the response shape as well as the filtering - a client that reads a bare array would break
 * silently otherwise.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class CategorySearchTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theListingIsAPageEnvelopeOfTenByDefault() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(10))
			.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void aPageReturnsOnlyItsOwnSlice() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").param("page", "0").param("size", "3")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.totalElements").value(10))
			.andExpect(jsonPath("$.totalPages").value(4));

		mockMvc.perform(get("/api/categories").param("page", "3").param("size", "3")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void categoriesComeBackSortedByName() throws Exception {
		String token = registerAndLogin();
		String body = mockMvc.perform(get("/api/categories").param("size", "50")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		List<String> names = JsonPath.read(body, "$.data[*].name");
		List<String> sorted = names.stream().sorted().toList();
		org.junit.jupiter.api.Assertions.assertEquals(sorted, names);
	}

	@Test
	void searchMatchesPartOfANameIgnoringCase() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").param("q", "food")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].name").value(hasItem(containsString("Food"))))
			.andExpect(jsonPath("$.totalElements").value(1));

		// Upper case in, mixed case in the data: the match is case-insensitive either way.
		mockMvc.perform(get("/api/categories").param("q", "OOD")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void aSearchWithNoMatchIsAnEmptyPageNotAnError() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").param("q", "no-such-category")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void aBlankSearchIsTheSameAsNoSearch() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").param("q", " ").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(10));
	}

	@Test
	void searchAndPagingWorkTogether() throws Exception {
		String token = registerAndLogin();

		mockMvc.perform(get("/api/categories").param("q", "e").param("page", "0").param("size", "2")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.totalPages").value(4));

		mockMvc.perform(get("/api/categories").param("q", "e").param("page", "1").param("size", "2")
			.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void searchNeverLeavesTheCallersOwnCategories() throws Exception {
		String mine = registerAndLogin();
		String other = registerAndLogin();
		String name = "Only " + UUID.randomUUID().toString().substring(0, 8);

		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, mine)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/categories").param("q", name).header(HttpHeaders.AUTHORIZATION, mine))
			.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get("/api/categories").param("q", name).header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.data[*].name").value(not(hasItem(name))));
	}

	@Test
	void theListingRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
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
