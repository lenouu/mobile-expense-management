package com.expensemanagement.backend.Monitoring.Repositories;

import com.expensemanagement.backend.Monitoring.Entities.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>
{
    /** Inclusive on both ends; the service substitutes wide bounds when the caller gives none. */
    Page<ErrorLog> findByOccurredAtBetween(Instant from, Instant to, Pageable pageable);

    long countByOccurredAtAfter(Instant since);
}
