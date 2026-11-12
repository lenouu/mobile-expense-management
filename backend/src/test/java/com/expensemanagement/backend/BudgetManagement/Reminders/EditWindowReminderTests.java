package com.expensemanagement.backend.BudgetManagement.Reminders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.expensemanagement.backend.BudgetManagement.Calendar.BudgetWindow;
import com.expensemanagement.backend.BudgetManagement.Entities.Budget;
import com.expensemanagement.backend.BudgetManagement.Enums.BudgetStatus;
import com.expensemanagement.backend.BudgetManagement.Repositories.BudgetRepository;
import com.expensemanagement.backend.NotificationManagement.Dtos.NotificationResponses;
import com.expensemanagement.backend.NotificationManagement.Entities.Notification;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import com.expensemanagement.backend.NotificationManagement.Repositories.NotificationRepository;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceImplements.NotificationServiceImpl;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;

/**
 * US59: one "edit window closing soon" reminder, halfway through the window, while edits remain.
 *
 * <p>The repositories are backed by in-memory lists that behave like the queries they stand in for -
 * in particular the conditional claim - so the tests read what a user would find in their inbox.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditWindowReminderTests
{
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID OTHER_USER_ID = UUID.randomUUID();

	private static final LocalDate START = LocalDate.of(2026, 11, 1);

	// 30 days: window 2592 min = 43h12m, so it closes 11-02 19:12 and the reminder is due 11-01 21:36.
	private static final LocalDate MONTH_END = LocalDate.of(2026, 11, 30);
	private static final LocalDateTime MONTH_DUE = LocalDateTime.of(2026, 11, 1, 21, 36);
	private static final LocalDateTime MONTH_CLOSES = LocalDateTime.of(2026, 11, 2, 19, 12);

	// 7 days: window 605 min = 10h05m, so it closes 11-01 10:05 and the reminder is due 11-01 05:02:30.
	private static final LocalDate WEEK_END = LocalDate.of(2026, 11, 7);
	private static final LocalDateTime WEEK_DUE = LocalDateTime.of(2026, 11, 1, 5, 2, 30);
	private static final LocalDateTime WEEK_CLOSES = LocalDateTime.of(2026, 11, 1, 10, 5);

	@Mock
	private BudgetRepository budgetRepository;
	@Mock
	private NotificationRepository notificationRepository;
	@Mock
	private UserRepository userRepository;

	private final List<Budget> budgets = new ArrayList<>();
	private final List<Notification> notifications = new ArrayList<>();

	private NotificationServiceImpl notificationService;
	private EditWindowReminderJob job;

	@BeforeEach
	void setUp()
	{
		when(budgetRepository.findAllRunningOn(any())).thenAnswer(call -> {
			LocalDate day = call.getArgument(0);
			return budgets.stream()
					.filter(b -> !b.getStartDate().isAfter(day) && !b.getEndDate().isBefore(day))
					.toList();
		});
		when(budgetRepository.claimEditReminder(any(), any(), any(), anyInt(), any())).thenAnswer(call -> {
			UUID id = call.getArgument(0);
			LocalDate start = call.getArgument(1);
			LocalDate end = call.getArgument(2);
			int cap = call.getArgument(3);
			LocalDateTime closesAt = call.getArgument(4);
			for (Budget b : budgets)
			{
				if (b.getId().equals(id) && b.getStartDate().equals(start) && b.getEndDate().equals(end)
						&& b.getEditsUsed() < cap && !closesAt.equals(b.getEditReminderSentFor()))
				{
					b.setEditReminderSentFor(closesAt);
					return 1;
				}
			}
			return 0;
		});

		when(userRepository.getReferenceById(any())).thenAnswer(call -> user(call.getArgument(0)));
		when(notificationRepository.save(any(Notification.class))).thenAnswer(call -> {
			Notification n = call.getArgument(0);
			n.setId(UUID.randomUUID());
			n.setCreatedAt(LocalDateTime.now());
			notifications.add(n);
			return n;
		});
		when(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(any())).thenAnswer(call -> {
			UUID userId = call.getArgument(0);
			return notifications.stream().filter(n -> n.getUser().getId().equals(userId)).toList();
		});
		when(notificationRepository.countByUserIdAndReadAtIsNull(any())).thenAnswer(call -> {
			UUID userId = call.getArgument(0);
			return notifications.stream()
					.filter(n -> n.getUser().getId().equals(userId) && n.getReadAt() == null)
					.count();
		});

		notificationService = new NotificationServiceImpl(notificationRepository, userRepository);
		job = newJob();
	}

	/** A fresh service and job over the same stored rows - what a restarted application sees. */
	private EditWindowReminderJob newJob()
	{
		return new EditWindowReminderJob(new EditWindowReminderService(budgetRepository, notificationService));
	}

	private static User user(UUID id)
	{
		User user = new User();
		user.setId(id);
		return user;
	}

	private Budget budget(UUID userId, LocalDate start, LocalDate end, int editsUsed)
	{
		Budget budget = new Budget();
		budget.setId(UUID.randomUUID());
		budget.setUser(user(userId));
		budget.setStartDate(start);
		budget.setEndDate(end);
		budget.setOverallAmount(BigDecimal.valueOf(100_000));
		budget.setStatus(BudgetStatus.ACTIVE);
		budget.setEditsUsed(editsUsed);
		budgets.add(budget);
		return budget;
	}

	private List<Notification> remindersFor(UUID userId)
	{
		return notifications.stream()
				.filter(n -> n.getUser().getId().equals(userId))
				.filter(n -> n.getKind() == NotificationKind.EDIT_WINDOW_CLOSING)
				.toList();
	}

	// ------------------------------------------------------------------
	// Timing
	// ------------------------------------------------------------------

	@Test
	@DisplayName("each duration gets its own midpoint, from the existing 6% window")
	void midpointFollowsTheBudgetsOwnWindow()
	{
		Budget month = budget(USER_ID, START, MONTH_END, 0);
		Budget week = budget(USER_ID, START, WEEK_END, 0);

		assertThat(BudgetWindow.editableUntil(month)).isEqualTo(MONTH_CLOSES);
		assertThat(BudgetWindow.reminderDueAt(month)).isEqualTo(MONTH_DUE);
		assertThat(BudgetWindow.editableUntil(week)).isEqualTo(WEEK_CLOSES);
		assertThat(BudgetWindow.reminderDueAt(week)).isEqualTo(WEEK_DUE);
	}

	@Test
	@DisplayName("at one instant a weekly budget can be due while a monthly one is not yet")
	void differentDurationsAreDueAtDifferentTimes()
	{
		Budget week = budget(USER_ID, START, WEEK_END, 0);
		Budget month = budget(OTHER_USER_ID, START, MONTH_END, 0);

		LocalDateTime now = START.atTime(LocalTime.of(6, 0));

		assertThat(EditWindowReminderService.isDue(week, now)).isTrue();
		assertThat(EditWindowReminderService.isDue(month, now)).isFalse();
	}

	@Test
	@DisplayName("one second before the midpoint: no reminder")
	void beforeTheMidpoint()
	{
		budget(USER_ID, START, MONTH_END, 0);

		assertThat(job.sendDueReminders(MONTH_DUE.minusSeconds(1))).isZero();
		assertThat(remindersFor(USER_ID)).isEmpty();
	}

	@Test
	@DisplayName("exactly at the midpoint: reminded")
	void atTheMidpoint()
	{
		budget(USER_ID, START, MONTH_END, 0);

		assertThat(job.sendDueReminders(MONTH_DUE)).isEqualTo(1);
		assertThat(remindersFor(USER_ID)).hasSize(1);
	}

	@Test
	@DisplayName("after the midpoint, window still open: reminded")
	void afterTheMidpointWhileOpen()
	{
		budget(USER_ID, START, MONTH_END, 0);

		assertThat(job.sendDueReminders(MONTH_CLOSES.minusSeconds(1))).isEqualTo(1);
		assertThat(remindersFor(USER_ID)).hasSize(1);
	}

	@Test
	@DisplayName("at or after the window closes: no reminder")
	void windowClosed()
	{
		budget(USER_ID, START, MONTH_END, 0);

		assertThat(job.sendDueReminders(MONTH_CLOSES)).isZero();
		assertThat(job.sendDueReminders(MONTH_CLOSES.plusDays(3))).isZero();
		assertThat(remindersFor(USER_ID)).isEmpty();
	}

	@Test
	@DisplayName("a budget that has not started: no reminder")
	void notStarted()
	{
		Budget upcoming = budget(USER_ID, START.plusDays(1), MONTH_END, 0);
		LocalDateTime now = START.atTime(23, 59);

		assertThat(EditWindowReminderService.isDue(upcoming, now)).isFalse();
		assertThat(job.sendDueReminders(now)).isZero();
		assertThat(remindersFor(USER_ID)).isEmpty();
	}

	// ------------------------------------------------------------------
	// Edits left
	// ------------------------------------------------------------------

	@Test
	@DisplayName("no edits left: no reminder")
	void noEditsLeft()
	{
		budget(USER_ID, START, MONTH_END, BudgetWindow.EDIT_CAP);

		assertThat(job.sendDueReminders(MONTH_DUE)).isZero();
		assertThat(remindersFor(USER_ID)).isEmpty();
	}

	@Test
	@DisplayName("one edit left: reminded")
	void oneEditLeft()
	{
		budget(USER_ID, START, MONTH_END, BudgetWindow.EDIT_CAP - 1);

		assertThat(job.sendDueReminders(MONTH_DUE)).isEqualTo(1);
		assertThat(remindersFor(USER_ID)).hasSize(1);
	}

	// ------------------------------------------------------------------
	// Once per window
	// ------------------------------------------------------------------

	@Test
	@DisplayName("repeated runs write one reminder")
	void repeatedRunsDoNotDuplicate()
	{
		Budget budget = budget(USER_ID, START, MONTH_END, 0);

		job.sendDueReminders(MONTH_DUE);
		job.sendDueReminders(MONTH_DUE.plusMinutes(15));
		job.sendDueReminders(MONTH_DUE.plusMinutes(30));

		assertThat(remindersFor(USER_ID)).hasSize(1);
		assertThat(budget.getEditReminderSentFor()).isEqualTo(MONTH_CLOSES);
	}

	@Test
	@DisplayName("after a restart the stored claim still prevents a second reminder")
	void restartDoesNotDuplicate()
	{
		budget(USER_ID, START, MONTH_END, 0);
		job.sendDueReminders(MONTH_DUE);

		EditWindowReminderJob afterRestart = newJob();

		assertThat(afterRestart.sendDueReminders(MONTH_DUE.plusHours(1))).isZero();
		assertThat(remindersFor(USER_ID)).hasSize(1);
	}

	@Test
	@DisplayName("two overlapping runs that both saw the budget as due write one reminder")
	void overlappingRunsDoNotDuplicate()
	{
		budget(USER_ID, START, MONTH_END, 0);
		EditWindowReminderService first = new EditWindowReminderService(budgetRepository, notificationService);
		EditWindowReminderService second = new EditWindowReminderService(budgetRepository, notificationService);

		List<Budget> seenByFirst = first.findDue(MONTH_DUE);
		List<Budget> seenBySecond = second.findDue(MONTH_DUE);
		Budget staleCopy = new Budget();
		staleCopy.setId(seenBySecond.get(0).getId());
		staleCopy.setUser(seenBySecond.get(0).getUser());
		staleCopy.setStartDate(START);
		staleCopy.setEndDate(MONTH_END);
		staleCopy.setEditsUsed(0);

		assertThat(first.remind(seenByFirst.get(0), MONTH_DUE)).isTrue();
		assertThat(second.remind(staleCopy, MONTH_DUE)).isFalse();
		assertThat(remindersFor(USER_ID)).hasSize(1);
	}

	@Test
	@DisplayName("moving the dates opens a new window, which gets its own reminder")
	void aNewWindowGetsItsOwnReminder()
	{
		Budget budget = budget(USER_ID, START, MONTH_END, 0);
		job.sendDueReminders(MONTH_DUE);

		budget.setStartDate(START.plusDays(5));
		budget.setEndDate(MONTH_END.plusDays(5));
		budget.setEditsUsed(1);

		assertThat(job.sendDueReminders(MONTH_DUE.plusDays(5))).isEqualTo(1);
		assertThat(remindersFor(USER_ID)).hasSize(2);
	}

	// ------------------------------------------------------------------
	// Ownership and the inbox
	// ------------------------------------------------------------------

	@Test
	@DisplayName("each reminder goes to the budget's owner only")
	void remindersAreUserScoped()
	{
		budget(USER_ID, START, MONTH_END, 0);
		budget(OTHER_USER_ID, START, MONTH_END, BudgetWindow.EDIT_CAP);

		job.sendDueReminders(MONTH_DUE);

		assertThat(remindersFor(USER_ID)).hasSize(1);
		assertThat(remindersFor(OTHER_USER_ID)).isEmpty();
		assertThat(notificationService.inbox(OTHER_USER_ID).getItems()).isEmpty();
	}

	@Test
	@DisplayName("the inbox shows the reminder with its kind and wording")
	void inboxExposesTheReminder()
	{
		budget(USER_ID, START, MONTH_END, 0);
		job.sendDueReminders(MONTH_DUE);

		NotificationResponses.Inbox inbox = notificationService.inbox(USER_ID);

		assertThat(inbox.getUnread()).isEqualTo(1);
		assertThat(inbox.getItems()).hasSize(1);
		NotificationResponses.Item item = inbox.getItems().get(0);
		assertThat(item.getKind()).isEqualTo(NotificationKind.EDIT_WINDOW_CLOSING);
		assertThat(item.getTitle()).isEqualTo("Budget editing window closing soon");
		assertThat(item.getBody()).contains("approaching its end");
		assertThat(item.isRead()).isFalse();
	}
}
