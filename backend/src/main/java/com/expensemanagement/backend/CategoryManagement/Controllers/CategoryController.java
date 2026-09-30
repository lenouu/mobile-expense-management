package com.expensemanagement.backend.CategoryManagement.Controllers;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.DefaultCategoryService;
import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the default categories for any signed-in user (US09).
 *
 * <p>Only active categories, sorted by name: this is the structure a new user starts with.
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Default expense categories offered to users")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class CategoryController
{
    private final DefaultCategoryService categoryService;

    public CategoryController(DefaultCategoryService categoryService)
    {
        this.categoryService = categoryService;
    }

    @GetMapping("/defaults")
    @Operation(summary = "Active default categories",
            description = "The categories a new user starts with, sorted by name. Not paged: the list is short.")
    @ApiResponse(responseCode = "200", description = "Active default categories")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public List<CategoryResponses.Summary> defaults()
    {
        return categoryService.findActive();
    }
}
