package com.expensemanagement.backend.NotificationManagement.Dtos;

import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class NotificationResponses
{
    /** One notification, as the app shows it. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Item
    {
        private final UUID id;
        private final NotificationKind kind;
        private final String title;
        private final String body;
        private final boolean read;
        private final LocalDateTime readAt;
        private final LocalDateTime createdAt;
    }

    /** The inbox: the rows plus the number that drives the badge. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Inbox
    {
        private final List<Item> items;
        private final long unread;
    }
}
