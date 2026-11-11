package com.expensemanagement.backend.NotificationManagement.Services.ServiceImplements;

import com.expensemanagement.backend.Exceptions.ResourceNotFoundException;
import com.expensemanagement.backend.NotificationManagement.Dtos.NotificationResponses;
import com.expensemanagement.backend.NotificationManagement.Entities.Notification;
import com.expensemanagement.backend.NotificationManagement.Entities.NotificationKind;
import com.expensemanagement.backend.NotificationManagement.Repositories.NotificationRepository;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface.NotificationService;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class NotificationServiceImpl implements NotificationService
{
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   UserRepository userRepository)
    {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void notify(UUID userId, NotificationKind kind, String title, String body)
    {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setKind(kind);
        notification.setTitle(title);
        notification.setBody(body);

        notificationRepository.save(notification);
        log.info("Notified user id={} ({}): {}", userId, kind, title);
    }

    @Override
    public NotificationResponses.Inbox inbox(UUID userId)
    {
        List<NotificationResponses.Item> items = notificationRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationServiceImpl::toItem)
                .toList();

        return NotificationResponses.Inbox.builder()
                .items(items)
                .unread(notificationRepository.countByUserIdAndReadAtIsNull(userId))
                .build();
    }

    @Override
    @Transactional
    public NotificationResponses.Inbox markRead(UUID userId, UUID notificationId)
    {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No notification with id " + notificationId));

        if (notification.getReadAt() == null)
        {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }

        return inbox(userId);
    }

    @Override
    @Transactional
    public List<NotificationResponses.Item> markAllRead(UUID userId)
    {
        List<Notification> unread = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(notification -> notification.getReadAt() == null)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        unread.forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(unread);

        return inbox(userId).getItems();
    }

    private static NotificationResponses.Item toItem(Notification notification)
    {
        return NotificationResponses.Item.builder()
                .id(notification.getId())
                .kind(notification.getKind())
                .title(notification.getTitle())
                .body(notification.getBody())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
