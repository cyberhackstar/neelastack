package com.neelastack.dto.paymentschedule;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PaymentScheduleDto(
        UUID id,
        UUID engagementId,
        BigDecimal totalAmount,
        String currency,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        List<PaymentScheduleInstallmentDto> installments
) {}
