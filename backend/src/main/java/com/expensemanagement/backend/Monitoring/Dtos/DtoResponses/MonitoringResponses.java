package com.expensemanagement.backend.Monitoring.Dtos.DtoResponses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Outgoing payloads for the monitoring API (US10).
 *
 * <p>Same shape as {@code UserResponses}: a holder class with nested {@code public static}
 * payload classes, built with Lombok builders.
 */
public class MonitoringResponses
{
    public enum Status
    {
        UP,
        DOWN
    }

    /** Body of {@code GET /api/admin/health}. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class PlatformHealth
    {
        /** DOWN as soon as any component is DOWN. */
        private final Status status;
        private final Instant timestamp;
        private final String applicationName;
        private final String version;
        private final long uptimeSeconds;
        private final Database database;
        private final Memory memory;
        private final Disk disk;

        /** Errors recorded in the last 24 hours; null while the database cannot be read. */
        private final Long errorsLast24Hours;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Database
    {
        private final Status status;
        private final Long responseTimeMs;
        private final String error;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Memory
    {
        private final Status status;
        private final long usedBytes;
        private final long maxBytes;
        private final int usagePercent;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Disk
    {
        private final Status status;
        private final long freeBytes;
        private final long totalBytes;
    }

    /** One entry of {@code GET /api/admin/logs/errors}. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class ErrorLogEntry
    {
        private final Long id;
        private final Instant occurredAt;
        private final String httpMethod;
        private final String path;
        private final int status;
        private final String exceptionType;
        private final String message;
        private final String stackTrace;
    }
}
