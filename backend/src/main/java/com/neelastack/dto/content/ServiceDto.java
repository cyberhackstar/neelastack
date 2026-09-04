package com.neelastack.dto.content;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ServiceDto(
        UUID id,
        String title,
        String slug,
        String summary,
        String description,
        String icon,
        String startingPrice,
        Integer displayOrder,
        boolean published,
        // Real Q&A content, admin-authored — powers FAQPage structured data on the
        // frontend. Empty rather than null when there are none, so the frontend never
        // needs a null check before deciding whether to emit the schema.
        List<FaqDto> faqs
) {
    // Record components can't carry a default-value initializer (that's not valid Java
    // record syntax, regardless of @Builder.Default) — a compact constructor is the
    // correct place to normalize null -> empty list. This runs on every construction
    // path, including the Lombok-generated builder's build(), so ServiceDto.builder()
    // .build() without faqs() set still yields List.of(), not null.
    public ServiceDto {
        faqs = faqs != null ? faqs : List.of();
    }
}
