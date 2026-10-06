package com.expensemanagement.backend.CategoryManagement.Repositories;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCategoryRepository extends JpaRepository<UserCategory, Long>
{
    List<UserCategory> findAllByUserIdOrderByNameAsc(Long userId);
}
