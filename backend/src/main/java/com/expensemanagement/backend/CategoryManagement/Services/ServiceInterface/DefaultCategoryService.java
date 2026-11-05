package com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DefaultCategoryService
{
    /** All categories, or only active / inactive ones when {@code active} is given. ADMIN only. */
    Page<CategoryResponses.Details> findAll(Boolean active, Pageable pageable);

    CategoryResponses.Details findById(Long id);

    /** @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the name is taken */
    CategoryResponses.Details create(CategoryRequests.SaveCategory request);

    /** @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the name is taken */
    CategoryResponses.Details update(Long id, CategoryRequests.SaveCategory request);

    CategoryResponses.Details changeStatus(Long id, boolean active);

    /** Active categories by name: the structure a new user starts with. Any signed-in user. */
    List<CategoryResponses.Summary> findActive();
}
