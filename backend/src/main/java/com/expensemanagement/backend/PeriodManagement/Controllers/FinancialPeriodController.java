package com.expensemanagement.backend.PeriodManagement.Controllers;

import com.expensemanagement.backend.PeriodManagement.Dtos.DtoRequests.PeriodRequests;
import com.expensemanagement.backend.PeriodManagement.Dtos.DtoResponses.PeriodResponses;
import com.expensemanagement.backend.PeriodManagement.Mappers.PeriodMappers;
import com.expensemanagement.backend.PeriodManagement.Services.ServiceInterface.FinancialPeriodService;
import com.expensemanagement.backend.Shared.PageResponse;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * S4-TECH-1 and US27: the caller's financial periods, and the length they run for.
 *
 * <p>The owner always comes from the token, so there is no user id in any URL to tamper with.
 *
 * <p>Changing the length deliberately does not touch the period in progress: the new length takes
 * effect from the next period's start date, which is what `effectiveFrom` in the settings reports.
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
    public PageResponse<PeriodResponses.Period> history(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 20, sort = "sequence", direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return PageResponse.of(periodService.history(currentUserId(jwt), pageable));
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

    @PutMapping("/settings")
    @Operation(summary = "Change my financial period length",
            description = """
                    Body: `{"count": 2, "unit": "WEEKS"}`. Units are `DAYS`, `WEEKS` and `MONTHS`.

                    A period must last at least one week and at most one year. The period running
                    right now keeps its dates: the new length takes effect from the start of the
                    next period, which the response reports as `effectiveFrom`.

                    Periods already created ahead of the change are discarded and rebuilt on
                    demand, and periods that have already happened are never rewritten.""")
    @ApiResponse(responseCode = "200", description = "Length changed; the settings are returned")
    @ApiResponse(responseCode = "400", description = "Missing count or unit, shorter than one "
            + "week, or longer than one year", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public PeriodResponses.Settings changeLength(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody PeriodRequests.ChangeLength request)
    {
        return PeriodMappers.toSettings(
                periodService.changeLength(currentUserId(jwt), request.getCount(), request.getUnit()));
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
