package com.expensemanagement.backend.NotificationManagement.Controllers;

import com.expensemanagement.backend.NotificationManagement.Dtos.NotificationResponses;
import com.expensemanagement.backend.NotificationManagement.Services.ServiceInterface.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The in-app notification inbox.
 *
 * <p>Everything is scoped to the caller's token: there is no endpoint that reads a notification by
 * id alone, so one user's news cannot be read by another.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app messages such as a finished budget period")
public class NotificationController
{
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService)
    {
        this.notificationService = notificationService;
    }

    @Operation(summary = "The caller's notifications",
            description = "Newest first, with the unread count that drives the badge.")
    @GetMapping
    public NotificationResponses.Inbox inbox(@AuthenticationPrincipal Jwt jwt)
    {
        return notificationService.inbox(userId(jwt));
    }

    @Operation(summary = "Mark one notification read", description = "Returns the refreshed inbox.")
    @PatchMapping("/{id}/read")
    public NotificationResponses.Inbox markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
    {
        return notificationService.markRead(userId(jwt), id);
    }

    @Operation(summary = "Mark every notification read")
    @PatchMapping("/read-all")
    public ResponseEntity<List<NotificationResponses.Item>> markAllRead(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(notificationService.markAllRead(userId(jwt)));
    }

    private UUID userId(Jwt jwt)
    {
        return UUID.fromString(jwt.getSubject());
    }
}
