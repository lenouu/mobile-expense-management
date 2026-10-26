package com.expensemanagement.backend.PeriodManagement.Controllers;

import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.PeriodManagement.Mappers.PeriodMappers;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * S4-TECH-1: read access to the caller's financial periods.
 *
 * <p>Read-only on purpose: choosing the period length is US27, which builds its endpoint on
 * {@code FinancialPeriodService.changeLength}. The owner always comes from the token.
 */
@RestController
@RequestMapping("/api/periods")
@Tag(name = "Financial periods", description = "The caller's financial periods")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class FinancialPeriodController
{
    private final FinancialPeriodService periodService;

    public FinancialPeriodController(FinancialPeriodService periodService)
    {
        this.periodService = periodService;
    }

    @GetMapping("/current")
    @Operation(summary = "My current financial period",
            description = """
                    The period containing today. Without a chosen length it is one calendar month,
                    counted from the day the account was created.""")
    @ApiResponse(responseCode = "200", description = "The current period")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public PeriodResponses.Period current(@AuthenticationPrincipal Jwt jwt)
    {
        return PeriodMappers.toPeriod(periodService.current(currentUserId(jwt)), LocalDate.now());
    }

    @GetMapping
    @Operation(summary = "My financial periods (paged)",
            description = "From the current period back to the first one, newest first.")
    @ApiResponse(responseCode = "200", description = "Page of periods")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public PagedModel<PeriodResponses.Period> history(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "sequence", direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return new PagedModel<>(periodService.history(currentUserId(jwt), pageable));
    }

    @GetMapping("/settings")
    @Operation(summary = "My financial period length",
            description = """
                    The length new periods use, whether it is still the default, and from which
                    date it applies (a change only applies from the next period).""")
    @ApiResponse(responseCode = "200", description = "The caller's period settings")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public PeriodResponses.Settings settings(@AuthenticationPrincipal Jwt jwt)
    {
        return PeriodMappers.toSettings(periodService.settings(currentUserId(jwt)));
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private Long currentUserId(Jwt jwt)
    {
        return Long.valueOf(jwt.getSubject());
    }
}
