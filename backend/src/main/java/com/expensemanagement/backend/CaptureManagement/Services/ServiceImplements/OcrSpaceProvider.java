package com.expensemanagement.backend.CaptureManagement.Services.ServiceImplements;

import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.ReceiptOcrProvider;
import com.expensemanagement.backend.Exceptions.ReceiptReadingUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "receipt.ocr.provider", havingValue = "ocrspace", matchIfMissing = true)
@Slf4j
public class OcrSpaceProvider implements ReceiptOcrProvider
{
    private final RestClient client;
    private final String apiKey;
    private final String url;
    private final int engine;
    private final String language;

    @Autowired
    public OcrSpaceProvider(@Value("${receipt.ocr.ocrspace.api-key:${OCR_SPACE_API_KEY:}}") String apiKey,
                            @Value("${receipt.ocr.ocrspace.url:https://api.ocr.space/parse/image}") String url,
                            @Value("${receipt.ocr.ocrspace.engine:2}") int engine,
                            @Value("${receipt.ocr.ocrspace.language:eng}") String language,
                            @Value("${receipt.ocr.ocrspace.timeout:15000}") int timeout)
    {
        this(builder(timeout), apiKey, url, engine, language);
    }

    public OcrSpaceProvider(RestClient.Builder builder, String apiKey, String url, int engine, String language)
    {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.url = url;
        this.engine = engine;
        this.language = language;
    }

    @Override
    public String extractText(byte[] image, String contentType)
    {
        if (apiKey.isBlank())
        {
            log.warn("Receipt OCR unavailable: OCR.space API key is not configured");
            throw new ReceiptReadingUnavailableException();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource file = new ByteArrayResource(image)
        {
            @Override
            public String getFilename()
            {
                return "receipt." + contentType.substring("image/".length()).replace("jpeg", "jpg");
            }
        };
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("apikey", apiKey);
        parts.add("file", new HttpEntity<>(file, headers));
        parts.add("OCREngine", Integer.toString(engine));
        parts.add("language", language);
        parts.add("isOverlayRequired", "false");

        try
        {
            JsonNode body = client.post().uri(url).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts).retrieve().body(JsonNode.class);
            if (body == null || !body.isObject() || !body.path("IsErroredOnProcessing").isBoolean()
                    || body.path("IsErroredOnProcessing").asBoolean())
            {
                log.warn("Receipt OCR unavailable: OCR.space returned a processing failure");
                throw new ReceiptReadingUnavailableException();
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode result : body.path("ParsedResults"))
            {
                String parsed = result.path("ParsedText").asString("");
                if (!parsed.isBlank())
                {
                    if (!text.isEmpty())
                    {
                        text.append('\n');
                    }
                    text.append(parsed);
                }
            }
            return text.toString();
        }
        catch (RestClientException ex)
        {
            log.warn("Receipt OCR unavailable: {}", ex.getClass().getSimpleName());
            throw new ReceiptReadingUnavailableException();
        }
    }

    private static RestClient.Builder builder(int timeout)
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory);
    }
}
