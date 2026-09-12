package com.neelastack.dto.paymentschedule;

import com.neelastack.entity.InstallmentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record PaymentScheduleInstallmentDto(
        UUID id,
        String label,
        BigDecimal amount,
        BigDecimal percentage,
        LocalDate dueDate,
        InstallmentStatus status,
        UUID invoiceId,
        String invoiceNumber,
        Integer displayOrder
) {}
