package com.expensemanagement.backend.CategoryManagement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.expensemanagement.backend.Seeds.UserSeeds;
import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US09 end to end: real JWTs from {@code POST /api/auth/login} for the seeded accounts, against a
 * Testcontainers PostgreSQL. Each test uses its own category names, because the database is
 * shared by every test in the context.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class AdminCategoryControllerTests {

	private static final String ADMIN = "admin.amina";

	private static final String USER = "sara.mansouri";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void starterCategoriesAreSeeded() throws Exception {
		mockMvc.perform(get("/api/admin/categories").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[*].name").value(hasItem("Transport")))
			.andExpect(jsonPath("$.content[*].name").value(hasItem("Other")));
	}

	@Test
	void adminCanCreateAndUpdateCategory() throws Exception {
		String adminToken = bearer(ADMIN);
		String name = uniqueName("Pets");

		String created = mockMvc
			.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  " + name + "  \",\"description\":\"Food and vet\",\"icon\":\"paw\",\"color\":\"#AABBCC\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.description").value("Food and vet"))
			.andExpect(jsonPath("$.icon").value("paw"))
			.andExpect(jsonPath("$.color").value("#AABBCC"))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		// Full replace: the omitted icon and color are cleared.
		mockMvc
			.perform(put("/api/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + " care\",\"description\":\"Vet only\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(name + " care"))
			.andExpect(jsonPath("$.description").value("Vet only"))
			.andExpect(jsonPath("$.icon").doesNotExist())
			.andExpect(jsonPath("$.color").doesNotExist());

		mockMvc.perform(get("/api/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(name + " care"));
	}

	@Test
	void duplicateNameIsRejectedIgnoringCase() throws Exception {
		String adminToken = bearer(ADMIN);
		String name = uniqueName("Gifts");
		create(adminToken, name);

		mockMvc
			.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name.toUpperCase() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("conflict"));

		// Renaming another category to the taken name is refused too...
		Integer otherId = create(adminToken, uniqueName("Travel"));
		mockMvc
			.perform(put("/api/admin/categories/" + otherId).header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name.toLowerCase() + "\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void renamingToOwnNameWithDifferentCaseIsAllowed() throws Exception {
		String adminToken = bearer(ADMIN);
		String name = uniqueName("savings");
		Integer id = create(adminToken, name);

		mockMvc
			.perform(put("/api/admin/categories/" + id).header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name.toUpperCase() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(name.toUpperCase()));
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		mockMvc
			.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \",\"color\":\"green\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("validation_failed"))
			.andExpect(jsonPath("$.fieldErrors.name").exists())
			.andExpect(jsonPath("$.fieldErrors.color").exists());
	}

	@Test
	void unknownCategoryIsNotFound() throws Exception {
		mockMvc.perform(get("/api/admin/categories/999999").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
			.andExpect(status().isNotFound());
	}

	@Test
	void deactivatedCategoryIsHiddenFromUsersUntilReactivated() throws Exception {
		String adminToken = bearer(ADMIN);
		String userToken = bearer(USER);
		String name = uniqueName("Subscriptions");
		Integer id = create(adminToken, name);

		mockMvc.perform(get("/api/categories/defaults").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(hasItem(name)))
			// Users do not see the admin-only fields.
			.andExpect(jsonPath("$[0].active").doesNotExist());

		mockMvc
			.perform(patch("/api/admin/categories/" + id + "/status").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/categories/defaults").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(jsonPath("$[*].name").value(not(hasItem(name))));

		// Still visible to the admin, and filterable.
		mockMvc
			.perform(get("/api/admin/categories").param("active", "false")
				.param("size", "100")
				.header(HttpHeaders.AUTHORIZATION, adminToken))
			.andExpect(jsonPath("$.content[*].name").value(hasItem(name)));

		mockMvc
			.perform(patch("/api/admin/categories/" + id + "/status").header(HttpHeaders.AUTHORIZATION, adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":true}"))
			.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(get("/api/categories/defaults").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(jsonPath("$[*].name").value(hasItem(name)));
	}

	@Test
	void adminEndpointsRejectRegularUser() throws Exception {
		String userToken = bearer(USER);
		mockMvc.perform(get("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, userToken))
			.andExpect(status().isForbidden());
		mockMvc
			.perform(post("/api/admin/categories").header(HttpHeaders.AUTHORIZATION, userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + uniqueName("Hack") + "\"}"))
			.andExpect(status().isForbidden());
		mockMvc
			.perform(patch("/api/admin/categories/1/status").header(HttpHeaders.AUTHORIZATION, userToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void categoryEndpointsRequireAToken() throws Exception {
		mockMvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/categories/defaults")).andExpect(status().isUnauthorized());
	}

	private Integer create(String adminToken, String name) throws Exception {
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
