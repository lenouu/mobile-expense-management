package com.expensemanagement.backend.NotificationManagement.Entities;

import com.expensemanagement.backend.UserManagement.Entities.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An in-app notification: something the app has to tell the user, waiting for them next time they
 * open it.
 *
 * <p>No push infrastructure yet, so this is the record the app reads on launch. When a device token
 * exists, the same row becomes the thing that is also pushed, rather than a second channel with its
 * own state.
 */
@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_user_created", columnList = "user_id, createdAt"))
@Getter
@Setter
public class Notification
{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** What kind of news it is, so the client can pick an icon without parsing the text. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationKind kind;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 400)
    private String body;

    /** Null until the user has seen it. Unread rows are what the badge counts. */
    private LocalDateTime readAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate()
    {
        this.createdAt = LocalDateTime.now();
    }
}
