package com.expensemanagement.backend.PeriodManagement.Repositories;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, Long>
{
    Optional<FinancialPeriod> findFirstByUserIdOrderBySequenceDesc(Long userId);

    Optional<FinancialPeriod> findByUserIdAndSequence(Long userId, int sequence);

    /** The period covering {@code date}: periods never overlap, so there is at most one. */
    Optional<FinancialPeriod> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId, LocalDate date, LocalDate sameDate);

    /** Upcoming periods created ahead of time, dropped when the length changes. */
    long deleteByUserIdAndSequenceGreaterThan(Long userId, int sequence);

    Page<FinancialPeriod> findAllByUserIdAndSequenceLessThanEqual(Long userId, int maxSequence, Pageable pageable);
}
