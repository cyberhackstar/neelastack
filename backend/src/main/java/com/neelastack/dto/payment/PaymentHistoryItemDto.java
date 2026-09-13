package com.neelastack.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentHistoryItemDto(
        UUID id,
        String invoiceNumber,
        String description,
        UUID engagementId,
        String projectTitle,
        String clientName,
        String clientEmail,
        BigDecimal amount,
        String currency,
        String paymentSource,
        String paymentMethod,
        String paymentReference,
        LocalDateTime paidAt
) {}
