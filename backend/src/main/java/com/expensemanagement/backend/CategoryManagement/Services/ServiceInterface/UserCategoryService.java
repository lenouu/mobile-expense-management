package com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.UserManagement.Entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserCategoryService
{
    /**
     * Gives a freshly created account its own copy of every active default category.
     *
     * <p>Joins the caller's transaction, so an account is never left without its categories:
     * if the copy fails, the account creation is rolled back with it.
     *
     * @return how many categories were copied
     */
    int provisionDefaults(User user);

    /**
     * The caller's own categories, paged and sorted by the given pageable (name by default).
     *
     * @param q optional; matches part of the name, ignoring case. Blank means no filter.
     */
    Page<CategoryResponses.Owned> findMine(UUID userId, String q, Pageable pageable);

    /**
     * Creates a category of the caller's own (US17). Needs nothing else to exist first - in
     * particular no budget.
     *
     * <p>The new row belongs to the caller alone and is invisible to every other account.
     *
     * @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the caller
     *         already has a category with that name, ignoring case
     */
    CategoryResponses.Owned create(UUID userId, CategoryRequests.SaveCategory request);

    /**
     * Edits one of the caller's own categories (US18), as a full replace.
     *
     * <p>Only the caller's own row is touched: editing a category that was copied from a default
     * changes that user's snapshot alone, never the default and never another user's copy.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the category
     *         does not exist or belongs to someone else
     * @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the caller
     *         already has another category with that name, ignoring case
     */
    CategoryResponses.Owned update(UUID userId, UUID id, CategoryRequests.SaveCategory request);

    /**
     * Deletes one of the caller's own categories (US19).
     *
     * <p>Refused while expenses still reference it, so a user cannot silently orphan or destroy
     * spending history. The check is scoped to the caller's own category - another user's
     * category answers 404 exactly as a missing one does.
     *
     * @throws com.expensemanagement.backend.Exceptions.ResourceNotFoundException if the category
     *         does not exist or belongs to someone else
     * @throws com.expensemanagement.backend.Exceptions.OperationNotAllowedException if expenses
     *         still reference it
     */
    void delete(UUID userId, UUID id);
}
