package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.util.List;

@Builder
public record BusinessAuditPreviewResultDto(
        int score,
        String level,
        List<String> teaserFindings,
        int lockedFindingsCount,
        String disclaimer
) {}
