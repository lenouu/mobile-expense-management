package com.expensemanagement.backend.UserManagement;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * US01 with the fields the sign-up screen now asks for: an account can be created with only a
 * username, an email and a password.
 *
 * <p>The name is optional on the request and nullable in the database, so these tests pin both
 * halves of that decision - the account exists and can be used straight away, and the name can be
 * filled in afterwards from the profile.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class RegistrationWithoutNamesTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void anAccountCanBeCreatedWithOnlyCredentials() throws Exception {
		String userName = uniqueUserName();

		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.userName").value(userName))
			// Serialized as JSON null rather than omitted: the field is part of the shape, it
			// simply has no value yet.
			.andExpect(jsonPath("$.firstName").value(nullValue()))
			.andExpect(jsonPath("$.lastName").value(nullValue()))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.type").value("USER"));
	}

	@Test
	void thatAccountCanSignInAndUseTheApp() throws Exception {
		String userName = uniqueUserName();
		register(userName);
		String token = login(userName);

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userName").value(userName))
			.andExpect(jsonPath("$.firstName").value(nullValue()))
			.andExpect(jsonPath("$.lastName").value(nullValue()));

		// Signing up also provisions the starter categories, so tracking works immediately.
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(10));
	}

	@Test
	void theNameCanBeFilledInAfterwards() throws Exception {
		String userName = uniqueUserName();
		register(userName);
		String token = login(userName);

		mockMvc
			.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Amara\",\"lastName\":\"Johnson\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Amara"))
			.andExpect(jsonPath("$.lastName").value("Johnson"));
	}

	@Test
	void registeringWithANameStillWorks() throws Exception {
		String userName = uniqueUserName();

		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\",\"firstName\":\"Given\",\"lastName\":\"Family\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.firstName").value("Given"))
			.andExpect(jsonPath("$.lastName").value("Family"));
	}

	@Test
	void theCredentialsAreStillRequired() throws Exception {
		String userName = uniqueUserName();

		// No password.
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.password").exists());

		// No username.
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x" + userName + "@example.com\",\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.userName").exists());
	}

	private static String uniqueUserName() {
		return "user." + UUID.randomUUID().toString().substring(0, 8);
	}

	private void register(String userName) throws Exception {
		mockMvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userName\":\"" + userName + "\",\"email\":\"" + userName + "@example.com\","
						+ "\"password\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isCreated());
	}

	private String login(String userName) throws Exception {
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
