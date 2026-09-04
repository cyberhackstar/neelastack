package com.neelastack.dto.payment;

import com.neelastack.entity.InvoiceStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record InvoiceDto(
        UUID id,
        UUID engagementId,
        String invoiceNumber,
        String description,
        BigDecimal amount,
        String currency,
        InvoiceStatus status,
        LocalDate dueDate,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
