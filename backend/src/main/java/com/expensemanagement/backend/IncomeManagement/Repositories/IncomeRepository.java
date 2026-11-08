package com.expensemanagement.backend.IncomeManagement.Repositories;

import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long>
{
    /** Scoped by owner: another user's id simply is not found. */
    Optional<Income> findByIdAndUserId(Long id, Long userId);

    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    Page<Income> findAllByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Database-level total for a user's incomes within a date range (inclusive on both ends).
     * Returns zero when there are no matching records, thanks to COALESCE.
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.user.id = :userId AND i.date BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);

    /** US13: number of income records in a date range, without loading the entities. */
    long countByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);
}
