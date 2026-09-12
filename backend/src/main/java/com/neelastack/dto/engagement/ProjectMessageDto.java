package com.neelastack.dto.engagement;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ProjectMessageDto(
        UUID id,
        UUID engagementId,
        UUID senderId,
        String senderName,
        String senderRole,
        String body,
        ProjectFileDto attachment,
        LocalDateTime createdAt
) {}
