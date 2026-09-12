package com.neelastack.dto.upi;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpiPaymentMethodDto(
        UUID id,
        String label,
        String vpa,
        String payeeName,
        String qrImageUrl,
        boolean active,
        Integer displayOrder
) {}
