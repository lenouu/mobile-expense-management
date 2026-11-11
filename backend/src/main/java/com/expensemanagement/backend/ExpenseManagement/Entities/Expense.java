package com.expensemanagement.backend.ExpenseManagement.Entities;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Money a user spent (S2-TECH-2). Always filed under one of the owner's own categories.
 */
@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expenses_user_date", columnList = "user_id, date"))
@Getter
@Setter
public class Expense
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private UserCategory category;

    /** BigDecimal, never double: money must not pick up rounding errors. */
    /**
     * The budget this expense is counted against, or null for a normal expense.
     *
     * <p>Optional on purpose: the ledger is continuous, and a budget only sees the expenses the
     * user chose to put under it. Deleting a budget takes its expenses with it, which is why this
     * is not a soft link.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id")
    private Budget budget;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** ISO 4217 code of {@link #amount}; defaults to XAF when the client omits it. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate()
    {
        // Microseconds, because that is all PostgreSQL keeps: without truncating, the response
        // to a create would show a time that differs from the one stored and read back later.
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate()
    {
        this.updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
