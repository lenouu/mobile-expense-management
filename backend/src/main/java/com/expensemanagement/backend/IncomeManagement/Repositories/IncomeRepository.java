package com.expensemanagement.backend.IncomeManagement.Repositories;

import com.expensemanagement.backend.IncomeManagement.Entities.Income;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID>
{
    /** Scoped by owner: another user's id simply is not found. */
    Optional<Income> findByIdAndUserId(UUID id, UUID userId);

    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    Page<Income> findAllByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * Database-level total for a user's incomes within a date range (inclusive on both ends).
     * Returns zero when there are no matching records, thanks to COALESCE.
     */
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Income i WHERE i.user.id = :userId AND i.date BETWEEN :from AND :to")
    BigDecimal sumByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** US13: number of income records in a date range, without loading the entities. */
    long countByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** The same window, one row per day - the shape of what came in. */
    @Query("""
            select i.date, coalesce(sum(i.amount), 0)
            from Income i
            where i.user.id = :userId and i.date between :from and :to
            group by i.date
            order by i.date""")
    List<Object[]> sumByDate(UUID userId, LocalDate from, LocalDate to);
    /** How many payments point at a source - the number the delete rule quotes. */
    long countBySourceIdAndUserId(UUID sourceId, UUID userId);

    /** The most recent payments from a source, so a refused delete can name them. */
    List<Income> findTop3BySourceIdAndUserIdOrderByDateDesc(UUID sourceId, UUID userId);

    /** The window split by where the money came from - the income list's summary block. */
    @Query("""
            select i.source.id, coalesce(sum(i.amount), 0), count(i)
            from Income i
            where i.user.id = :userId and i.date between :from and :to
            group by i.source.id""")
    List<Object[]> sumBySource(UUID userId, LocalDate from, LocalDate to);
}
