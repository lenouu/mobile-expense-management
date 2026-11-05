package com.expensemanagement.backend.Monitoring.Controllers;

import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.PlatformHealthService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * US10: lets administrators check platform health and review recorded server errors.
 * <strong>ADMIN only.</strong>
 *
 * <p>Protected twice, like {@code AdminUserController}: {@code SecurityConfig} requires
 * {@code ROLE_ADMIN} for {@code /api/admin/**}, and the class-level {@code @PreAuthorize} travels
 * with the code if the route ever moves.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Platform monitoring", description = "ADMIN only - platform health and error log")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class AdminMonitoringController
{
    private final PlatformHealthService platformHealthService;
    private final ErrorLogService errorLogService;

    public AdminMonitoringController(PlatformHealthService platformHealthService, ErrorLogService errorLogService)
    {
        this.platformHealthService = platformHealthService;
        this.errorLogService = errorLogService;
    }

    @GetMapping("/health")
    @Operation(summary = "Platform health",
            description = """
                    Overall status plus database, memory and disk checks, uptime, version and the
                    number of errors recorded in the last 24 hours.

                    Always answers 200: a failing component is reported as `DOWN` in the body,
                    because that is exactly what the administrator needs to see.""")
    @ApiResponse(responseCode = "200", description = "Current health status")
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN", content = @Content)
    public MonitoringResponses.PlatformHealth health()
    {
        return platformHealthService.check();
    }

    @GetMapping("/logs/errors")
    @Operation(summary = "Recorded server errors (paged)",
            description = """
                    Unexpected server errors (HTTP 5xx), newest first.

                    `from` / `to` are optional ISO 8601 timestamps with an offset, e.g.
                    `?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z&page=0&size=20`""")
    @ApiResponse(responseCode = "200", description = "Page of error log entries")
    @ApiResponse(responseCode = "400", description = "Invalid date, or 'from' after 'to'", content = @Content)
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN", content = @Content)
    public PagedModel<MonitoringResponses.ErrorLogEntry> errors(
            @Parameter(description = "Only errors at or after this time")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Only errors at or before this time")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return new PagedModel<>(errorLogService.find(
                from != null ? from.toInstant() : null,
                to != null ? to.toInstant() : null,
                pageable));
    }
}
