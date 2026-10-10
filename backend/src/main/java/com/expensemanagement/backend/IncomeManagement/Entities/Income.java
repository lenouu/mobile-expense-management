package com.expensemanagement.backend.IncomeManagement.Entities;

import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Money a user received (S2-TECH-2), e.g. a salary or a sale.
 */
@Entity
@Table(name = "incomes", indexes = @Index(name = "idx_incomes_user_date", columnList = "user_id, date"))
@Getter
@Setter
public class Income
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BigDecimal, never double: money must not pick up rounding errors. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    /** Where the money came from, e.g. "Salary". */
    @Column(nullable = false, length = 100)
    private String source;

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
