package com.expensemanagement.backend.CaptureManagement.Entities;

import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A transaction the app detected and that waits for the user's review (S6-TECH-2).
 *
 * <p>It is deliberately not an expense: nothing reaches the user's expenses, totals or budgets
 * until they confirm it, and then a normal expense is created and linked through {@link #expenseId}.
 *
 * <p>Only the fields read from the message are kept. The raw SMS text is never stored: it can hold
 * a balance, phone numbers and names the user did not ask us to keep. Receipt photos are not stored
 * either.
 *
 * <p>Any field the parser could not read is null rather than guessed; the review form shows it
 * blank so the user fills it in.
 */
@Entity
@Table(name = "captured_transactions",
        indexes = @Index(name = "idx_captures_user_status", columnList = "user_id, status"),
        // The same SMS pasted twice must not become two expenses. PostgreSQL treats NULLs as
        // distinct, so messages without a reference are not blocked by this.
        uniqueConstraints = @UniqueConstraint(name = "uk_captures_user_provider_reference",
                columnNames = {"user_id", "provider", "reference"}))
@Getter
@Setter
public class CapturedTransaction
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CaptureSource source;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MobileMoneyProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MobileMoneyTransactionType type;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Operator fee charged on top of the amount; null when the message did not state one. */
    @Column(precision = 12, scale = 2)
    private BigDecimal fee;

    /** Who received the money: a name, a phone number or both. */
    @Column(length = 120)
    private String counterparty;

    /** The operator's transaction id, used to refuse the same message twice. */
    @Column(length = 64)
    private String reference;

    private LocalDate transactionDate;

    private LocalTime transactionTime;

    @Column(length = 100)
    private String merchant;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaptureStatus status;

    /**
     * The expense created on confirmation. A plain id rather than a foreign key, so the user can
     * still delete that expense later like any other.
     */
    private UUID expenseId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** When it was confirmed or dismissed. */
    private LocalDateTime reviewedAt;

    /** Two taps on "Confirm" must not create two expenses: the second write fails instead. */
    @Version
    private Long version;

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
