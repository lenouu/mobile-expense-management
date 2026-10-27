package com.expensemanagement.backend.BudgetManagement.Entities;

import com.expensemanagement.backend.CategoryManagement.Entities.UserCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A category allocation under an overall budget (S4-TECH-2).
 * Total of these per budget must not exceed budget.overallAmount.
 */
@Entity
@Table(name = "category_allocations",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_allocations_budget_category",
                columnNames = {"budget_id", "category_id"}),
        indexes = @Index(name = "idx_category_allocations_budget", columnList = "budget_id"))
@Getter
@Setter
public class CategoryAllocation
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private UserCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allocatedAmount;

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
