package com.expensemanagement.backend.ExpenseManagement.Repositories;

import com.expensemanagement.backend.ExpenseManagement.Entities.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /** Sum and count of a user's expenses dated between {@code from} and {@code to}, inclusive. */
    interface Totals
    {
        BigDecimal getTotal();

        long getCount();
    }

    /**
     * Summed by the database rather than in Java, so it stays one cheap query however many
     * expenses there are. COALESCE turns the SUM of no rows (NULL) into 0.
     */
    @Query("""
            select coalesce(sum(e.amount), 0) as total, count(e) as count
            from Expense e
            where e.user.id = :userId and e.date between :from and :to""")
    Totals totalsBetween(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
