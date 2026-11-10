package com.expensemanagement.backend.CategoryManagement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US18 end to end: {@code PUT /api/categories/{id}} edits one of the caller's own categories.
 *
 * <p>The story's acceptance criteria, one test each:
 *
 * <ol>
 * <li>a user can change the name, description, icon and colour of their own category, and only
 * their copy changes - the platform default and other users' copies are untouched;</li>
 * <li>renaming to a name the caller already uses returns 409;</li>
 * <li>another user's id returns 404, identical to a missing one;</li>
 * <li>{@code PUT} is a full replace, so an omitted description is cleared.</li>
 * </ol>
 *
 * <p>Deleting a category that expenses still use is US19, so it is not covered here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class UserCategoryEditTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void editingMyCopyLeavesTheDefaultAndOtherAccountsAlone() throws Exception {
		String mine = registerAndLogin();
		String other = registerAndLogin();
		String myCopy = categoryId(mine, "Transport");

		String tag = UUID.randomUUID().toString().substring(0, 6);
		String newName = "Transport " + tag;

		mockMvc
			.perform(put("/api/categories/" + myCopy).header(HttpHeaders.AUTHORIZATION, mine)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + newName + "\",\"description\":\"my own transport\"," + "\"icon\":\"🚌\",\"color\":\"#123456\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(myCopy))
			.andExpect(jsonPath("$.name").value(newName))
			.andExpect(jsonPath("$.description").value("my own transport"))
			.andExpect(jsonPath("$.color").value("#123456"));

		// The caller sees the edit.
		mockMvc.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, mine))
			.andExpect(jsonPath("$.data[*].name").value(hasItem(newName)));

		// Another account still has the name it was provisioned with.
		mockMvc.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.data[*].name").value(hasItem("Transport")))
			.andExpect(jsonPath("$.data[*].name").value(not(hasItem(newName))));

		// And the platform default itself never changed.
		mockMvc.perform(get("/api/categories/defaults").header(HttpHeaders.AUTHORIZATION, mine))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name").value(hasItem("Transport")))
			.andExpect(jsonPath("$[*].name").value(not(hasItem(newName))));
	}

	@Test
	void renamingToANameIAlreadyUseIsAConflict() throws Exception {
		String token = registerAndLogin();
		String mine = categoryId(token, "Other");

		mockMvc
			.perform(put("/api/categories/" + mine).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Transport\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("conflict"));

		// Refused, so nothing changed.
		mockMvc.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.data[*].name").value(hasItem("Other")));
	}

	@Test
	void anotherUsersCategoryIdIsNotFound() throws Exception {
		String mine = registerAndLogin();
		String other = registerAndLogin();
		String othersCategory = categoryId(other, "Health");

		mockMvc
			.perform(put("/api/categories/" + othersCategory).header(HttpHeaders.AUTHORIZATION, mine)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hijacked\"}"))
			.andExpect(status().isNotFound());

		// An id that does not exist answers the same way, so ids cannot be probed.
		mockMvc
			.perform(put("/api/categories/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, mine)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hijacked\"}"))
			.andExpect(status().isNotFound());

		// The owner's category is untouched.
		mockMvc.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, other))
			.andExpect(jsonPath("$.data[*].name").value(hasItem("Health")))
			.andExpect(jsonPath("$.data[*].name").value(not(hasItem("Hijacked"))));
	}

	@Test
	void anOmittedDescriptionIsClearedBecausePutIsAFullReplace() throws Exception {
		String token = registerAndLogin();
		String created = createCategory(token, "With description " + UUID.randomUUID().toString().substring(0, 6),
				"this will be removed");

		mockMvc
			.perform(put("/api/categories/" + created).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renamed " + UUID.randomUUID().toString().substring(0, 6) + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value(nullValue()))
			.andExpect(jsonPath("$.icon").value(nullValue()))
			.andExpect(jsonPath("$.color").value(nullValue()));
	}

	@Test
	void editingRequiresAToken() throws Exception {
		String token = registerAndLogin();
		String mine = categoryId(token, "Shopping");

		mockMvc
			.perform(put("/api/categories/" + mine).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Anything\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aBlankNameIsARefusedRequest() throws Exception {
		String token = registerAndLogin();
		String mine = categoryId(token, "Housing");

		mockMvc
			.perform(put("/api/categories/" + mine).header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.name").exists());
	}

	private String createCategory(String token, String name, String description) throws Exception {
		String body = mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc
			.perform(get("/api/categories").param("size", "200").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> ids = JsonPath.read(body, "$.data[?(@.name == '" + name + "')].id");
		return ids.get(0);
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
