package com.neelastack.dto.inquiry;

import lombok.Builder;

@Builder
public record BusinessAuditFindingDto(
        String title,
        String priority,
        String summary,
        String opportunity
) {}
