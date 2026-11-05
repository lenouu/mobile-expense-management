package com.expensemanagement.backend.Monitoring.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses;
import com.expensemanagement.backend.Monitoring.Entities.ErrorLog;
import com.expensemanagement.backend.Monitoring.Repositories.ErrorLogRepository;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

@Service
@Slf4j
public class ErrorLogServiceImpl implements ErrorLogService
{
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_STACK_TRACE_LENGTH = 4000;

    /** Bounds used when the caller leaves {@code from} / {@code to} out. */
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59Z");

    private final ErrorLogRepository errorLogRepository;

    public ErrorLogServiceImpl(ErrorLogRepository errorLogRepository)
    {
        this.errorLogRepository = errorLogRepository;
    }

    // Deliberately not @Transactional: opening the transaction would itself fail while the
    // database is down, before the try block below could catch it.
    @Override
    public void record(Throwable error, HttpServletRequest request, int status)
    {
        try
        {
            errorLogRepository.save(new ErrorLog(Instant.now(), request.getMethod(), request.getRequestURI(),
                    status, error.getClass().getName(), truncate(error.getMessage(), MAX_MESSAGE_LENGTH),
                    truncate(stackTraceOf(error), MAX_STACK_TRACE_LENGTH)));
        }
        catch (RuntimeException recordingFailure)
        {
            log.warn("Could not record error in the error log: {}", recordingFailure.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MonitoringResponses.ErrorLogEntry> find(Instant from, Instant to, Pageable pageable)
    {
        if (from != null && to != null && from.isAfter(to))
        {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }

        return errorLogRepository
                .findByOccurredAtBetween(from != null ? from : EARLIEST, to != null ? to : LATEST, pageable)
                .map(ErrorLogServiceImpl::toEntry);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSince(Instant since)
    {
        return errorLogRepository.countByOccurredAtAfter(since);
    }

    private static MonitoringResponses.ErrorLogEntry toEntry(ErrorLog errorLog)
    {
        return MonitoringResponses.ErrorLogEntry.builder()
                .id(errorLog.getId())
                .occurredAt(errorLog.getOccurredAt())
                .httpMethod(errorLog.getHttpMethod())
                .path(errorLog.getPath())
                .status(errorLog.getStatus())
                .exceptionType(errorLog.getExceptionType())
                .message(errorLog.getMessage())
                .stackTrace(errorLog.getStackTrace())
                .build();
    }

    private static String stackTraceOf(Throwable error)
    {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
