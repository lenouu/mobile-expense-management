package com.expensemanagement.backend.CategoryManagement.Mappers;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.CategoryManagement.Entities.DefaultCategory;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.UserManagement.Entities.User;
import org.springframework.util.StringUtils;

public final class CategoryMappers
{
    private CategoryMappers()
    {
        throw new AssertionError("CategoryMappers is a static utility class and cannot be instantiated");
    }

    public static CategoryResponses.Details toDetails(DefaultCategory category)
    {
        return CategoryResponses.Details.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .icon(category.getIcon())
                .color(category.getColor())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public static CategoryResponses.Summary toSummary(DefaultCategory category)
    {
        return CategoryResponses.Summary.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .icon(category.getIcon())
                .color(category.getColor())
                .build();
    }

    public static CategoryResponses.Owned toOwned(UserCategory category)
    {
        return CategoryResponses.Owned.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .icon(category.getIcon())
                .color(category.getColor())
                .defaultCategoryId(category.getDefaultCategoryId())
                .build();
    }

    /** A user's own snapshot of a default category, taken at account creation. */
    public static UserCategory toUserCategory(DefaultCategory source, User owner)
    {
        UserCategory category = new UserCategory();
        category.setUser(owner);
        category.setName(source.getName());
        category.setDescription(source.getDescription());
        category.setIcon(source.getIcon());
        category.setColor(source.getColor());
        category.setDefaultCategoryId(source.getId());
        return category;
    }

    /**
     * Copies every field of the request onto the category (create and full replace).
     *
     * <p>Text is trimmed and blanks become null, so " Food " and "Food" count as the same name
     * and an empty description is not stored as an empty string.
     */
    public static DefaultCategory apply(DefaultCategory category, CategoryRequests.SaveCategory request)
    {
        category.setName(request.getName().trim());
        category.setDescription(trimToNull(request.getDescription()));
        category.setIcon(trimToNull(request.getIcon()));
        category.setColor(trimToNull(request.getColor()));
        return category;
    }

    private static String trimToNull(String value)
    {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
