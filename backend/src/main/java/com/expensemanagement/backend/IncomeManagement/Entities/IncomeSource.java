package com.expensemanagement.backend.IncomeManagement.Entities;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.expensemanagement.backend.UserManagement.Entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Where money came from - "Salary", "Freelance", "Gift".
 *
 * <p>The income counterpart of a user category, and deliberately separate from it: what you earn
 * and what you spend answer different questions, and merging the two lists would make "Salary"
 * selectable on an expense.
 *
 * <p>Scoped to its owner, with names unique per user rather than globally, so two people may both
 * have "Salary".
 */
@Entity
@Table(name = "income_sources",
        indexes = @Index(name = "idx_income_sources_user", columnList = "user_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_income_sources_user_name", columnNames = {"user_id", "name"}))
@Getter
@Setter
public class IncomeSource
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String name;

    /** An icon name the app knows how to draw; optional so a source can be plain text. */
    @Column(length = 40)
    private String icon;

    /** Hex colour, e.g. {@code #01916D}. */
    @Column(length = 9)
    private String color;

    /** A sentence on what belongs here, e.g. "Where regular money comes from". */
    @Column(length = 200)
    private String description;

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
