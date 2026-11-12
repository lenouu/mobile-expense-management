package com.expensemanagement.backend.CaptureManagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements.OcrSpaceProvider;
import com.expensemanagement.backend.Exceptions.ReceiptReadingUnavailableException;

@ExtendWith(OutputCaptureExtension.class)
class OcrSpaceProviderTests {

    private static final String URL = "https://api.ocr.space/parse/image";
    private static final String API_KEY = "test-only-ocr-key";
    private static final byte[] IMAGE = "fake-image-data".getBytes(StandardCharsets.UTF_8);
    private static final String MANUAL_ENTRY_MESSAGE = "receipt reading unavailable, enter the expense manually";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OcrSpaceProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OcrSpaceProvider(builder, API_KEY, URL, 2, "eng");
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void sendsRequiredMultipartFieldsAndCombinesAllParsedResults() {
        expectPost().andExpect(request -> {
            assertThat(request.getHeaders().getContentType()).isNotNull();
            assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                    .isTrue();
            String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
            assertThat(body).contains(
                    "name=\"apikey\"", "\r\n\r\n" + API_KEY + "\r\n",
                    "name=\"file\"; filename=\"receipt.png\"", "Content-Type: image/png", "fake-image-data",
                    "name=\"OCREngine\"", "\r\n\r\n2\r\n",
                    "name=\"language\"", "\r\n\r\neng\r\n",
                    "name=\"isOverlayRequired\"", "\r\n\r\nfalse\r\n");
        }).andRespond(withSuccess("""
                {
                  "IsErroredOnProcessing": false,
                  "ParsedResults": [
                    {"ParsedText": "Corner Market"},
                    {"ParsedText": ""},
                    {"ParsedText": "TOTAL 12.50"},
                    {"ParsedText": "DATE: 12/05/2024"}
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        assertThat(provider.extractText(IMAGE, "image/png"))
                .isEqualTo("Corner Market\nTOTAL 12.50\nDATE: 12/05/2024");
    }

    @Test
    void processingErrorGivesManualEntryMessageWithoutLoggingProviderDetails(CapturedOutput output) {
        expectPost().andRespond(withSuccess("""
                {
                  "IsErroredOnProcessing": true,
                  "ErrorMessage": ["provider detail: %s"]
                }
                """.formatted(API_KEY), MediaType.APPLICATION_JSON));

        assertUnavailable();
        assertThat(output.getAll()).contains("OCR.space returned a processing failure")
                .doesNotContain(API_KEY, "provider detail:");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503, 429})
    void httpFailuresGiveManualEntryMessage(int status) {
        expectPost().andRespond(withStatus(HttpStatus.valueOf(status)));

        assertUnavailable();
    }

    @Test
    void timeoutGivesManualEntryMessage() {
        expectPost().andRespond(request -> {
            throw new SocketTimeoutException("read timed out");
        });

        assertUnavailable();
    }

    @Test
    void emptyApiKeyFailsWithoutRequestsOrSecretInLogs(CapturedOutput output) {
        provider = new OcrSpaceProvider(builder, "", URL, 2, "eng");

        assertUnavailable();
        assertThat(output.getAll()).contains("OCR.space API key is not configured").doesNotContain(API_KEY);
    }

    @Test
    void emptyParsedTextReturnsEmptyString() {
        expectPost().andRespond(withSuccess("""
                {
                  "IsErroredOnProcessing": false,
                  "ParsedResults": [{"ParsedText": ""}, {"ParsedText": "  "}]
                }
                """, MediaType.APPLICATION_JSON));

        assertThat(provider.extractText(IMAGE, "image/png")).isEmpty();
    }

    private ResponseActions expectPost() {
        return server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST));
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> provider.extractText(IMAGE, "image/png"))
                .isInstanceOf(ReceiptReadingUnavailableException.class)
                .hasMessage(MANUAL_ENTRY_MESSAGE);
    }
}
