package com.neelastack.dto.upi;

import com.neelastack.entity.UpiSubmissionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UpiSubmissionDto(
        UUID id,
        UUID invoiceId,
        String invoiceNumber,
        UUID engagementId,
        String methodLabel,
        String submittedByName,
        String utrReference,
        String payerUpiId,
        BigDecimal amountClaimed,
        String screenshotUrl,
        UpiSubmissionStatus status,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime verifiedAt
) {}
