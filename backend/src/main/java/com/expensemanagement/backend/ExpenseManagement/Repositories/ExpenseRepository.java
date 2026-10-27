package com.expensemanagement.backend.ExpenseManagement.Repositories;

import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import com.expensemanagement.backend.ExpenseManagement.Repositories.Projections.CategorySpendingProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * S8-TECH-1: Aggregated total spending for a user between two dates (inclusive).
     * Returns 0 when no expenses exist in the period.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.user.id = :userId AND e.date BETWEEN :from AND :to")
    BigDecimal sumAmountByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * S8-TECH-1: Aggregated per-category spending for a user between two dates (inclusive).
     * Excludes other users' expenses and categories, ordered by total spent descending.
     */
    @Query("SELECT c.id AS categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor, SUM(e.amount) AS totalSpent " +
           "FROM Expense e JOIN e.category c " +
           "WHERE e.user.id = :userId AND c.user.id = :userId AND e.date BETWEEN :from AND :to " +
           "GROUP BY c.id, c.name, c.icon, c.color " +
           "ORDER BY SUM(e.amount) DESC")
    List<CategorySpendingProjection> findCategorySpendingByUserIdAndDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
