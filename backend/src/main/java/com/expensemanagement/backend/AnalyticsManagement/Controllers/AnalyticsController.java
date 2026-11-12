package com.expensemanagement.backend.AnalyticsManagement.Controllers;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.expensemanagement.backend.AnalyticsManagement.Dtos.AnalyticsResponses;
import com.expensemanagement.backend.AnalyticsManagement.Services.AnalyticsServiceImpl;
import com.expensemanagement.backend.Swagger.SwaggerConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

/**
 * The Dashboard: every figure it shows, in one response.
 *
 * <p>One endpoint rather than a call per card, because a page that shows a savings total beside a
 * chart of the same money must not be able to display two different answers.
 */
@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Income against expenses for a window, and against the window before")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class AnalyticsController
{
    private final AnalyticsServiceImpl analyticsService;

    public AnalyticsController(AnalyticsServiceImpl analyticsService)
    {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "The dashboard for a window",
            description = "Income, expenses and their difference for the window and for the window of "
                    + "the same length before it; the window day by day and in seven buckets; where the "
                    + "money went and where it came from; the best and worst day by net; and the plan "
                    + "in force.")
    @ApiResponse(responseCode = "200", description = "The window, summarised")
    @ApiResponse(responseCode = "400", description = "A missing or reversed range", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public AnalyticsResponses.Summary summary(
            @AuthenticationPrincipal Jwt jwt,
            @NotNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @NotNull @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
    {
        return analyticsService.summary(UUID.fromString(jwt.getSubject()), from, to);
    }
}
