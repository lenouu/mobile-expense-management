package com.expensemanagement.backend.UserManagement.Entities;

import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private LocalDate dateOfBirth;

    private String profilePictureReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAccountState status;

    /**
     * Privilege level. Never populated from a client request - see {@link #onCreate()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType type;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) {
            this.status = UserAccountState.PENDING;
        }
        // Default to the least privileged type. This is the backstop that makes
        // privilege escalation impossible even if a request DTO ever grows a
        // "type" field: a self-registering account is always USER, and only an
        // admin-only service operation may change it afterwards.
        if (this.type == null) {
            this.type = UserType.USER;
        }
    }
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
