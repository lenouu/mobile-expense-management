package com.expensemanagement.backend.BudgetManagement.Controllers;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetAnalyticsResponses;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetAnalyticsService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Budget analytics (US41, US42): overall balance and per-category balance against
 * the budget that overlaps a given financial period.
 *
 * <p>Read-only. The numbers are the same as {@code GET /api/budgets/{id}} because
 * the service reuses the identical repository queries.
 */
@RestController
@RequestMapping(BudgetAnalyticsController.BASE_PATH)
@Tag(name = "Budget Analytics", description = "Overall and per-category budget balance")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
@PreAuthorize("hasRole('USER')")
public class BudgetAnalyticsController
{
    static final String BASE_PATH = "/api/v1/analytics/budget";

    private final BudgetAnalyticsService analyticsService;

    public BudgetAnalyticsController(BudgetAnalyticsService analyticsService)
    {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overall-balance")
    @Operation(summary = "Overall budget vs actual (US41)",
            description = """
                    Returns the budget amount, actual spending and balance for the budget that
                    overlaps the given financial period. Balance may be negative when spending
                    exceeds the plan.""")
    @ApiResponse(responseCode = "200", description = "Overall balance")
    @ApiResponse(responseCode = "400", description = "Missing or invalid periodId", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "403", description = "Insufficient privileges", content = @Content)
    @ApiResponse(responseCode = "404", description = "Period not found or no budget for it", content = @Content)
    public BudgetAnalyticsResponses.OverallBalance overallBalance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of your financial periods")
            @RequestParam UUID periodId)
    {
        return analyticsService.overallBalance(currentUserId(jwt), periodId);
    }

    @GetMapping("/category-balance")
    @Operation(summary = "Per-category budget balance (US42)",
            description = """
                    One entry per category allocation of the budget that overlaps the given
                    financial period. A budget with no allocations returns an empty array.
                    Balance may be negative; overspent is true when it is.""")
    @ApiResponse(responseCode = "200", description = "Category balances")
    @ApiResponse(responseCode = "400", description = "Missing or invalid periodId", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "403", description = "Insufficient privileges", content = @Content)
    @ApiResponse(responseCode = "404", description = "Period not found or no budget for it", content = @Content)
    public List<BudgetAnalyticsResponses.CategoryBalance> categoryBalance(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of your financial periods")
            @RequestParam UUID periodId)
    {
        return analyticsService.categoryBalance(currentUserId(jwt), periodId);
    }

    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}

