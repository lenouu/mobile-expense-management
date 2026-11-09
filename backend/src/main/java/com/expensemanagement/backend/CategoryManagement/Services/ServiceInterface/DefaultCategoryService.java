package com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DefaultCategoryService
{
    /** All categories, or only active / inactive ones when {@code active} is given. ADMIN only. */
    Page<CategoryResponses.Details> findAll(Boolean active, Pageable pageable);

    CategoryResponses.Details findById(UUID id);

    /** @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the name is taken */
    CategoryResponses.Details create(CategoryRequests.SaveCategory request);

    /** @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the name is taken */
    CategoryResponses.Details update(UUID id, CategoryRequests.SaveCategory request);

    /**
     * Removes a default category permanently. ADMIN only.
     *
     * <p>Safe for existing accounts: a user's categories are snapshots taken at sign-up, not
     * references to this row, so deleting a default stops it being offered to new users without
     * touching anything already copied. Deactivate instead when you want to keep it visible to
     * administrators in the history.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if no such id
     */
    void delete(UUID id);

    CategoryResponses.Details changeStatus(UUID id, boolean active);

    /** Active categories by name: the structure a new user starts with. Any signed-in user. */
    List<CategoryResponses.Summary> findActive();
}
