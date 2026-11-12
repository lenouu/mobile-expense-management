package com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface;

import com.expensemanagement.backend.NotificationManagement.Dtos.NotificationResponses;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;

import java.util.List;
import java.util.UUID;

public interface NotificationService
{
    /** Writes an in-app notification for the user. Called by whatever has news for them. */
    void notify(UUID userId, NotificationKind kind, String title, String body);

    /** The user's inbox, newest first, plus the unread count. */
    NotificationResponses.Inbox inbox(UUID userId);

    /** Marks one notification read. Another user's id is a 404, like a missing one. */
    NotificationResponses.Inbox markRead(UUID userId, UUID notificationId);

    /** Marks everything read; the badge goes to zero. */
    List<NotificationResponses.Item> markAllRead(UUID userId);
}
