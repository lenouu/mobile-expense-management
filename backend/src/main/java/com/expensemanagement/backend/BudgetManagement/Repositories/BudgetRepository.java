package com.expensemanagement.backend.BudgetManagement.Repositories;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID>
{
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    Optional<Budget> findByUserIdAndPeriodId(UUID userId, UUID periodId);

    List<Budget> findAllByUserId(UUID userId);
}
