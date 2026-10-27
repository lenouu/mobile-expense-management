package com.expensemanagement.backend.Monitoring.Controllers;

import com.expensemanagement.backend.Swagger.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Throws on purpose so the error log can be tested by hand.
 *
 * <p>Only exists when {@code app.monitoring.simulate-error-enabled=true}; with the property
 * absent (the default) the endpoint is simply not registered.
 */
@RestController
@RequestMapping("/api/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnBooleanProperty("app.monitoring.simulate-error-enabled")
@Tag(name = "Platform monitoring")
@SecurityRequirement(name = SwaggerConfig.BEARER_SCHEME)
public class SimulatedErrorController
{
    @PostMapping("/simulate-error")
    @Operation(summary = "Trigger a test server error (testing only)",
            description = "Always fails with 500, so the error shows up in `GET /api/admin/logs/errors`.")
    public void simulateError()
    {
        throw new IllegalStateException("Simulated error for monitoring test");
    }
}
