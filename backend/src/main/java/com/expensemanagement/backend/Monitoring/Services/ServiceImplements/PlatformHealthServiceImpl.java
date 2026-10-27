package com.expensemanagement.backend.Monitoring.Services.ServiceImplements;

import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses;
import com.expensemanagement.backend.Monitoring.Dtos.DtoResponses.MonitoringResponses.Status;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.ErrorLogService;
import com.expensemanagement.backend.Monitoring.Services.ServiceInterface.PlatformHealthService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;

@Service
public class PlatformHealthServiceImpl implements PlatformHealthService
{
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;

    /** Below this much free disk space the disk is reported DOWN. */
    private static final long MIN_FREE_DISK_BYTES = 100L * 1024 * 1024;

    private final DataSource dataSource;
    private final ErrorLogService errorLogService;
    private final String applicationName;
    private final String version;

    public PlatformHealthServiceImpl(DataSource dataSource,
                                     ErrorLogService errorLogService,
                                     ObjectProvider<BuildProperties> buildProperties,
                                     @Value("${spring.application.name}") String applicationName)
    {
        this.dataSource = dataSource;
        this.errorLogService = errorLogService;
        this.applicationName = applicationName;
        // BuildProperties comes from the spring-boot-maven-plugin build-info goal (see pom.xml).
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null ? build.getVersion() : "unknown";
    }

    @Override
    public MonitoringResponses.PlatformHealth check()
    {
        MonitoringResponses.Database database = checkDatabase();
        MonitoringResponses.Memory memory = checkMemory();
        MonitoringResponses.Disk disk = checkDisk();
        boolean allUp = database.getStatus() == Status.UP
                && memory.getStatus() == Status.UP
                && disk.getStatus() == Status.UP;

        return MonitoringResponses.PlatformHealth.builder()
                .status(allUp ? Status.UP : Status.DOWN)
                .timestamp(Instant.now())
                .applicationName(applicationName)
                .version(version)
                .uptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .database(database)
                .memory(memory)
                .disk(disk)
                .errorsLast24Hours(database.getStatus() == Status.UP ? errorsLast24Hours() : null)
                .build();
    }

    private MonitoringResponses.Database checkDatabase()
    {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection())
        {
            if (!connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS))
            {
                return MonitoringResponses.Database.builder().status(Status.DOWN).error("Connection is not valid").build();
            }
            return MonitoringResponses.Database.builder()
                    .status(Status.UP)
                    .responseTimeMs(Duration.ofNanos(System.nanoTime() - start).toMillis())
                    .build();
        }
        catch (Exception ex)
        {
            return MonitoringResponses.Database.builder().status(Status.DOWN).error(ex.getMessage()).build();
        }
    }

    private MonitoringResponses.Memory checkMemory()
    {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        return new MonitoringResponses.Memory(Status.UP, used, max, (int) Math.round(used * 100.0 / max));
    }

    private MonitoringResponses.Disk checkDisk()
    {
        File root = new File(".").getAbsoluteFile();
        long free = root.getUsableSpace();
        return new MonitoringResponses.Disk(free >= MIN_FREE_DISK_BYTES ? Status.UP : Status.DOWN, free,
                root.getTotalSpace());
    }

    private Long errorsLast24Hours()
    {
        try
        {
            return errorLogService.countSince(Instant.now().minus(Duration.ofHours(24)));
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }
}
