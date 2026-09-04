package com.neelastack.dto.inquiry;

import lombok.Builder;

@Builder
public record AuditFindingDto(
        String title,
        String severity,
        String summary,
        String recommendation
) {}
