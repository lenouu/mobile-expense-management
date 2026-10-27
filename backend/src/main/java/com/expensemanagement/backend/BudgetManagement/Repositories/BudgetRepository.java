package com.expensemanagement.backend.BudgetManagement.Repositories;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long>
{
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    Optional<Budget> findByUserIdAndPeriodId(Long userId, Long periodId);

    List<Budget> findAllByUserId(Long userId);
}
