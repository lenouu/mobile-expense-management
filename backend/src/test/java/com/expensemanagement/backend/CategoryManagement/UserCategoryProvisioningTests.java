package com.expensemanagement.backend.CategoryManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.expensemanagement.backend.Seeds.UserSeeds;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * S2-TECH-1 end to end: registering through {@code POST /api/auth/register} gives the new account
 * its own copy of the active default categories, read back with {@code GET /api/categories}.
 *
 * <p>The database is shared by every test in the context, so each test registers its own users
 * and uses its own category names, and compares against the defaults as they are at that moment.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class UserCategoryProvisioningTests {

	private static final String ADMIN = "admin.amina";

	private static final String USER = "sara.mansouri";

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void newAccountStartsWithEveryActiveDefaultCategory() throws Exception {
		String userToken = registerAndLogin();

		List<String> defaults = names(mockMvc
			.perform(get("/api/categories/defaults").header(HttpHeaders.AUTHORIZATION, userToken))
			.andReturn()
			.getResponse()
			.getContentAsString());

		String mine = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").isNumber())
			.andExpect(jsonPath("$[0].defaultCategoryId").value(notNullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(names(mine)).containsExactlyElementsOf(defaults).contains("Transport", "Other");
	}

	@Test
	void inactiveDefaultIsNotCopied() throws Exception {
		String adminToken = bearer(ADMIN);
		String hidden = uniqueName("Hidden");
		Integer id = createDefault(adminToken, hidden);
		mockMvc
			.perform(patch("/api/admin/categories/" + id + "/status").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk());

		String userToken = registerAndLogin();

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(jsonPath("$[*].name").value(not(hasItem(hidden))));
	}

	@Test
	void copiesAreSnapshotsAndBelongToTheirOwner() throws Exception {
		String adminToken = bearer(ADMIN);
		String firstToken = registerAndLogin();

		// A default added after the first registration reaches only later accounts.
		String added = uniqueName("Gym");
		Integer id = createDefault(adminToken, added);
		String secondToken = registerAndLogin();

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, firstToken))
			.andExpect(jsonPath("$[*].name").value(not(hasItem(added))));
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, secondToken))
			.andExpect(jsonPath("$[*].name").value(hasItem(added)));

		// Renaming the default afterwards does not touch the copy already given out.
		mockMvc
			.perform(put("/api/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + added + " renamed\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, secondToken))
			.andExpect(jsonPath("$[*].name").value(hasItem(added)))
			.andExpect(jsonPath("$[*].name").value(not(hasItem(added + " renamed"))));

		// Both users have a "Transport", but each sees only their own row.
		List<Integer> firstIds = ids(firstToken);
		List<Integer> secondIds = ids(secondToken);
		assertThat(firstIds).isNotEmpty().doesNotContainAnyElementsOf(secondIds);
	}

	@Test
	void seededUsersHaveCategoriesButAdminsDoNot() throws Exception {
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(hasItem("Transport")));

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").value(empty()));
	}

	@Test
	void myCategoriesRequireAToken() throws Exception {
		mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
	}

	private String registerAndLogin() throws Exception {
		String userName = "user." + UUID.randomUUID().toString().substring(0, 8);
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}"))
			.andExpect(status().isCreated());
		return bearer(userName);
	}

	private Integer createDefault(String adminToken, String name) throws Exception {
		String body = mockMvc
			.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private List<Integer> ids(String token) throws Exception {
		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$[*].id");
	}

	private static List<String> names(String json) {
		return JsonPath.read(json, "$[*].name");
	}

	private static String uniqueName(String prefix) {
		return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
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
