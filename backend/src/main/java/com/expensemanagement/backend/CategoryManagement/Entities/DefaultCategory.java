package com.expensemanagement.backend.CategoryManagement.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A platform-wide expense category that new users start with (US09).
 *
 * <p>Managed by administrators only. Never hard-deleted: {@code active = false} hides it from
 * new users while keeping the row, so anything already derived from it stays consistent.
 */
@Entity
@Table(name = "default_categories")
@Getter
@Setter
public class DefaultCategory
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique ignoring case; the service checks this before insert to answer 409, not 500. */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /** Icon name the mobile app knows how to draw, e.g. "cart". Optional. */
    @Column(length = 50)
    private String icon;

    /** Hex color such as "#1F9D55". Optional. */
    @Column(length = 7)
    private String color;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate()
    {
        this.updatedAt = LocalDateTime.now();
    }
}
