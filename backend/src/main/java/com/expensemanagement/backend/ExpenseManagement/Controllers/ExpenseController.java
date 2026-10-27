package com.expensemanagement.backend.ExpenseManagement.Controllers;

import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
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
 * S2-TECH-2: the caller's own expenses.
 *
 * <p>The owner always comes from the token's {@code sub} claim, never from the URL or body, so
 * the only expenses a caller can reach are their own. Another user's expense id answers 404.
 */
@RestController
@RequestMapping("/api/expenses")
@Tag(name = "Expenses", description = "The caller's own expenses")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class ExpenseController
{
    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService)
    {
        this.expenseService = expenseService;
    }

    @GetMapping
    @Operation(summary = "My expenses (paged)",
            description = """
                    Newest first by default. `from` / `to` are optional dates (inclusive), e.g.
                    `?from=2026-09-01&to=2026-09-30&page=0&size=20`""")
    @ApiResponse(responseCode = "200", description = "Page of expenses")
    @ApiResponse(responseCode = "400", description = "Invalid date, or 'from' after 'to'", content = @Content)
    public PagedModel<ExpenseResponses.Details> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Only expenses on or after this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Only expenses on or before this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = {"date", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return new PagedModel<>(expenseService.findMine(currentUserId(jwt), from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my expenses")
    @ApiResponse(responseCode = "200", description = "Expense found")
    @ApiResponse(responseCode = "404", description = "No such expense among yours", content = @Content)
    public ExpenseResponses.Details get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id)
    {
        return expenseService.findMine(currentUserId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an expense",
            description = """
                    `amount` > 0 with at most 2 decimals, `date` not in the future, and
                    `categoryId` one of your categories (`GET /api/categories`).""")
    @ApiResponse(responseCode = "201", description = "Expense created")
    @ApiResponse(responseCode = "400", description = "Validation failed or category not yours", content = @Content)
    public ExpenseResponses.Details create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody ExpenseRequests.SaveExpense request)
    {
        return expenseService.create(currentUserId(jwt), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an expense", description = "Full replace: an omitted description is cleared.")
    @ApiResponse(responseCode = "200", description = "Expense updated")
    @ApiResponse(responseCode = "400", description = "Validation failed or category not yours", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such expense among yours", content = @Content)
    public ExpenseResponses.Details update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                           @Valid @RequestBody ExpenseRequests.SaveExpense request)
    {
        return expenseService.update(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense")
    @ApiResponse(responseCode = "204", description = "Expense deleted")
    @ApiResponse(responseCode = "404", description = "No such expense among yours", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id)
    {
        expenseService.delete(currentUserId(jwt), id);
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private Long currentUserId(Jwt jwt)
    {
        return Long.valueOf(jwt.getSubject());
    }
}
