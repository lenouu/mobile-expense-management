package com.expensemanagement.backend.CategoryManagement.Entities;

import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An expense category owned by one user (S2-TECH-1).
 *
 * <p>At account creation every active {@link DefaultCategory} is copied into one of these. The
 * copy is a snapshot: later admin edits to the default never reach existing users, which is why
 * {@code defaultCategoryId} is a plain column rather than a relation - it only records where the
 * copy came from.
 */
@Entity
@Table(name = "user_categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_categories_user_name", columnNames = {"user_id", "name"}),
        indexes = @Index(name = "idx_user_categories_user", columnList = "user_id"))
@Getter
@Setter
public class UserCategory
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String icon;

    @Column(length = 7)
    private String color;

    /** The default category this was copied from; null for a category the user created. */
    private Long defaultCategoryId;

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
