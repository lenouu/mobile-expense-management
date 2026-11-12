package com.expensemanagement.backend.CaptureManagement;

import com.expensemanagement.backend.Authentication.Config.SecurityConfig;
import com.expensemanagement.backend.CaptureManagement.Config.ReceiptCaptureConfiguration;
import com.expensemanagement.backend.CaptureManagement.Controllers.CaptureController;
import com.expensemanagement.backend.CaptureManagement.Controllers.ReceiptCaptureController;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser;
import com.expensemanagement.backend.CaptureManagement.Repositories.CapturedTransactionRepository;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.CaptureServiceImpl;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.ReceiptCaptureService;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.ReceiptOcrProvider;
import com.expensemanagement.backend.Exceptions.ReceiptReadingUnavailableException;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReceiptCaptureController.class, CaptureController.class})
@Import({SecurityConfig.class, ReceiptCaptureConfiguration.class, ReceiptCaptureService.class,
        CaptureServiceImpl.class, MobileMoneySmsParser.class})
@TestPropertySource(properties = "app.security.jwt.secret=receipt-tests-only-secret-0123456789abcdef")
class ReceiptCaptureControllerTests
{
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAPTURE_ID = UUID.randomUUID();
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    @Autowired private MockMvc mvc;
    @Autowired private JwtEncoder encoder;
    @Autowired private MultipartConfigElement multipartConfig;
    @MockitoBean private ReceiptOcrProvider provider;
    @MockitoBean private CapturedTransactionRepository captures;
    @MockitoBean private UserRepository users;
    @MockitoBean private ExpenseService expenses;
    @MockitoBean private ErrorLogService errorLogs;

    @BeforeEach
    void setUp()
    {
        User user = mock(User.class);
        when(user.getBaseCurrency()).thenReturn("XAF");
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(captures.save(any())).thenAnswer(invocation -> {
            var capture = invocation.getArgument(0, com.expensemanagement.backend.CaptureManagement.Entities.CapturedTransaction.class);
            capture.setId(CAPTURE_ID);
            return capture;
        });
    }

    @Test
    void fullExtractionReturnsAcceptedAndUsesTokenOwner() throws Exception
    {
        when(provider.extractText(any(), anyString())).thenReturn("Marché Central\nDate 02/01/2024\nTOTAL TTC 12 500 FCFA");
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.captureId").value(CAPTURE_ID.toString()))
                .andExpect(jsonPath("$.extracted.amount.amount").value(12500.0))
                .andExpect(jsonPath("$.extracted.amount.currency").value("XAF"))
                .andExpect(jsonPath("$.extracted.date").value("2024-01-02"))
                .andExpect(jsonPath("$.extracted.merchant").value("Marché Central"))
                .andExpect(jsonPath("$.confidence").value(1.0));
        verify(users).findById(USER_ID);
        verifyNoInteractions(expenses);
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(5 * 1024 * 1024);
        assertThat(multipartConfig.getFileSizeThreshold()).isGreaterThanOrEqualTo((int) multipartConfig.getMaxRequestSize());
    }

    @Test
    void partialExtractionKeepsUnreadableFieldsNull() throws Exception
    {
        when(provider.extractText(any(), anyString())).thenReturn("Marché Central");
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.extracted.amount").value(nullValue()))
                .andExpect(jsonPath("$.extracted.date").value(nullValue()))
                .andExpect(jsonPath("$.extracted.merchant").value("Marché Central"))
                .andExpect(jsonPath("$.confidence").value(0.25));
    }

    @Test
    void emptyExtractionReturnsAllNullWithZeroConfidence() throws Exception
    {
        when(provider.extractText(any(), anyString())).thenReturn("");
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.extracted.amount").value(nullValue()))
                .andExpect(jsonPath("$.extracted.date").value(nullValue()))
                .andExpect(jsonPath("$.extracted.merchant").value(nullValue()))
                .andExpect(jsonPath("$.confidence").value(0.0));
    }

    @Test
    void missingPartIsBadRequest() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provider, captures, expenses);
    }

    @Test
    void emptyPartIsBadRequest() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provider, captures, expenses);
    }

    @Test
    void wrongContentTypeIsUnsupported() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("application/pdf", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(provider, captures, expenses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void claimedImageTypeWithoutMatchingSignatureIsUnsupported(String type) throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file(type, "not an image".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(provider, captures, expenses);
    }

    @Test
    void fileAboveFiveMegabytesIsTooLarge() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH)
                        .file(file("image/jpeg", new byte[ReceiptCaptureService.MAX_UPLOAD_BYTES + 1]))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isContentTooLarge());
        verifyNoInteractions(provider, captures, expenses);
    }

    @Test
    void providerFailureIsBadGatewayWithoutDraft() throws Exception
    {
        when(provider.extractText(any(), anyString())).thenThrow(new ReceiptReadingUnavailableException());
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("USER")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("receipt_reading_unavailable"))
                .andExpect(jsonPath("$.message").value("receipt reading unavailable, enter the expense manually"));
        verifyNoInteractions(captures, expenses);
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(provider, captures, expenses);
    }

    @Test
    void adminTokenIsForbidden() throws Exception
    {
        mvc.perform(multipart(ReceiptCaptureController.BASE_PATH).file(file("image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(provider, captures, expenses);
    }

    @ParameterizedTest
    @ValueSource(strings = {"list", "detail", "confirm", "dismiss"})
    void adminCannotReviewReceiptThroughExistingCaptureEndpoints(String operation) throws Exception
    {
        var request = switch (operation) {
            case "list" -> get("/api/captures");
            case "detail" -> get("/api/captures/" + CAPTURE_ID);
            case "confirm" -> post("/api/captures/" + CAPTURE_ID + "/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":{\"amount\":10,\"currency\":\"XAF\"},\"date\":\"2024-01-02\",\"categoryId\":\"" + UUID.randomUUID() + "\"}");
            default -> post("/api/captures/" + CAPTURE_ID + "/dismiss");
        };
        mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(captures, expenses);
    }

    private static MockMultipartFile file(String type, byte[] bytes)
    {
        return new MockMultipartFile("file", "receipt", type, bytes);
    }

    private String token(String type)
    {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(USER_ID.toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(60)).claim("type", type).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
