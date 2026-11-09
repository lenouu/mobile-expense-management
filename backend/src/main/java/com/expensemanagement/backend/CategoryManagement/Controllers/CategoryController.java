package com.expensemanagement.backend.CategoryManagement.Controllers;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.DefaultCategoryService;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.UserCategoryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Categories for any signed-in user: their own categories, copied at sign-up (S2-TECH-1) or
 * created by them (US17), and a read-only view of the active default categories (US09).
 */
@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "The caller's own expense categories and the platform defaults")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class CategoryController
{
    private final DefaultCategoryService categoryService;
    private final UserCategoryService userCategoryService;

    public CategoryController(DefaultCategoryService categoryService, UserCategoryService userCategoryService)
    {
        this.categoryService = categoryService;
        this.userCategoryService = userCategoryService;
    }

    /**
     * The caller's own categories (S2-TECH-1). The user id comes from the token's {@code sub}
     * claim, never from the request, so nobody can read another user's categories.
     */
    @GetMapping
    @Operation(summary = "My categories",
            description = """
                    The caller's own categories, sorted by name. A new account starts with a copy
                    of every active default category; later admin changes to the defaults do not
                    affect these copies.""")
    @ApiResponse(responseCode = "200", description = "The caller's categories")
    @ApiResponse(responseCode = "401", description = "No valid token", content = @Content)
    public List<CategoryResponses.Owned> mine(@AuthenticationPrincipal Jwt jwt)
    {
        return userCategoryService.findMine(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create one of my categories",
            description = """
                    `name` is required and must not match one of your categories, ignoring case.
                    `description`, `icon` and `color` (hex, e.g. `#1F9D55`) are optional.

                    No budget is needed: the category can be used for expenses straight away.""")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "You already have a category with that name", content = @Content)
    public CategoryResponses.Owned create(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody CategoryRequests.SaveCategory request)
    {
        return userCategoryService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit one of my categories (US18)",
            description = """
                    Full replace: an omitted `description`, `icon` or `color` is cleared.

                    Only your own category changes. Editing a category you inherited from the
                    defaults rewrites your copy alone - the platform default and every other
                    user's copy stay as they were.""")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such category among yours", content = @Content)
    @ApiResponse(responseCode = "409", description = "You already have a category with that name", content = @Content)
    public CategoryResponses.Owned update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody CategoryRequests.SaveCategory request)
    {
        return userCategoryService.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of my categories (US19)",
            description = """
                    Only your own category can be deleted; another user's id answers 404.

                    Refused with 422 while expenses still reference the category, so spending
                    history is never silently orphaned. Move or delete those expenses first.""")
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(responseCode = "404", description = "No such category among yours", content = @Content)
    @ApiResponse(responseCode = "422", description = "Expenses still use this category", content = @Content)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        userCategoryService.delete(UUID.fromString(jwt.getSubject()), id);
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
