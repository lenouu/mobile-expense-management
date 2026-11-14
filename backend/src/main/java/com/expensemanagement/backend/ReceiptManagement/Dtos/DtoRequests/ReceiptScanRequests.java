package com.expensemanagement.backend.ReceiptManagement.Dtos.DtoRequests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A picture to read, as the app sends it.
 *
 * <p>The bytes travel as base64 inside JSON rather than as a multipart file. That is deliberate:
 * the client already holds the compressed photo as base64 (it never has a filesystem path on the
 * web build), and one JSON body behaves the same on a phone, an emulator and a browser.
 *
 * <p>{@code imageBase64} is tolerant on purpose - a {@code data:image/jpeg;base64,} prefix,
 * newlines and missing padding are all accepted - because a decode failure should mean a bad
 * photo, not a formatting mismatch between two halves of the same app.
 */
public final class ReceiptScanRequests
{
    private ReceiptScanRequests()
    {
    }

    /**
     * @param imageBase64 the picture's bytes, base64-encoded (a data-URL prefix is allowed)
     * @param mimeType     e.g. {@code image/jpeg}; sniffed from the bytes when blank or unknown
     */
    public record Scan(
            @NotBlank(message = "imageBase64 is required")
            String imageBase64,

            @Size(max = 64, message = "mimeType is too long")
            String mimeType
    )
    {
    }
}
