package com.neelastack.dto.inquiry;

import lombok.Builder;

import java.util.List;

@Builder
public record BusinessAuditUnlockResultDto(
        InquiryDto inquiry,
        int score,
        String level,
        List<BusinessAuditFindingDto> findings,
        List<String> recommendations,
        String disclaimer
) {}
