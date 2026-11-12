package com.expensemanagement.backend.CaptureManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.CaptureManagement.Entities.MobileMoneyProvider;
import com.expensemanagement.backend.CaptureManagement.Parsers.MobileMoneySmsParser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class CaptureRequests
{
    /** Body of {@code POST /api/captures/sms}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmsCapture
    {
        /** The SMS exactly as the user copied it. Read once and not stored. */
        @NotBlank(message = "Paste the text of the SMS")
        @Size(max = MobileMoneySmsParser.MAX_LENGTH, message = "The message is too long to be an SMS")
        private String text;

        /** Optional: the service the user says it came from. Detected from the text when omitted. */
        private MobileMoneyProvider provider;
    }
}
