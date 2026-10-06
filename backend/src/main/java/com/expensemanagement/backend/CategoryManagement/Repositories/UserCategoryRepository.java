package com.expensemanagement.backend.CategoryManagement.Repositories;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCategoryRepository extends JpaRepository<UserCategory, Long>
{
    List<UserCategory> findAllByUserIdOrderByNameAsc(Long userId);

    /** Scoped by owner, so another user's category is simply not found. */
    Optional<UserCategory> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
