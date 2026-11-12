package com.expensemanagement.backend.CaptureManagement.Controllers;

import com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests.CaptureRequests;
import com.expensemanagement.backend.CaptureManagement.Dtos.DtoResponses.CaptureResponses;
import com.expensemanagement.backend.CaptureManagement.Entities.CaptureStatus;
import com.expensemanagement.backend.CaptureManagement.Services.ServiceInterface.CaptureService;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.Shared.PageResponse;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * S6-TECH-2: transactions captured from a pasted mobile money SMS, and their review.
 *
 * <p>A capture is not an expense. It waits as PENDING until the user confirms it - which creates
 * a normal expense from the values they reviewed - or dismisses it.
 */
@RestController
@RequestMapping("/api/captures")
@Tag(name = "Captured transactions", description = "Mobile money SMS capture and review")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class CaptureController
{
    private final CaptureService captureService;

    public CaptureController(CaptureService captureService)
    {
        this.captureService = captureService;
    }

    @PostMapping("/sms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Capture a pasted mobile money SMS",
            description = """
                    Reads an MTN MoMo or Orange Money SMS and keeps the transaction as PENDING.
                    **No expense is created** until it is confirmed.

                    `provider` (`MTN_MOMO` or `ORANGE_MONEY`) is optional and detected from the text
                    when omitted. Fields the message does not contain come back null rather than
                    guessed. The SMS text itself is not stored.

                    Refused with 422 `unsupported_message` when the text is not from a supported
                    service, records money received, or has no readable amount; with 409 when the
                    same transaction reference was already captured.""")
    @ApiResponse(responseCode = "201", description = "Transaction captured, waiting for review")
    @ApiResponse(responseCode = "400", description = "Text missing or too long", content = @Content)
    @ApiResponse(responseCode = "409", description = "Already captured", content = @Content)
    @ApiResponse(responseCode = "422", description = "Not a supported mobile money transaction", content = @Content)
    public CaptureResponses.Details captureSms(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CaptureRequests.SmsCapture request)
    {
        return captureService.captureSms(currentUserId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "My captured transactions (paged)", description = "Newest first. `status` is optional.")
    @ApiResponse(responseCode = "200", description = "Page of captured transactions")
    public PageResponse<CaptureResponses.Details> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "PENDING, CONFIRMED or DISMISSED; omit for all")
            @RequestParam(required = false) CaptureStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return PageResponse.of(captureService.findMine(currentUserId(jwt), status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my captured transactions")
    @ApiResponse(responseCode = "200", description = "Captured transaction found")
    @ApiResponse(responseCode = "404", description = "No such capture among yours", content = @Content)
    public CaptureResponses.Details get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        return captureService.findMine(currentUserId(jwt), id);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a captured transaction as an expense",
            description = """
                    The body is the expense exactly as for `POST /api/expenses`, holding the values
                    the user reviewed (they may have corrected the amount, date or description, and
                    always choose the category). The expense is created and the capture becomes
                    CONFIRMED with its `expenseId` - both or neither.""")
    @ApiResponse(responseCode = "200", description = "Expense created, capture confirmed")
    @ApiResponse(responseCode = "400", description = "Validation failed or category not yours", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such capture among yours", content = @Content)
    @ApiResponse(responseCode = "422", description = "Already confirmed or dismissed", content = @Content)
    public CaptureResponses.Details confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @Valid @RequestBody ExpenseRequests.SaveExpense expense)
    {
        return captureService.confirm(currentUserId(jwt), id, expense);
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "Dismiss a captured transaction", description = "No expense is created.")
    @ApiResponse(responseCode = "200", description = "Capture dismissed")
    @ApiResponse(responseCode = "404", description = "No such capture among yours", content = @Content)
    @ApiResponse(responseCode = "422", description = "Already confirmed or dismissed", content = @Content)
    public CaptureResponses.Details dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        return captureService.dismiss(currentUserId(jwt), id);
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
