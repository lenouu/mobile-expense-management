package com.expensemanagement.backend.ExpenseManagement.Repositories;

import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>
{
    /**
     * Scoped by owner: another user's id simply is not found, so nobody can read or change
     * someone else's expense. The category is fetched with it because every response shows it.
     */
    @EntityGraph(attributePaths = "category")
    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAllByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to, Pageable pageable);
}
