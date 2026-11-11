package com.expensemanagement.backend.BudgetManagement.Controllers;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetService;
import com.expensemanagement.backend.ExpenseManagement.Dtos.DtoResponses.ExpenseResponses;
import com.expensemanagement.backend.Shared.PageResponse;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Budgets (US28-US33).
 *
 * <p>A budget is a plan for a window the user chooses, not for a financial period: it can be two
 * months of 100 000 or one week of 30 000, and only one may run at a time. Expenses join it
 * explicitly, which is why a budget's spending is a sum of assigned rows rather than a date range.
 */
@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "The caller's budgets, their allocations and their expenses")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class BudgetController
{
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService)
    {
        this.budgetService = budgetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a budget",
            description = "The window, the optional name, the amount and the optional split, in one call. "
                    + "A split must add up to the amount, and the window may not overlap another budget.")
    @ApiResponse(responseCode = "201", description = "Budget created")
    @ApiResponse(responseCode = "400",
            description = "Invalid window, overlapping dates, or an unbalanced split", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public BudgetResponses.BudgetDetails create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BudgetRequests.CreateBudget request)
    {
        return budgetService.createBudget(currentUserId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "List the caller's budgets",
            description = "Newest window first. Reading a budget whose window has ended closes it and "
                    + "writes its recap.")
    @ApiResponse(responseCode = "200", description = "Budgets, newest first")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public List<BudgetResponses.BudgetSummary> list(@AuthenticationPrincipal Jwt jwt)
    {
        return budgetService.listMine(currentUserId(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one budget",
            description = "Amounts, spending, allocations, edit allowance and, once closed, the verdict.")
    @ApiResponse(responseCode = "200", description = "Budget details")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.BudgetDetails getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id)
    {
        return budgetService.getBudget(currentUserId(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a budget's plan",
            description = "Accepted only while the budget is editable: before its start, then within the "
                    + "first 6% of its window, and at most three times. Counts as one edit.")
    @ApiResponse(responseCode = "200", description = "Budget updated")
    @ApiResponse(responseCode = "400", description = "Fixed, closed, or an invalid plan", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.BudgetDetails update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody BudgetRequests.UpdateBudget request)
    {
        return budgetService.updateBudget(currentUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a budget",
            description = "Deletes the budget and the expenses recorded under it. A finished budget "
                    + "cannot be deleted - it is history.")
    @ApiResponse(responseCode = "204", description = "Budget and its expenses deleted")
    @ApiResponse(responseCode = "400", description = "The budget has closed", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        budgetService.deleteBudget(currentUserId(jwt), id);
    }

    @GetMapping("/{id}/expenses")
    @Operation(summary = "The expenses assigned to a budget",
            description = "The rows behind the spending figure, paged the same way as the ledger.")
    @ApiResponse(responseCode = "200", description = "Expenses counted under this budget")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public PageResponse<ExpenseResponses.Details> expenses(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable)
    {
        return PageResponse.of(budgetService.expensesOf(currentUserId(jwt), id, pageable));
    }

    private UUID currentUserId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
