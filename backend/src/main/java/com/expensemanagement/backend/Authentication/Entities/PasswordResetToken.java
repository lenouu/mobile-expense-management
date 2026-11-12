package com.expensemanagement.backend.Authentication.Entities;

import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A one-time password reset token (US06).
 *
 * <p>Only the SHA-256 hash of the token is stored, never the token itself: a leaked database
 * dump must not hand anyone a working reset link. The raw value exists only in the email that
 * was sent, and is impossible to recover from this row.
 *
 * <p>A token is single-use ({@code usedAt}) and short-lived ({@code expiresAt}), and issuing a
 * new one invalidates every token the user already had, so an attacker who triggered a reset
 * earlier cannot use the old link.
 */
@Entity
@Table(name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_password_reset_tokens_hash", columnList = "tokenHash"),
                @Index(name = "idx_password_reset_tokens_user", columnList = "user_id")
        })
@Getter
@Setter
public class PasswordResetToken
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Hex SHA-256 of the raw token: 64 characters, unique. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Set the moment the token is redeemed; a second attempt with the same token fails. */
    private Instant usedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate()
    {
        if (this.createdAt == null)
        {
            this.createdAt = Instant.now();
        }
    }
}
