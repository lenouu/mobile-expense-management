package com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A pasted message for the AI reader: a mobile money SMS, a bank alert, any short text.
 *
 * <p>There is no provider field. The old parser had to be told which network's wording to
 * expect; the model reads any of them, so the client no longer has to guess first.
 */
public final class CaptureScanRequests
{
    private CaptureScanRequests()
    {
    }

    public record Scan(
            @NotBlank(message = "text is required")
            @Size(max = 8000, message = "text is too long")
            String text
    )
    {
    }
}
