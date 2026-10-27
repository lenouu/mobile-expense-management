package com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Outgoing payloads for default categories (US09). Same shape as {@code UserResponses}.
 */
public class CategoryResponses
{
    /** What administrators see, including inactive categories and audit dates. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Details
    {
        private final Long id;
        private final String name;
        private final String description;
        private final String icon;
        private final String color;
        private final boolean active;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    /** One of the caller's own categories (S2-TECH-1). */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Owned
    {
        private final Long id;
        private final String name;
        private final String description;
        private final String icon;
        private final String color;
        /** The default it was copied from; null for a category the user created. */
        private final Long defaultCategoryId;
    }

    /** What regular users see: only active categories, so no status or dates. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary
    {
        private final Long id;
        private final String name;
        private final String description;
        private final String icon;
        private final String color;
    }
}
