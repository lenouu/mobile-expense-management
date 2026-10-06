package com.expensemanagement.backend.CategoryManagement;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * US17 end to end: a user creates their own expense category with {@code POST /api/categories}
 * and can file expenses under it straight away. Every test registers its own users, so the
 * shared database never leaks records between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class UserCategoryCreationTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void newAccountWithNoBudgetCanCreateACategoryAndUseItForAnExpense() throws Exception {
		// A brand-new account: nothing exists for it but its starter categories, and no budget.
		String token = registerAndLogin();

		String created = mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"  Pets  \",\"description\":\"Food and vet\",\"icon\":\"paw\",\"color\":\"#AABBCC\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Pets"))
			.andExpect(jsonPath("$.description").value("Food and vet"))
			.andExpect(jsonPath("$.icon").value("paw"))
			.andExpect(jsonPath("$.color").value("#AABBCC"))
			// Created by the user, not copied from a default.
			.andExpect(jsonPath("$.defaultCategoryId").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Integer id = JsonPath.read(created, "$.id");

		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$[*].name").value(hasItem("Pets")));

		mockMvc
			.perform(post("/api/expenses").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":25,\"date\":\"2026-09-15\",\"categoryId\":" + id + "}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category.id").value(id))
			.andExpect(jsonPath("$.category.name").value("Pets"));
	}

	@Test
	void onlyTheNameIsRequired() throws Exception {
		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, registerAndLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Savings\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Savings"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.color").doesNotExist());
	}

	@Test
	void duplicateNameIsRejectedIgnoringCaseButOnlyForTheSameUser() throws Exception {
		String first = registerAndLogin();
		String second = registerAndLogin();
		createCategory(first, "Pets");

		// Same user, other case -> 409.
		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, first)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"PETS\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error").value("conflict"));

		// Clashing with a starter category the user already has -> 409 too.
		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, first)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"transport\"}"))
			.andExpect(status().isConflict());

		// Another user may use the same name, and does not see the first user's category.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, second))
			.andExpect(jsonPath("$[*].name").value(not(hasItem("Pets"))));
		createCategory(second, "Pets");
	}

	@Test
	void invalidInputIsRejected() throws Exception {
		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, registerAndLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\" \",\"color\":\"green\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("validation_failed"))
			.andExpect(jsonPath("$.fieldErrors.name").exists())
			.andExpect(jsonPath("$.fieldErrors.color").exists());

		mockMvc
			.perform(post("/api/categories").header(HttpHeaders.AUTHORIZATION, registerAndLogin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + "x".repeat(51) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.name").exists());
	}

	@Test
	void creatingACategoryRequiresAToken() throws Exception {
		mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pets\"}"))
			.andExpect(status().isUnauthorized());
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
