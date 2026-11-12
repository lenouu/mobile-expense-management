package com.expensemanagement.backend.NotificationManagement.Repositories;

import com.expensemanagement.backend.NotificationManagement.Entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID>
{
    /** Newest first: the recap the user just missed is the one they want at the top. */
    List<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);
}
