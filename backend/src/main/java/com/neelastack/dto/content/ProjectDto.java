package com.neelastack.dto.content;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ProjectDto(
        UUID id,
        String title,
        String slug,
        String summary,
        String problemStatement,
        String solution,
        String outcome,
        String coverImageUrl,
        List<String> techStack,
        String liveUrl,
        String repoUrl,
        boolean featured,
        boolean published,
        // Published testimonials only — powers Review/AggregateRating structured data.
        // Never fabricated or backfilled: an empty list means the frontend emits no
        // review schema for this case study at all, which is correct behavior, not a
        // gap to paper over (a rating with zero real reviews behind it is exactly the
        // kind of unverifiable claim Google's structured-data spam policy targets).
        List<ReviewDto> reviews,
        Double averageRating,
        Integer reviewCount,
        // Module 3 (proposal case-study injection) — see Project#serviceCategories
        // and Project#keyMetrics. Both admin-entered, both empty by default.
        List<String> serviceCategories,
        List<String> keyMetrics
) {
    // Record components can't carry a default-value initializer (that's not valid Java
    // record syntax, regardless of @Builder.Default) — a compact constructor is the
    // correct place to normalize null -> empty list. This runs on every construction
    // path, including the Lombok-generated builder's build(), so ProjectDto.builder()
    // .build() without these set still yields List.of(), not null.
    public ProjectDto {
        reviews = reviews != null ? reviews : List.of();
        serviceCategories = serviceCategories != null ? serviceCategories : List.of();
        keyMetrics = keyMetrics != null ? keyMetrics : List.of();
    }
}
