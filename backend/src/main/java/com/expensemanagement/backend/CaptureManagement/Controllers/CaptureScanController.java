package com.expensemanagement.backend.CaptureManagement.Controllers;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests.CaptureScanRequests;
import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureScanResponses;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.CaptureScanService;
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
 * Reads a pasted message and proposes the money movements in it.
 *
 * <p>Separate from {@link CaptureController} on purpose: that endpoint persists a pending capture
 * found by the deterministic parser, while this one is a stateless read that creates nothing. The
 * key for the AI provider stays on the server, behind the caller's ordinary bearer token.
 */
@RestController
@RequestMapping("/api/captures")
@Tag(name = "Captures", description = "Transactions read from a pasted message")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class CaptureScanController
{
    private final CaptureScanService captureScanService;

    public CaptureScanController(CaptureScanService captureScanService)
    {
        this.captureScanService = captureScanService;
    }

    @PostMapping("/scan")
    @Operation(summary = "Read the money movements in a pasted message",
            description = """
                    Sends the text to the AI reader together with the caller's own expense
                    categories and income sources. Each movement comes back with a suggested
                    direction - `OUT` (an expense) or `IN` (an income) - which the client shows as
                    a toggle the user can flip before anything is saved.

                    Nothing is persisted. An empty `transactions` list means the text held no
                    money movement - that is a 200, not an error.""")
    @ApiResponse(responseCode = "200", description = "The movements found (possibly none)")
    @ApiResponse(responseCode = "400", description = "The text is blank or too long", content = @Content)
    @ApiResponse(responseCode = "503", description = "The AI service is not configured or unavailable", content = @Content)
    public CaptureScanResponses.Scan scan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CaptureScanRequests.Scan request)
    {
        return captureScanService.scan(UUID.fromString(jwt.getSubject()), request);
    }
}
