package com.expensemanagement.backend.PeriodManagement.Repositories;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, UUID>
{
    Optional<FinancialPeriod> findFirstByUserIdOrderBySequenceDesc(UUID userId);

    /** Scoped by owner, so another user's period is simply not found. */
    Optional<FinancialPeriod> findByIdAndUserId(UUID id, UUID userId);

    Optional<FinancialPeriod> findByUserIdAndSequence(UUID userId, int sequence);

    /** The period covering {@code date}: periods never overlap, so there is at most one. */
    Optional<FinancialPeriod> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID userId, LocalDate date, LocalDate sameDate);

    /** Upcoming periods created ahead of time, dropped when the length changes. */
    long deleteByUserIdAndSequenceGreaterThan(UUID userId, int sequence);

    Page<FinancialPeriod> findAllByUserIdAndSequenceLessThanEqual(UUID userId, int maxSequence, Pageable pageable);
}
