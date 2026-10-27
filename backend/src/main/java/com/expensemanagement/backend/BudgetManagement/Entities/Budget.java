package com.expensemanagement.backend.BudgetManagement.Entities;

import com.expensemanagement.backend.PeriodManagement.Entities.FinancialPeriod;
import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * An overall budget for a user in a specific financial period (S4-TECH-2, US28).
 * Category allocations hang off this entity.
 */
@Entity
@Table(name = "budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_budgets_user_period", columnNames = {"user_id", "period_id"}),
        indexes = @Index(name = "idx_budgets_user_period", columnList = "user_id, period_id"))
@Getter
@Setter
public class Budget
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private FinancialPeriod period;

    /**
     * The overall budget amount. Total category allocations must not exceed this amount.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overallAmount;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoryAllocation> categoryAllocations = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate()
    {
        this.updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
