package com.expensemanagement.backend.IncomeManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.Shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

public class IncomeRequests {

    /** Used by both POST and PUT. PUT is a full replace, so an omitted description is cleared. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @SourceProvided
    public static class SaveIncome {

        /** {@code {"amount": 250000.00, "currency": "XAF"}} - never a bare number. */
        @Valid
        @NotNull(message = "Amount is required")
        private Money amount;

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Date cannot be in the future")
        private LocalDate date;

        /**
         * The income source the user owns. Every payment comes from one: give either this or
         * {@link #source} (checked by {@link SourceProvided}). When both are given, this one wins.
         */
        private UUID sourceId;

        /**
         * The source by name, e.g. "Salary" - the shape clients used before income sources existed,
         * still accepted so they keep working. It is matched to the user's source of that name
         * (ignoring case), and a new source is created when there is none.
         */
        @Size(max = 60, message = "Source must be at most 60 characters")
        private String source;

        /** An optional label for this payment, e.g. "November salary". */
        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;

        @Size(max = 255, message = "Description must be at most 255 characters")
        private String description;
    }
}
