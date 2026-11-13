package com.expensemanagement.backend.BudgetManagement.Reminders;

import com.expensemanagement.backend.BudgetManagement.Calendar.BudgetWindow;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The "edit window closing soon" reminder (US59).
 *
 * <p>Due once half of a budget's edit window has gone by, while the window is still open and the
 * user still has an edit left. Each window is reminded about at most once.
 */
@Service
@Slf4j
public class EditWindowReminderService
{
    static final String TITLE = "Budget editing window closing soon";
    static final String BODY = "Your budget editing window is approaching its end. "
            + "Make any remaining corrections before it closes.";

    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;

    public EditWindowReminderService(BudgetRepository budgetRepository, NotificationService notificationService)
    {
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
    }

    /** The budgets whose reminder is due at {@code now} and not yet sent. */
    @Transactional(readOnly = true)
    public List<Budget> findDue(LocalDateTime now)
    {
        return budgetRepository.findAllRunningOn(now.toLocalDate()).stream()
                .filter(budget -> isDue(budget, now))
                .toList();
    }

    /**
     * Claims and writes the reminder for one budget, in one transaction: if the notification cannot
     * be written the claim rolls back too, and the next run tries again.
     *
     * @return whether this call sent it
     */
    @Transactional
    public boolean remind(Budget budget, LocalDateTime now)
    {
        if (!isDue(budget, now))
        {
            return false;
        }

        int claimed = budgetRepository.claimEditReminder(budget.getId(), budget.getStartDate(),
                budget.getEndDate(), BudgetWindow.EDIT_CAP, BudgetWindow.editableUntil(budget));
        if (claimed == 0)
        {
            return false;
        }

        notificationService.notify(budget.getUser().getId(), NotificationKind.EDIT_WINDOW_CLOSING, TITLE, BODY);
        log.info("Edit-window reminder sent for budget id={}", budget.getId());
        return true;
    }

    static boolean isDue(Budget budget, LocalDateTime now)
    {
        LocalDateTime closesAt = BudgetWindow.editableUntil(budget);

        return !now.isBefore(BudgetWindow.reminderDueAt(budget))
                && now.isBefore(closesAt)
                && BudgetWindow.hasEditsLeft(budget.getEditsUsed())
                && !closesAt.equals(budget.getEditReminderSentFor());
    }
}
