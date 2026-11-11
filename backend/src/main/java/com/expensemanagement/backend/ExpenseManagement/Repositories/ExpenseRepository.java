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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>
{
    /**
     * Scoped by owner: another user's id simply is not found, so nobody can read or change
     * someone else's expense. The category is fetched with it because every response shows it.
     */
    @EntityGraph(attributePaths = "category")
    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    /** Used before changing the account's base currency: amounts already recorded pin it. */
    boolean existsByUserId(UUID userId);

    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAllByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to, Pageable pageable);

    /**
     * The listing query: every filter is optional and they combine.
     *
     * <p>{@code categoryId} and {@code pattern} are passed as null to mean "no such filter". Both
     * are used in a typed comparison as well as the null check, which is what lets Hibernate
     * determine their type - a bare {@code :param is null} on its own would not.
     *
     * <p>{@code pattern} is already lower-cased and wrapped in {@code %} by the service, so the
     * wildcards are not interpreted from user input.
     */
    @EntityGraph(attributePaths = {"category", "budget"})
    @Query("""
            select e from Expense e
            where e.user.id = :userId
              and e.date between :from and :to
              and (:categoryId is null or e.category.id = :categoryId)
              and (:pattern is null or lower(e.description) like :pattern)
              and (:budgetId is null or e.budget.id = :budgetId)""")
    Page<Expense> search(@Param("userId") UUID userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryId") UUID categoryId,
                         @Param("pattern") String pattern,
                         @Param("budgetId") UUID budgetId,
                         Pageable pageable);

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
    Totals totalsBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * The expenses assigned to one budget: the rows behind its spending figure.
     *
     * <p>Scoped by owner as well as by budget, so a budget id from another account finds nothing.
     */
    @EntityGraph(attributePaths = {"category", "budget"})
    Page<Expense> findAllByBudgetIdAndUserId(UUID budgetId, UUID userId, Pageable pageable);

    /** Everything assigned to a budget, used when the budget is deleted. */
    List<Expense> findAllByBudgetIdAndUserId(UUID budgetId, UUID userId);

    /** What a budget has spent, and how many expenses it covers. */
    @Query("""
            select coalesce(sum(e.amount), 0) as total, count(e) as count
            from Expense e
            where e.user.id = :userId and e.budget.id = :budgetId""")
    Totals totalsByBudget(@Param("userId") UUID userId, @Param("budgetId") UUID budgetId);

    /** A budget's spending split by category, for its allocations' own balances. */
    @Query("""
            select e.category.id, coalesce(sum(e.amount), 0)
            from Expense e
            where e.user.id = :userId and e.budget.id = :budgetId
            group by e.category.id""")
    List<Object[]> sumByCategoryForBudget(@Param("userId") UUID userId, @Param("budgetId") UUID budgetId);
    /**
     * The same window as {@link #totalsMatching}, split by category.
     *
     * <p>Used by a budget to give each allocation its own balance. Categories with no spending are
     * absent rather than zero, so the caller decides what a missing row means.
     */
    @Query("""
            select e.category.id, coalesce(sum(e.amount), 0)
            from Expense e
            where e.user.id = :userId and e.date between :from and :to
            group by e.category.id""")
    List<Object[]> sumByCategory(@Param("userId") UUID userId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /**
     * The same filters as {@link #search}, summed instead of paged, so a total shown above a list
     * always describes that list and not some other set of rows.
     */
    @Query("""
            select coalesce(sum(e.amount), 0) as total, count(e) as count
            from Expense e
            where e.user.id = :userId
              and e.date between :from and :to
              and (:categoryId is null or e.category.id = :categoryId)
              and (:pattern is null or lower(e.description) like :pattern)""")
    Totals totalsMatching(@Param("userId") UUID userId,
                          @Param("from") LocalDate from,
                          @Param("to") LocalDate to,
                          @Param("categoryId") UUID categoryId,
                          @Param("pattern") String pattern);
}
