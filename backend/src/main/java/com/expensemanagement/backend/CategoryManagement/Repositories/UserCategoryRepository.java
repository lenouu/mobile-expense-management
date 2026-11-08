package com.expensemanagement.backend.CategoryManagement.Repositories;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCategoryRepository extends JpaRepository<UserCategory, UUID>
{
    List<UserCategory> findAllByUserIdOrderByNameAsc(UUID userId);

    /** Scoped by owner, so another user's category is simply not found. */
    Optional<UserCategory> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Same check for a rename: another of the caller's categories may not already use the name. */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    /**
     * How many expenses are filed under one of the user's categories.
     *
     * <p>A native query rather than a relation so {@code CategoryManagement} keeps no compile-time
     * dependency on {@code ExpenseManagement} - the dependency runs the other way (expenses look
     * up categories). Deleting a category still in use has to be refused, not cascade silently.
     */
    @Query(value = "select count(*) from expenses where category_id = :categoryId", nativeQuery = true)
    long countExpensesUsing(@Param("categoryId") UUID categoryId);
}
