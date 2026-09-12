package com.neelastack.controller;

import com.neelastack.dto.notification.NotificationDto;
import com.neelastack.dto.notification.UnreadNotificationCountDto;
import com.neelastack.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Authenticated (client or admin) — every user reads their own notification feed only,
 *  enforced by NotificationService reading the current user off CurrentUserProvider rather
 *  than trusting any id from the request. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification feed for the current user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> list() {
        return notificationService.listMine();
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountDto unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/mark-all-read")
    public void markAllRead() {
        notificationService.markAllRead();
    }
}
