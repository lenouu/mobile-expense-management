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

import org.junit.jupiter.api.Assertions;
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
 * <p>Nothing here assumes how many categories a new account starts with. The database is shared by
 * the whole suite and other test classes add active defaults, so a freshly registered account can
 * begin with more than the seeds define. Every count is therefore compared with the live list of
 * active defaults rather than with a hard-coded ten.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class CategorySearchTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theListingIsAPageEnvelopeOfTwentyByDefault() throws Exception {
		String token = registerAndLogin();

		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andReturn()
			.getResponse()
			.getContentAsString();

		// The count is whatever this account was provisioned with - read from the response itself,
		// because the suite shares one database and other classes add defaults as it runs.
		int total = ((Number) JsonPath.read(body, "$.totalElements")).intValue();
		Assertions.assertEquals(Math.min(20, total), ((Number) JsonPath.read(body, "$.data.length()")).intValue());
		Assertions.assertEquals(pages(total, 20), ((Number) JsonPath.read(body, "$.totalPages")).intValue());
	}

	@Test
	void aPageReturnsOnlyItsOwnSlice() throws Exception {
		String token = registerAndLogin();

		String body = mockMvc
			.perform(get("/api/categories").param("page", "0").param("size", "3")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		int total = ((Number) JsonPath.read(body, "$.totalElements")).intValue();
		Assertions.assertEquals(Math.min(3, total), ((Number) JsonPath.read(body, "$.data.length()")).intValue());
		Assertions.assertEquals(pages(total, 3), ((Number) JsonPath.read(body, "$.totalPages")).intValue());

		// The last page is the remainder, so it can never be fuller than a whole page.
		int lastPage = pages(total, 3) - 1;
		String lastBody = mockMvc
			.perform(get("/api/categories").param("page", String.valueOf(lastPage)).param("size", "3")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		int onLastPage = ((Number) JsonPath.read(lastBody, "$.data.length()")).intValue();
		Assertions.assertTrue(onLastPage >= 1 && onLastPage <= 3);
	}

	@Test
	void categoriesComeBackSortedByName() throws Exception {
		String token = registerAndLogin();
		String body = mockMvc
			.perform(get("/api/categories").param("size", "100").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		List<String> names = JsonPath.read(body, "$.data[*].name");
		List<String> sorted = names.stream().sorted().toList();
		Assertions.assertEquals(sorted, names);
	}

	@Test
	void searchMatchesPartOfANameIgnoringCase() throws Exception {
		String token = registerAndLogin();
		String tag = uniqueTag();
		String name = "Nebula " + tag;
		createCategory(token, name);

		// Lower-case needle, mixed-case name.
		mockMvc
			.perform(get("/api/categories").param("q", "nebula " + tag.toLowerCase())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].name").value(hasItem(name)))
			.andExpect(jsonPath("$.totalElements").value(1));

		// Upper-case needle finds the same single row.
		mockMvc
			.perform(get("/api/categories").param("q", "NEBULA " + tag.toUpperCase())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void aSearchWithNoMatchIsAnEmptyPageNotAnError() throws Exception {
		String token = registerAndLogin();

		mockMvc
			.perform(get("/api/categories").param("q", "no-such-category-" + uniqueTag())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0))
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void aBlankSearchIsTheSameAsNoSearch() throws Exception {
		String token = registerAndLogin();

		String unfiltered = mockMvc
			.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String blank = mockMvc
			.perform(get("/api/categories").param("q", " ").param("size", "200")
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		// Same result as no filter at all - not "no rows", which is what "%%" would have meant.
		Assertions.assertEquals(
				((Number) JsonPath.read(unfiltered, "$.totalElements")).intValue(),
				((Number) JsonPath.read(blank, "$.totalElements")).intValue());
	}

	@Test
	void searchAndPagingWorkTogether() throws Exception {
		String token = registerAndLogin();
		String tag = uniqueTag();

		// Five categories sharing one needle, so the page arithmetic does not depend on the seeds.
		for (int i = 0; i < 5; i++) {
			createCategory(token, "Findable " + tag + " " + i);
		}

		mockMvc
			.perform(get("/api/categories").param("q", "findable " + tag).param("page", "0")
				.param("size", "2").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.totalElements").value(5))
			.andExpect(jsonPath("$.totalPages").value(3));

		mockMvc
			.perform(get("/api/categories").param("q", "findable " + tag).param("page", "2")
				.param("size", "2").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void searchNeverLeavesTheCallersOwnCategories() throws Exception {
		String mine = registerAndLogin();
		String other = registerAndLogin();
		String name = "Only " + uniqueTag();

		createCategory(mine, name);

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

	private static int pages(int totalElements, int size) {
		return size == 0 ? 0 : (totalElements + size - 1) / size;
	}

	private static String uniqueTag() {
		return UUID.randomUUID().toString().substring(0, 6);
	}

	private void createCategory(String token, String name) throws Exception {
		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated());
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
