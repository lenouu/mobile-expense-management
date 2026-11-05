package com.expensemanagement.backend.Monitoring.Services.ServiceInterface;

import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses;

public interface PlatformHealthService
{
    /** Checks database, memory and disk right now. Never throws; a failing part is reported DOWN. */
    MonitoringResponses.PlatformHealth check();
}
