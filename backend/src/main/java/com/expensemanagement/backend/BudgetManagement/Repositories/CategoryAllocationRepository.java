package com.expensemanagement.backend.BudgetManagement.Repositories;

import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryAllocationRepository extends JpaRepository<CategoryAllocation, Long>
{
    List<CategoryAllocation> findAllByBudgetId(Long budgetId);

    Optional<CategoryAllocation> findByIdAndBudgetId(Long id, Long budgetId);

    Optional<CategoryAllocation> findByBudgetIdAndCategoryId(Long budgetId, Long categoryId);

    @Query("SELECT COALESCE(SUM(ca.allocatedAmount), 0) FROM CategoryAllocation ca WHERE ca.budget.id = :budgetId")
    BigDecimal sumAllocationsByBudgetId(@Param("budgetId") Long budgetId);

    @Query("SELECT COALESCE(SUM(ca.allocatedAmount), 0) FROM CategoryAllocation ca WHERE ca.budget.id = :budgetId AND ca.id <> :excludeAllocationId")
    BigDecimal sumAllocationsByBudgetIdExcluding(@Param("budgetId") Long budgetId, @Param("excludeAllocationId") Long excludeAllocationId);
}
