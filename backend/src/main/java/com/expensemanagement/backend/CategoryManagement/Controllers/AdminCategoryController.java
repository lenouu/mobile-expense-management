package com.expensemanagement.backend.CategoryManagement.Controllers;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface.DefaultCategoryService;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * US09: lets administrators manage the platform's default expense categories.
 * <strong>ADMIN only.</strong>
 *
 * <p>Protected twice, like {@code AdminUserController}: {@code SecurityConfig} requires
 * {@code ROLE_ADMIN} for {@code /api/admin/**}, and the class-level {@code @PreAuthorize} travels
 * with the code if the route ever moves.
 *
 * <p>There is no DELETE on purpose: a category is deactivated instead, so it disappears for new
 * users without breaking anything already built from it.
 */
@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Default categories administration", description = "ADMIN only - manage default expense categories")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class AdminCategoryController
{
    private final DefaultCategoryService categoryService;

    public AdminCategoryController(DefaultCategoryService categoryService)
    {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List default categories (paged)",
            description = """
                    Omit `active` to list every category, including deactivated ones.

                    Example: `?active=true&page=0&size=20&sort=name,asc`""")
    @ApiResponse(responseCode = "200", description = "Page of categories")
    @ApiResponse(responseCode = "403", description = "Caller is not an ADMIN", content = @Content)
    public PagedModel<CategoryResponses.Details> list(
            @Parameter(description = "Filter by active flag; omit for all")
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable)
    {
        return new PagedModel<>(categoryService.findAll(active, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one default category")
    @ApiResponse(responseCode = "200", description = "Category found")
    @ApiResponse(responseCode = "404", description = "No such id", content = @Content)
    public CategoryResponses.Details getById(@PathVariable Long id)
    {
        return categoryService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a default category",
            description = """
                    `name` is required and unique ignoring case. `description`, `icon` and
                    `color` (hex, e.g. `#1F9D55`) are optional. New categories start active.""")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "Name already used", content = @Content)
    public CategoryResponses.Details create(@Valid @RequestBody CategoryRequests.SaveCategory request)
    {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a default category's details",
            description = """
                    Full replace: an omitted `description`, `icon` or `color` is cleared.
                    Does not change the active flag - use `PATCH /{id}/status` for that.""")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "404", description = "No such id", content = @Content)
    @ApiResponse(responseCode = "409", description = "Name already used", content = @Content)
    public CategoryResponses.Details update(@PathVariable Long id,
                                            @Valid @RequestBody CategoryRequests.SaveCategory request)
    {
        return categoryService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a default category",
            description = "A deactivated category is no longer offered to new users.")
    @ApiResponse(responseCode = "200", description = "Status changed")
    @ApiResponse(responseCode = "404", description = "No such id", content = @Content)
    public CategoryResponses.Details changeStatus(@PathVariable Long id,
                                                  @Valid @RequestBody CategoryRequests.UpdateCategoryStatus request)
    {
        return categoryService.changeStatus(id, request.getActive());
    }
}
