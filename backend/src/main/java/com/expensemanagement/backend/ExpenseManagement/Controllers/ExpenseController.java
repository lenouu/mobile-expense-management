package com.expensemanagement.backend.ExpenseManagement.Controllers;

import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests.ExpenseRequests;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.ExpenseManagement.Services.ServiceInterface.ExpenseService;
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
import java.util.UUID;

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
    @Operation(summary = "My expenses (paged, filterable)",
            description = """
                    Newest first by default. Every filter is optional and they combine:

                    - `periodId`: one of your financial periods (US21); its start and end dates
                      become the date bounds
                    - `from` / `to`: dates, inclusive (US25)
                    - `categoryId`: one of your categories (US25)
                    - `q`: case-insensitive text in the description (US26)

                    e.g. `?periodId=3&categoryId=7&q=lunch&page=0&size=20`.

                    `periodId` and `from`/`to` are both date filters, so when a period and
                    explicit dates are given the dates narrow the period rather than replacing it.
                    A filter that matches nothing returns an empty page, not an error.""")
    @ApiResponse(responseCode = "200", description = "Page of expenses")
    @ApiResponse(responseCode = "400", description = "Invalid date, or 'from' after 'to'", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such period among yours", content = @Content)
    public PageResponse<ExpenseResponses.Details> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Only expenses dated inside this period; must be one of yours")
            @RequestParam(required = false) UUID periodId,
            @Parameter(description = "Only expenses on or after this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Only expenses on or before this date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only expenses in this category; must be one of yours")
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Case-insensitive text to look for in the description")
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = {"date", "createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable)
    {
        return PageResponse.of(
                expenseService.findMine(currentUserId(jwt), periodId, from, to, categoryId, q, pageable));
    }

    /** Declared before {@code /{id}} for readability; the literal path wins either way. */
    @GetMapping("/total")
    @Operation(summary = "Total of my expenses for a financial period (US39)",
            description = """
                    Sum of the expenses dated inside the period, both ends included, and how many
                    there are. Without `periodId` it is the current period; past periods come
                    from `GET /api/periods`. A period without expenses totals 0.""")
    @ApiResponse(responseCode = "200", description = "The period, its total and the number of expenses")
    @ApiResponse(responseCode = "404", description = "No such period among yours", content = @Content)
    public ExpenseResponses.Total total(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One of your periods; omit for the current one")
            @RequestParam(required = false) UUID periodId)
    {
        return expenseService.totalForPeriod(currentUserId(jwt), periodId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my expenses")
    @ApiResponse(responseCode = "200", description = "Expense found")
    @ApiResponse(responseCode = "404", description = "No such expense among yours", content = @Content)
    public ExpenseResponses.Details get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        return expenseService.findMine(currentUserId(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an expense",
            description = """
                    `amount` is an object, never a bare number: `{"amount": 12.50, "currency": "XAF"}`.
                    `currency` is optional and defaults to XAF. `date` must not be in the future,
                    and `categoryId` must be one of your categories (`GET /api/categories`).""")
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
    public ExpenseResponses.Details update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @Valid @RequestBody ExpenseRequests.SaveExpense request)
    {
        return expenseService.update(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense")
    @ApiResponse(responseCode = "204", description = "Expense deleted")
    @ApiResponse(responseCode = "404", description = "No such expense among yours", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        expenseService.delete(currentUserId(jwt), id);
    }

    /** {@code sub} holds the user id as a string (see {@code JwtService}). */
    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
