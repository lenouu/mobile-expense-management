package com.expensemanagement.backend.ExpenseManagement.Dtos.DtoRequests;

import com.expensemanagement.backend.Shared.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

public class ExpenseRequests {

    /** Used by both POST and PUT. PUT is a full replace, so an omitted description is cleared. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveExpense {

        /** {@code {"amount": 12.50, "currency": "XAF"}} - never a bare number. */
        @Valid
        @NotNull(message = "Amount is required")
        private Money amount;

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Date cannot be in the future")
        private LocalDate date;

        /** One of the caller's own categories (GET /api/categories). */
        @NotNull(message = "Category is required")
        private UUID categoryId;

        @Size(max = 255, message = "Description must be at most 255 characters")
        private String description;

        /**
         * Optional: the budget this expense counts against. Its date must fall inside that budget's
         * window, and a closed budget accepts nobody.
         */
        private UUID budgetId;
    }
}
