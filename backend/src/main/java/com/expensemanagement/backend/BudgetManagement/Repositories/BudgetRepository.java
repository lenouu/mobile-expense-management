package com.expensemanagement.backend.BudgetManagement.Repositories;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID>
{
    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    /**
     * The caller's budgets, newest first.
     *
     * <p>Ordered by start date rather than status because status is derived: a budget's dates are
     * what say whether it is running, waiting or finished.
     */
    @Query("select b from Budget b where b.user.id = :userId order by b.startDate desc")
    List<Budget> findAllByUserIdNewestFirst(@Param("userId") UUID userId);

    /**
     * Any budget of the caller's whose window touches {@code [start, end]}.
     *
     * <p>One plan at a time, past or future: the check covers every budget the user has, so a new
     * plan cannot be laid over yesterday's any more than over tomorrow's.
     *
     * @param excludedBudgetId the budget being edited, which must not clash with itself
     */
    @Query("""
            select b from Budget b
            where b.user.id = :userId
              and (:excludedId is null or b.id <> :excludedId)
              and b.startDate <= :end
              and b.endDate >= :start""")
    Optional<Budget> findOverlapping(@Param("userId") UUID userId,
                                     @Param("excludedId") UUID excludedBudgetId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    /**
     * Every budget of the caller's whose window touches {@code [start, end]}.
     *
     * <p>Used by analytics to pick the best-overlapping budget for a financial period (US41, US42).
     */
    @Query("""
            select b from Budget b
            where b.user.id = :userId
              and b.startDate <= :end
              and b.endDate >= :start""")
    List<Budget> findAllOverlapping(@Param("userId") UUID userId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

    /** Every budget, of any user, running on {@code day}: the only ones whose edit window can be open. */
    @Query("select b from Budget b where b.startDate <= :day and b.endDate >= :day")
    List<Budget> findAllRunningOn(@Param("day") LocalDate day);

    /**
     * Records that the edit window closing at {@code closesAt} has been reminded about (US59).
     *
     * <p>One conditional update, so concurrent or repeated runs cannot both win: it only matches
     * while the budget still has these dates, edits left, and no reminder for this window.
     *
     * @return 1 if this call claimed the reminder, 0 if it was already sent or no longer applies
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Budget b set b.editReminderSentFor = :closesAt
            where b.id = :id
              and b.startDate = :start
              and b.endDate = :end
              and b.editsUsed < :editCap
              and (b.editReminderSentFor is null or b.editReminderSentFor <> :closesAt)""")
    int claimEditReminder(@Param("id") UUID id,
                          @Param("start") LocalDate start,
                          @Param("end") LocalDate end,
                          @Param("editCap") int editCap,
                          @Param("closesAt") LocalDateTime closesAt);
}
