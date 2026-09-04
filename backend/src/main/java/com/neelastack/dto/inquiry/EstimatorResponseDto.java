package com.neelastack.dto.inquiry;

import lombok.Builder;

@Builder
public record EstimatorResponseDto(
        InquiryDto inquiry,
        EstimateDto estimate
) {}
