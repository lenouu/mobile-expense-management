package com.expensemanagement.backend.Ai;

import com.expensemanagement.backend.Exceptions.AiUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The only outbound door to the AI provider.
 *
 * <p>The API key lives here, on the server, and never travels to an app. It is read from
 * {@code app.ai.deepseek.api-key}, which {@code application.properties} fills from the
 * {@code DEEPSEEK_API_KEY} environment variable, so the value is not committed.
 *
 * <p>Two shapes are offered because the product has two readers: {@link #readImage} for a
 * photograph (the picture travels as a {@code data:} URL inside the OpenAI-compatible payload)
 * and {@link #readText} for pasted text such as a mobile money message. Neither stores anything:
 * the bytes and the text are posted and dropped with the request.
 */
@Component
public class DeepSeekClient
{
    /** The value the shipped template carries, so a forgotten key fails loudly instead of 401ing. */
    private static final String PLACEHOLDER = "PASTE_YOUR_DEEPSEEK_API_KEY_HERE";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(
            @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.deepseek.api-key:}") String apiKey,
            @Value("${app.ai.deepseek.model:deepseek-flash}") String model,
            @Value("${app.ai.deepseek.timeout-seconds:120}") int timeoutSeconds,
            ObjectMapper objectMapper)
    {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.objectMapper = objectMapper;

        // A dense receipt and a long message both take longer than a default read timeout would
        // allow, and the provider can be slow at peak times.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Whether a key has been configured; the services turn "no" into a clear 503. */
    public boolean isConfigured()
    {
        return !apiKey.isBlank() && !PLACEHOLDER.equals(apiKey);
    }

    /**
     * Sends a prompt together with a picture and returns the assistant's raw text.
     *
     * @param prompt       the instruction
     * @param imageDataUrl {@code data:image/...;base64,...} - already canonical
     */
    public String readImage(String prompt, String imageDataUrl)
    {
        return chat(List.of(
                Map.of("type", "text", "text", prompt),
                Map.of("type", "image_url", "image_url", Map.of(
                        "url", imageDataUrl,
                        // A receipt is dense small print: keep the resolution.
                        "detail", "high"))));
    }

    /**
     * Sends a prompt with no picture and returns the assistant's raw text.
     *
     * @param prompt the instruction, including any text to read
     */
    public String readText(String prompt)
    {
        return chat(List.of(Map.of("type", "text", "text", prompt)));
    }

    private String chat(List<Map<String, Object>> content)
    {
        if (!isConfigured())
        {
            throw new AiUnavailableException(
                    "The AI reader is not configured on the server. Set DEEPSEEK_API_KEY "
                            + "and restart the backend.");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "temperature", 0,
                "max_tokens", 1500);

        String raw;
        try
        {
            raw = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        }
        catch (RestClientResponseException e)
        {
            // Bad key, no credit, unknown model - the status says which, the body is not echoed
            // because it can contain the key's prefix.
            throw new AiUnavailableException(
                    "The AI service refused the request (HTTP " + e.getStatusCode().value()
                            + "). Check the server's DeepSeek key and model.", e);
        }
        catch (ResourceAccessException e)
        {
            throw new AiUnavailableException(
                    "The AI service could not be reached. Check the server's connection and try again.",
                    e);
        }

        if (raw == null || raw.isBlank())
        {
            throw new AiUnavailableException("The AI service returned an empty answer.");
        }

        try
        {
            JsonNode contentNode = objectMapper.readTree(raw)
                    .path("choices").path(0).path("message").path("content");

            if (!contentNode.isTextual() || contentNode.asText().isBlank())
            {
                throw new AiUnavailableException("The AI service returned an empty answer.");
            }
            return contentNode.asText();
        }
        catch (AiUnavailableException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new AiUnavailableException("The AI service's answer could not be read.", e);
        }
    }
}
