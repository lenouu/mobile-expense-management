package com.expensemanagement.backend.Monitoring.Entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An unexpected server error (HTTP 5xx), stored so administrators can review it (US10).
 *
 * <p>Written only by {@code GlobalExceptionHandler}; never edited afterwards, hence no setters.
 */
@Entity
@Table(name = "error_logs", indexes = @Index(name = "idx_error_logs_occurred_at", columnList = "occurredAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorLog
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 10)
    private String httpMethod;

    @Column(length = 2048)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private String exceptionType;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    public ErrorLog(Instant occurredAt, String httpMethod, String path, int status, String exceptionType,
                    String message, String stackTrace)
    {
        this.occurredAt = occurredAt;
        this.httpMethod = httpMethod;
        this.path = path;
        this.status = status;
        this.exceptionType = exceptionType;
        this.message = message;
        this.stackTrace = stackTrace;
    }
}
