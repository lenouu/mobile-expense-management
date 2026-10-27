package com.expensemanagement.backend.IncomeManagement.Repositories;

import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long>
{
    /** Scoped by owner: another user's id simply is not found. */
    Optional<Income> findByIdAndUserId(Long id, Long userId);

    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    Page<Income> findAllByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to, Pageable pageable);
}
