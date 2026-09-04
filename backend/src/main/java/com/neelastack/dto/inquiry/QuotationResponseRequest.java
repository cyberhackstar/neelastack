package com.neelastack.dto.inquiry;

import jakarta.validation.constraints.NotNull;

public record QuotationResponseRequest(
        @NotNull Boolean accept,
        String reason
) {}
