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
 * A user's financial period length (S4-TECH-1), one row per user.
 *
 * <p>{@code anchorDate} and {@code anchorSequence} say where the current run of same-length
 * periods begins: period number {@code anchorSequence} starts on {@code anchorDate}, and the
 * following ones are counted from there (see {@code PeriodCalendar}). A length change starts a
 * new run at the beginning of the next period, so the period in progress keeps its dates.
 */
@Entity
@Table(name = "financial_period_settings")
@Getter
@Setter
public class FinancialPeriodSettings
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int lengthCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodUnit lengthUnit;

    @Column(nullable = false)
    private LocalDate anchorDate;

    @Column(nullable = false)
    private int anchorSequence;

    /** True until the user chooses a length themselves (US27). */
    @Column(nullable = false)
    private boolean defaultLength;

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
