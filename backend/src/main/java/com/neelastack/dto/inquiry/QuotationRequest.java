package com.neelastack.dto.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record QuotationRequest(
        @NotNull java.util.UUID inquiryId,
        @NotBlank String title,
        String scopeSummary,
        @NotEmpty @Valid List<QuotationLineItemDto> lineItems,
        String currency,
        LocalDate validUntil,
        String notes,
        /** Optional: which PricingRule this quotation was based on. See Quotation.pricingRuleId. */
        java.util.UUID pricingRuleId,
        /** Optional: service-line key for case-study matching (see Quotation.serviceCategory).
         *  Left null to let QuotationService auto-infer one from the source inquiry's
         *  free-text project type -- explicit value always wins when provided. */
        String serviceCategory
) {}
