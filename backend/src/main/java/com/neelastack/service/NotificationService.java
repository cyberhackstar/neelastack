package com.neelastack.service;

import com.neelastack.dto.notification.NotificationDto;
import com.neelastack.dto.notification.UnreadNotificationCountDto;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Notification;
import com.neelastack.entity.NotificationPriority;
import com.neelastack.entity.NotificationType;
import com.neelastack.entity.Role;
import com.neelastack.entity.User;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.NotificationRepository;
import com.neelastack.repository.UserRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * P0 #2 (client-workspace review): the common notification engine every other workflow
 * (milestones, invoices, UPI verification, payment schedules, change requests) fans out
 * through. Event -> Notification row (in-app) -> best-effort email, in that order. Mirrors
 * ProjectActivityService's REQUIRES_NEW + recordBestEffort/notifyBestEffort pattern
 * deliberately: a notification failing to write (or an email failing to send) must never be
 * able to roll back the business transaction that triggered it -- a payment confirming or a
 * milestone approving matters far more than the notification about it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final EmailService emailService;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(User recipient, Engagement engagement, NotificationType type, NotificationPriority priority,
                        String title, String body, String relatedEntityType, UUID relatedEntityId, String deepLink) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .engagement(engagement)
                .type(type)
                .priority(priority != null ? priority : NotificationPriority.MEDIUM)
                .title(title)
                .body(body)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .deepLink(deepLink)
                .build();

        notificationRepository.save(notification);

        // Best-effort: the async email send is fire-and-forget from here and already logs
        // its own failures (see EmailService#sendHtml) rather than throwing.
        emailService.sendNotificationEmail(
                recipient.getEmail(), recipient.getFullName(), title, body,
                deepLink != null ? frontendUrl + deepLink : null,
                deepLink != null ? "View in Neelastack" : null);
    }

    /** Same as {@link #notify}, but never propagates a failure to the caller. Every business
     *  call site should use this one -- see the class javadoc. */
    public void notifyBestEffort(User recipient, Engagement engagement, NotificationType type, NotificationPriority priority,
                                  String title, String body, String relatedEntityType, UUID relatedEntityId, String deepLink) {
        try {
            notify(recipient, engagement, type, priority, title, body, relatedEntityType, relatedEntityId, deepLink);
        } catch (Exception e) {
            log.error("Notification write failed for {} to {} — proceeding without it: {}",
                    type, recipient != null ? recipient.getEmail() : "unknown", e.getMessage());
        }
    }

    /** Convenience overload for engagement-scoped notifications, which is the overwhelming
     *  majority of call sites. */
    public void notifyBestEffort(User recipient, Engagement engagement, NotificationType type,
                                  String title, String body, String deepLink) {
        notifyBestEffort(recipient, engagement, type, NotificationPriority.MEDIUM, title, body, null, null, deepLink);
    }

    /** Fan-out to every admin/superadmin account — there's no per-engagement "which admin
     *  owns this" concept yet (a solo/small team runs the whole book), so admin-facing events
     *  (a milestone decision, a UPI payment claim) simply go to everyone with admin access. */
    public void notifyAllAdminsBestEffort(Engagement engagement, NotificationType type, NotificationPriority priority,
                                           String title, String body, String deepLink) {
        List<User> admins = userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.ADMIN, Role.SUPERADMIN));
        for (User admin : admins) {
            notifyBestEffort(admin, engagement, type, priority, title, body, null, null, deepLink);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> listMine() {
        User current = currentUserProvider.get();
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(current.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountDto unreadCount() {
        User current = currentUserProvider.get();
        return UnreadNotificationCountDto.builder()
                .unreadCount(notificationRepository.countByRecipientIdAndReadFalse(current.getId()))
                .build();
    }

    @Transactional
    public NotificationDto markRead(UUID id) {
        User current = currentUserProvider.get();
        Notification notification = notificationRepository.findByIdAndRecipientId(id, current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    @Transactional
    public void markAllRead() {
        User current = currentUserProvider.get();
        notificationRepository.markAllRead(current.getId(), LocalDateTime.now());
    }

    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .engagementId(n.getEngagement() != null ? n.getEngagement().getId() : null)
                .type(n.getType())
                .priority(n.getPriority())
                .title(n.getTitle())
                .body(n.getBody())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .deepLink(n.getDeepLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
