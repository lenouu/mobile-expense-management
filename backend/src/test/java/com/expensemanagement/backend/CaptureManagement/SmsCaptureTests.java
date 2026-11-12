package com.expensemanagement.backend.CaptureManagement;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.ResultActions;

import com.expensemanagement.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * S6-TECH-2 end to end: pasting a mobile money SMS, reviewing it, and confirming or dismissing it.
 * The parsing itself is covered in {@link MobileMoneySmsParserTests}; these check the flow around
 * it. Every test registers its own users, so records never leak between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seed.enabled=true")
@AutoConfigureMockMvc
class SmsCaptureTests {

	private static final String PASSWORD = "Password123!";

	private static final String MTN_PAYMENT = "Y'ello! Vous avez payé 5 000 FCFA à ENEO le 12/05/2026 à 10:22:33. "
			+ "Frais: 100 FCFA. Nouveau solde: 12 450 FCFA. ID de transaction: 8945123301.";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void aPastedSmsIsCapturedForReviewWithoutCreatingAnExpense() throws Exception {
		String token = registerAndLogin();

		capture(token, MTN_PAYMENT, null)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.source").value("MOBILE_MONEY_SMS"))
			.andExpect(jsonPath("$.provider").value("MTN_MOMO"))
			.andExpect(jsonPath("$.type").value("PAYMENT"))
			.andExpect(jsonPath("$.amount.amount").value(5000))
			.andExpect(jsonPath("$.amount.currency").value("XAF"))
			.andExpect(jsonPath("$.fee.amount").value(100))
			.andExpect(jsonPath("$.total.amount").value(5100))
			.andExpect(jsonPath("$.counterparty").value("ENEO"))
			.andExpect(jsonPath("$.reference").value("8945123301"))
			.andExpect(jsonPath("$.transactionDate").value("2026-05-12"))
			.andExpect(jsonPath("$.transactionTime").value("10:22:33"))
			.andExpect(jsonPath("$.suggestedDescription").value("MTN MoMo payment - ENEO"))
			.andExpect(jsonPath("$.expenseId").value(nullValue()))
			// The SMS itself is never sent back or kept.
			.andExpect(jsonPath("$.text").doesNotExist());

		// It is waiting in the review list...
		mockMvc.perform(get("/api/captures?status=PENDING").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.totalElements").value(1));

		// ...and is not an expense yet.
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void confirmingCreatesTheExpenseWithTheReviewedValues() throws Exception {
		String token = registerAndLogin();
		String id = idOf(capture(token, MTN_PAYMENT, null).andExpect(status().isCreated()));
		String category = categoryId(token, "Other");

		// The user corrected the amount and wrote their own description.
		String body = mockMvc
			.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("5100", "2026-05-12", category, "Electricity bill")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.reviewedAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String expenseId = JsonPath.read(body, "$.expenseId");

		mockMvc.perform(get("/api/expenses/" + expenseId).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount.amount").value(5100))
			.andExpect(jsonPath("$.date").value("2026-05-12"))
			.andExpect(jsonPath("$.description").value("Electricity bill"))
			.andExpect(jsonPath("$.category.id").value(category));

		// No longer pending.
		mockMvc.perform(get("/api/captures?status=PENDING").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void anInvalidConfirmationCreatesNothingAndLeavesTheCapturePending() throws Exception {
		String token = registerAndLogin();
		String id = idOf(capture(token, MTN_PAYMENT, null).andExpect(status().isCreated()));

		// No category chosen.
		mockMvc
			.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":{\"amount\":5000},\"date\":\"2026-05-12\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.categoryId").exists());

		// Someone else's category.
		String otherUser = registerAndLogin();
		mockMvc
			.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("5000", "2026-05-12", categoryId(otherUser, "Other"), null)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/captures/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.status").value("PENDING"));
		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void dismissingCreatesNoExpenseAndAReviewedCaptureCannotBeReviewedAgain() throws Exception {
		String token = registerAndLogin();
		String id = idOf(capture(token, MTN_PAYMENT, null).andExpect(status().isCreated()));

		mockMvc.perform(post("/api/captures/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISMISSED"))
			.andExpect(jsonPath("$.expenseId").value(nullValue()));

		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc
			.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(expenseJson("5000", "2026-05-12", categoryId(token, "Other"), null)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("business_rule_violation"));
		mockMvc.perform(post("/api/captures/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void aConfirmedCaptureCannotBeConfirmedTwice() throws Exception {
		String token = registerAndLogin();
		String id = idOf(capture(token, MTN_PAYMENT, null).andExpect(status().isCreated()));
		String json = expenseJson("5000", "2026-05-12", categoryId(token, "Other"), null);

		mockMvc.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/captures/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, token)
				.contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isUnprocessableEntity());

		mockMvc.perform(get("/api/expenses").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void theSameSmsPastedTwiceIsRefusedButAnotherUserMayCaptureTheirOwn() throws Exception {
		String token = registerAndLogin();
		capture(token, MTN_PAYMENT, null).andExpect(status().isCreated());

		capture(token, MTN_PAYMENT, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("conflict"));

		capture(registerAndLogin(), MTN_PAYMENT, null).andExpect(status().isCreated());
	}

	@Test
	void unsupportedMessagesAreRefusedWithAClearReason() throws Exception {
		String token = registerAndLogin();

		capture(token, "MTN MoMo: Vous avez reçu 25 000 FCFA de PAUL NGONO. Nouveau solde: 30 000 FCFA.", null)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("unsupported_message"));
		capture(token, "Bonjour, votre colis arrive demain.", null)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("unsupported_message"));
		capture(token, "   ", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fields.text").exists());
		capture(token, "x".repeat(2001), null)
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/captures").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void theProviderTheUserPickedIsUsed() throws Exception {
		capture(registerAndLogin(), "Paiement de 3000 FCFA a CAMWATER. ID transaction: 1234567890", "ORANGE_MONEY")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.provider").value("ORANGE_MONEY"))
			.andExpect(jsonPath("$.suggestedDescription").value("Orange Money payment - CAMWATER"));
	}

	@Test
	void anotherUsersCaptureIsNotFound() throws Exception {
		String token = registerAndLogin();
		String id = idOf(capture(token, MTN_PAYMENT, null).andExpect(status().isCreated()));
		String intruder = registerAndLogin();

		mockMvc.perform(get("/api/captures/" + id).header(HttpHeaders.AUTHORIZATION, intruder))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/captures/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, intruder))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/captures").header(HttpHeaders.AUTHORIZATION, intruder))
			.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/captures/" + id).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void capturingRequiresAToken() throws Exception {
		mockMvc.perform(post("/api/captures/sms").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\":\"MTN MoMo paiement de 500 FCFA\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/captures")).andExpect(status().isUnauthorized());
	}

	private ResultActions capture(String token, String text, String provider) throws Exception {
		String json = "{\"text\":\"" + text.replace("\"", "\\\"") + "\""
				+ (provider == null ? "" : ",\"provider\":\"" + provider + "\"") + "}";
		return mockMvc.perform(post("/api/captures/sms").header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String expenseJson(String amount, String date, String categoryId, String description) {
		return "{\"amount\":{\"amount\":" + amount + "},\"date\":\"" + date + "\",\"categoryId\":\"" + categoryId + "\""
				+ (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
	}

	private String categoryId(String token, String name) throws Exception {
		String body = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, token))
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
