package com.neelastack.dto.content;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ReviewDto(
        UUID id,
        UUID projectId,
        String authorName,
        String authorTitle,
        Integer rating,
        String reviewBody,
        boolean published,
        Integer displayOrder,
        String videoUrl,
        String submittedVia,
        LocalDateTime createdAt
) {}
