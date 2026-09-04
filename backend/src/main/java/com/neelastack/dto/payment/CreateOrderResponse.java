package com.neelastack.dto.payment;

import lombok.Builder;

@Builder
public record CreateOrderResponse(
        String razorpayOrderId,
        String razorpayKeyId,
        long amountInPaise,
        String currency,
        String invoiceNumber,
        String description
) {}
