package com.expensemanagement.backend.BudgetManagement.Entities;

import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A spending goal the user set for a window of their own choosing (US28).
 *
 * <p>A budget is not tied to a financial period. It has its own start and end, so one can be a
 * fortnight of 30 000 and the next two months of 100 000 - the ledger keeps flowing underneath, and
 * a budget only counts the expenses the user explicitly put under it.
 *
 * <p>One budget is active at a time: windows may not overlap, past nor future. A budget that has to
 * wait for its start date is {@code UPCOMING}, one that is running is {@code ACTIVE} (and editable
 * for a short while), and one whose end has passed is {@code CLOSED} history with a verdict.
 */
@Entity
@Table(name = "budgets",
        indexes = {
                @Index(name = "idx_budgets_user_start", columnList = "user_id, startDate"),
                @Index(name = "idx_budgets_user_end", columnList = "user_id, endDate")
        })
@Getter
@Setter
public class Budget
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Optional: an unnamed budget is shown by its dates. */
    @Column(length = 60)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /**
     * The overall amount. When the plan is split across categories, the allocations must add up to
     * exactly this number - a split that does not complete the total is not a plan.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overallAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BudgetStatus status = BudgetStatus.UPCOMING;

    /** How many edits have been used. The cap lives in {@code BudgetWindow}. */
    @Column(nullable = false)
    private int editsUsed = 0;

    /**
     * When the budget was closed.
     *
     * <p>It doubles as the "already announced" flag: the recap notification is written only when
     * this is null, so a budget is never announced twice however often it is read.
     */
    private LocalDateTime closedAt;

    /**
     * The closing instant of the edit window a "closing soon" reminder was sent for (US59).
     *
     * <p>Keyed on the window rather than a plain flag: moving the dates opens a new window, which
     * earns its own reminder, while the same window is never reminded twice.
     *
     * <p>Not updatable: only {@code BudgetRepository.claimEditReminder} writes it, so a stale entity
     * saved by an edit or a status refresh cannot reset the claim and cause a second reminder.
     */
    @Column(updatable = false)
    private LocalDateTime editReminderSentFor;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoryAllocation> categoryAllocations = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** How many days the budget runs for, inclusive of both ends. */
    @Transient
    public long lengthInDays()
    {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

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
