package com.expensemanagement.backend.SavingsManagement.Controllers;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.SavingsManagement.Dtos.DtoResponses.SavingsResponses;
import com.expensemanagement.backend.SavingsManagement.Services.ServiceInterface.SavingsService;
import com.expensemanagement.backend.Shared.PageResponse;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Savings and debt analytics with automatic carry-forward (US44, US46, US48, US49).
 *
 * <p>Read-only. Carry-forward is derived across periods rather than recorded as a user action.
 */
@RestController
@RequestMapping(SavingsAnalyticsController.BASE_PATH)
@Tag(name = "Savings Analytics", description = "Savings, debt and carry-forward calculation")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('USER')")
public class SavingsAnalyticsController
{
    public static final String BASE_PATH = "/api/v1/analytics/savings";

    // Same default and cap as spring.data.web.pageable.* used by the Pageable endpoints.
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SavingsService savingsService;

    public SavingsAnalyticsController(SavingsService savingsService)
    {
        this.savingsService = savingsService;
    }

    @GetMapping
    @Operation(summary = "Savings, debt and carry-forward for a financial period (US48)",
            description = """
                    Computes total income, total expenses, carried debt from previous periods,
                    effective income, and resulting savings or debt for the specified period.
                    Shortfalls become debt carried forward automatically into the next period.""")
    @ApiResponse(responseCode = "200", description = "Period savings and debt summary")
    @ApiResponse(responseCode = "400", description = "Missing or invalid periodId", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "403", description = "Insufficient privileges", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such period among yours", content = @Content)
    public SavingsResponses.PeriodSummary get(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of your financial periods")
            @RequestParam UUID periodId)
    {
        return savingsService.summaryFor(currentUserId(jwt), periodId);
    }

    @GetMapping("/history")
    @Operation(summary = "Savings history across completed periods (US45)",
            description = """
                    One savings/debt summary per completed financial period, newest first, in the
                    same shape as GET /api/v1/analytics/savings. The current and future periods are
                    excluded. A user with no completed period gets an empty page.""")
    @ApiResponse(responseCode = "200", description = "Page of period summaries")
    @ApiResponse(responseCode = "400", description = "Invalid page or size", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "403", description = "Insufficient privileges", content = @Content)
    public PageResponse<SavingsResponses.PeriodSummary> history(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, 1 to " + MAX_PAGE_SIZE)
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size)
    {
        if (page < 0)
        {
            throw new InvalidRequestException("page must be 0 or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE)
        {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageResponse.of(savingsService.history(currentUserId(jwt), PageRequest.of(page, size)));
    }

    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}

