package com.expensemanagement.backend.BudgetManagement.Repositories;

import com.expensemanagement.backend.BudgetManagement.Entities.CategoryAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryAllocationRepository extends JpaRepository<CategoryAllocation, UUID>
{
    List<CategoryAllocation> findAllByBudgetId(UUID budgetId);

    Optional<CategoryAllocation> findByIdAndBudgetId(UUID id, UUID budgetId);

    Optional<CategoryAllocation> findByBudgetIdAndCategoryId(UUID budgetId, UUID categoryId);

    @Query("SELECT COALESCE(SUM(ca.allocatedAmount), 0) FROM CategoryAllocation ca WHERE ca.budget.id = :budgetId")
    BigDecimal sumAllocationsByBudgetId(@Param("budgetId") UUID budgetId);

    @Query("SELECT COALESCE(SUM(ca.allocatedAmount), 0) FROM CategoryAllocation ca WHERE ca.budget.id = :budgetId AND ca.id <> :excludeAllocationId")
    BigDecimal sumAllocationsByBudgetIdExcluding(@Param("budgetId") UUID budgetId, @Param("excludeAllocationId") UUID excludeAllocationId);
}
