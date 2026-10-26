package com.expensemanagement.backend.PeriodManagement.Entities;

import com.expensemanagement.backend.PeriodManagement.Enums.PeriodUnit;
import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One concrete financial period of a user (S4-TECH-1): a fixed window of dates, numbered 1, 2,
 * 3... Budgets (US28), savings (US44) and debt carry-forward (US48) hang off these.
 *
 * <p>Rows are created on demand by {@code FinancialPeriodService} and never change afterwards:
 * the length they were created with is copied in, so a later length change cannot move the dates
 * of a period that already exists.
 */
@Entity
@Table(name = "financial_periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_financial_periods_user_sequence",
                columnNames = {"user_id", "sequence"}),
        indexes = @Index(name = "idx_financial_periods_user_start", columnList = "user_id, start_date"))
@Getter
@Setter
public class FinancialPeriod
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 1 for the user's first period, then 2, 3... with no gaps. */
    @Column(nullable = false)
    private int sequence;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Inclusive: the period covers every day from startDate to endDate. */
    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int lengthCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodUnit lengthUnit;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean contains(LocalDate date)
    {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
