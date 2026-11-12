package com.expensemanagement.backend.BudgetManagement.Reminders;

import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Sends due edit-window reminders in the background, so they arrive without the user opening a
 * budget (US59).
 *
 * <p>The shortest edit window (a 7-day budget) is about ten hours, so a 15-minute cadence lands
 * well inside its second half.
 */
@Component
@Slf4j
public class EditWindowReminderJob
{
    private final EditWindowReminderService reminders;

    public EditWindowReminderJob(EditWindowReminderService reminders)
    {
        this.reminders = reminders;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    public void run()
    {
        sendDueReminders(LocalDateTime.now());
    }

    /** @return how many reminders this run sent */
    int sendDueReminders(LocalDateTime now)
    {
        int sent = 0;
        for (Budget budget : reminders.findDue(now))
        {
            try
            {
                if (reminders.remind(budget, now))
                {
                    sent++;
                }
            }
            catch (RuntimeException e)
            {
                // One bad row must not starve the others; it stays unclaimed and is retried next run.
                log.warn("Edit-window reminder failed for budget id={}", budget.getId(), e);
            }
        }
        return sent;
    }
}
