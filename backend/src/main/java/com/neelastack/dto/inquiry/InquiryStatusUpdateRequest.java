package com.neelastack.dto.inquiry;

import com.neelastack.entity.InquiryStatus;
import jakarta.validation.constraints.NotNull;

public record InquiryStatusUpdateRequest(
        @NotNull InquiryStatus status
) {}
