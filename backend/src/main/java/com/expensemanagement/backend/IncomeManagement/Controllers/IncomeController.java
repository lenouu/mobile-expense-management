package com.expensemanagement.backend.IncomeManagement.Controllers;

import com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests.IncomeRequests;
import com.expensemanagement.backend.IncomeManagement.Dtos.DtoResponses.IncomeResponses;
import com.expensemanagement.backend.IncomeManagement.Services.ServiceInterface.IncomeService;
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
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * S2-TECH-2: the caller's own incomes (money received, e.g. a salary).
 *
 * <p>The owner always comes from the token's {@code sub} claim, never from the URL or body, so
 * the only incomes a caller can reach are their own. Another user's income id answers 404.
 */
@RestController
@RequestMapping("/api/incomes")
@Tag(name = "Incomes", description = "The caller's own incomes")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class IncomeController
{
    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService)
    {
        this.incomeService = incomeService;
    }

    @GetMapping
    @Operation(summary = "My incomes (paged)",
            description = """
                    Newest first by default. `from` / `to` are optional dates (inclusive), e.g.
                    `?from=2026-09-01&to=2026-09-30&page=0&size=20`""")
    @ApiResponse(responseCode = "200", description = "Page of incomes")
    @ApiResponse(responseCode = "400", description = "Invalid date, or 'from' after 'to'", content = @Content)
    public PagedModel<IncomeResponses.Details> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Only incomes on or after this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Only incomes on or before this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = {"date", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return new PagedModel<>(incomeService.findMine(currentUserId(jwt), from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my incomes")
    @ApiResponse(responseCode = "200", description = "Income found")
    @ApiResponse(responseCode = "404", description = "No such income among yours", content = @Content)
    public IncomeResponses.Details get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id)
    {
        return incomeService.findMine(currentUserId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an income",
            description = """
                    `amount` > 0 with at most 2 decimals, `date` not in the future, and a
                    `source` such as "Salary".""")
    @ApiResponse(responseCode = "201", description = "Income created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    public IncomeResponses.Details create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody IncomeRequests.SaveIncome request)
    {
        return incomeService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an income", description = "Full replace: an omitted description is cleared.")
    @ApiResponse(responseCode = "200", description = "Income updated")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such income among yours", content = @Content)
    public IncomeResponses.Details update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @Valid @RequestBody IncomeRequests.SaveIncome request)
    {
        return incomeService.update(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an income")
    @ApiResponse(responseCode = "204", description = "Income deleted")
    @ApiResponse(responseCode = "404", description = "No such income among yours", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id)
    {
        incomeService.delete(currentUserId(jwt), id);
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private Long currentUserId(Jwt jwt)
    {
        return Long.valueOf(jwt.getSubject());
    }
}
