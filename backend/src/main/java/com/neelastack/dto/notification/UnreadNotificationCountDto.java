package com.neelastack.dto.notification;

import lombok.Builder;

@Builder
public record UnreadNotificationCountDto(long unreadCount) {}
