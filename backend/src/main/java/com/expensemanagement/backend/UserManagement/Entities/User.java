package com.expensemanagement.backend.UserManagement.Entities;

import com.expensemanagement.backend.Shared.Money;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Enums.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String userName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    /**
     * The unit every amount in this account is recorded in.
     *
     * <p>One currency per account rather than one per record, because the app sums and compares
     * amounts: without a conversion rate, mixing units would produce a total that is simply wrong
     * (50 EUR + 10 000 XAF is not 10 050 of anything). The choice lives here so every screen can
     * read it, and so a future multi-currency mode has a base to convert into.
     */
    @Column(nullable = false, length = 3)
    private String baseCurrency = Money.DEFAULT_CURRENCY;

    // Optional: an account can be created with only credentials, and the name is filled in
    // from the profile screen afterwards. Anything that displays a user falls back to the
    // username when these are null.
    private String firstName;

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
