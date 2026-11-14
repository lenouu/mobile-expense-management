package com.expensemanagement.backend.ReceiptManagement.Controllers;

import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoRequests.ReceiptScanRequests;
import com.expensemanagement.backend.ReceiptManagement.Dtos.DtoResponses.ReceiptScanResponses;
import com.expensemanagement.backend.ReceiptManagement.Services.ServiceInterface.ReceiptScanService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Reads a receipt photo and proposes the expenses on it.
 *
 * <p>This is the only endpoint that talks to an AI provider, and the API key stays behind it:
 * the app authenticates with its ordinary bearer token and never learns the key. The picture is
 * not stored anywhere - it is forwarded to the model and forgotten with the request.
 */
@RestController
@RequestMapping("/api/receipts")
@Tag(name = "Receipts", description = "Read expenses from a picture")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class ReceiptScanController
{
    private final ReceiptScanService receiptScanService;

    public ReceiptScanController(ReceiptScanService receiptScanService)
    {
        this.receiptScanService = receiptScanService;
    }

    @PostMapping("/scan")
    @Operation(summary = "Read the expenses on a receipt photo",
            description = """
                    Sends the picture to the vision model together with the caller's own
                    categories, and returns the expenses it found. Nothing is saved: the client
                    reviews the rows and creates them through POST /expenses.

                    The body is JSON so one shape works on every client:

                        {"imageBase64": "<base64>", "mimeType": "image/jpeg"}

                    `imageBase64` may include a `data:image/...;base64,` prefix, line breaks and
                    missing padding; the real format is read from the bytes. An empty
                    `expenses` list means the photo held nothing readable - that is a 200, not
                    an error.""")
    @ApiResponse(responseCode = "200", description = "The expenses found (possibly none)")
    @ApiResponse(responseCode = "400", description = "The picture is empty, too large, or not an image", content = @Content)
    @ApiResponse(responseCode = "503", description = "The AI service is not configured or unavailable", content = @Content)
    public ReceiptScanResponses.Scan scan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReceiptScanRequests.Scan request)
    {
        return receiptScanService.scan(UUID.fromString(jwt.getSubject()), request);
    }
}
