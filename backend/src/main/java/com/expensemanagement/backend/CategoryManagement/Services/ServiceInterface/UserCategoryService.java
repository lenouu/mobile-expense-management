package com.expensemanagement.backend.CategoryManagement.Services.ServiceInterface;

import com.expensemanagement.backend.CategoryManagement.Dtos.DtoRequests.CategoryRequests;
import com.expensemanagement.backend.CategoryManagement.Dtos.DtoResponses.CategoryResponses;
import com.expensemanagement.backend.UserManagement.Entities.User;

import java.util.List;

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

    /** The caller's own categories, by name. */
    List<CategoryResponses.Owned> findMine(Long userId);

    /**
     * Creates a category of the caller's own (US17). Needs nothing else to exist first - in
     * particular no budget.
     *
     * @throws com.expensemanagement.backend.Exceptions.DuplicateResourceException if the caller
     *         already has a category with that name, ignoring case
     */
    CategoryResponses.Owned create(Long userId, CategoryRequests.SaveCategory request);
}
