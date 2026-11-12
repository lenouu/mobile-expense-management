package com.expensemanagement.backend.IncomeManagement.Repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expensemanagement.backend.IncomeManagement.Entities.IncomeSource;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID>
{
    /** Scoped by owner: another user's id simply is not found. */
    Optional<IncomeSource> findByIdAndUserId(UUID id, UUID userId);

    List<IncomeSource> findAllByUserIdOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    Optional<IncomeSource> findFirstByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);

    long countByUserId(UUID userId);
}
