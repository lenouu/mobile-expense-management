package com.expensemanagement.backend.Monitoring.Services.ServiceInterface;

import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface ErrorLogService
{
    /**
     * Stores an unexpected error. Never throws: failing to record an error (for example because
     * the database itself is down) must not hide the original error from the caller.
     */
    void record(Throwable error, HttpServletRequest request, int status);

    /**
     * Errors between {@code from} and {@code to} (both optional, inclusive).
     *
     * @throws com.expensemanagement.backend.Exceptions.InvalidRequestException if from is after to
     */
    Page<MonitoringResponses.ErrorLogEntry> find(Instant from, Instant to, Pageable pageable);

    long countSince(Instant since);
}
