package com.neelastack.dto.content;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record BlogPostSummaryDto(
        UUID id,
        String title,
        String slug,
        String excerpt,
        String coverImageUrl,
        String authorName,
        String category,
        List<String> tags,
        boolean published,
        LocalDateTime publishedAt
) {}
