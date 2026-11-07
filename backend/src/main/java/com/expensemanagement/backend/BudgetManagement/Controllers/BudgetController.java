package com.expensemanagement.backend.BudgetManagement.Controllers;

import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.BudgetRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoRequests.CategoryAllocationRequests;
import com.expensemanagement.backend.BudgetManagement.Dtos.DtoResponses.BudgetResponses;
import com.expensemanagement.backend.BudgetManagement.Services.ServiceInterface.BudgetService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Budgets and category allocations (S4-TECH-2, US28).
 * Scoped by owner from the JWT token.
 */
@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "The caller's overall budgets and category allocations")
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
    @Operation(summary = "Create an overall budget for a period",
            description = "Creates a new overall budget tied to a financial period.")
    @ApiResponse(responseCode = "201", description = "Budget created")
    @ApiResponse(responseCode = "400", description = "Invalid request or validation failed", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "409", description = "Budget already exists for this period", content = @Content)
    public BudgetResponses.BudgetDetails create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BudgetRequests.CreateBudget request)
    {
        return budgetService.createBudget(currentUserId(jwt), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get budget by ID", description = "Returns a budget and its category allocations.")
    @ApiResponse(responseCode = "200", description = "Budget details")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.BudgetDetails getById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id)
    {
        return budgetService.getBudget(currentUserId(jwt), id);
    }

    @GetMapping("/period/{periodId}")
    @Operation(summary = "Get budget for a financial period", description = "Returns the budget for a specific period.")
    @ApiResponse(responseCode = "200", description = "Budget details")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.BudgetDetails getByPeriod(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long periodId)
    {
        return budgetService.getBudgetByPeriod(currentUserId(jwt), periodId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update overall budget amount",
            description = "Updates the overall amount. Must not be lower than the sum of current category allocations.")
    @ApiResponse(responseCode = "200", description = "Budget updated")
    @ApiResponse(responseCode = "400", description = "Invalid request or amount lower than current allocations", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.BudgetDetails update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody BudgetRequests.UpdateBudget request)
    {
        return budgetService.updateBudget(currentUserId(jwt), id, request);
    }

    @PostMapping("/{budgetId}/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Allocate an amount to a category",
            description = "Adds or updates an allocation for a category. Total allocations cannot exceed overall budget.")
    @ApiResponse(responseCode = "201", description = "Category allocated")
    @ApiResponse(responseCode = "400", description = "Exceeds overall budget or category invalid", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public BudgetResponses.AllocationDetails allocateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long budgetId,
            @Valid @RequestBody CategoryAllocationRequests.SaveAllocation request)
    {
        return budgetService.addOrUpdateAllocation(currentUserId(jwt), budgetId, request);
    }

    @PutMapping("/{budgetId}/allocations/{allocationId}")
    @Operation(summary = "Update a category allocation",
            description = "Updates an existing category allocation amount without double counting.")
    @ApiResponse(responseCode = "200", description = "Allocation updated")
    @ApiResponse(responseCode = "400", description = "Exceeds overall budget or invalid amount", content = @Content)
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget or allocation not found", content = @Content)
    public BudgetResponses.AllocationDetails updateAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long budgetId,
            @PathVariable Long allocationId,
            @Valid @RequestBody CategoryAllocationRequests.UpdateAllocation request)
    {
        return budgetService.updateAllocation(currentUserId(jwt), budgetId, allocationId, request);
    }

    @DeleteMapping("/{budgetId}/allocations/{allocationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a category allocation", description = "Removes a category allocation from a budget.")
    @ApiResponse(responseCode = "204", description = "Allocation deleted")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget or allocation not found", content = @Content)
    public void deleteAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long budgetId,
            @PathVariable Long allocationId)
    {
        budgetService.deleteAllocation(currentUserId(jwt), budgetId, allocationId);
    }

    @GetMapping("/{budgetId}/allocations")
    @Operation(summary = "List allocations for a budget", description = "Returns all category allocations for a budget.")
    @ApiResponse(responseCode = "200", description = "List of allocations")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    @ApiResponse(responseCode = "404", description = "Budget not found", content = @Content)
    public List<BudgetResponses.AllocationDetails> getAllocations(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long budgetId)
    {
        return budgetService.getAllocations(currentUserId(jwt), budgetId);
    }

    private Long currentUserId(Jwt jwt)
    {
        return Long.valueOf(jwt.getSubject());
    }
}
