package com.neelastack.dto.inquiry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record QuotationRequest(
        @NotNull java.util.UUID inquiryId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 8000) String scopeSummary,
        @NotEmpty @Valid List<QuotationLineItemDto> lineItems,
        @NotBlank @Pattern(regexp = "[A-Z]{3,8}", message = "Currency must be 3–8 uppercase letters") String currency,
        @FutureOrPresent LocalDate validUntil,
        String notes,
        @NotBlank @Size(max = 8000) String executiveSummary,
        @NotBlank @Size(max = 12000) String deliverables,
        @NotBlank @Size(max = 8000) String timeline,
        @NotBlank @Size(max = 4000) String paymentTerms,
        String assumptions,
        String exclusions,
        @NotBlank @Size(max = 4000) String nextSteps,
        /** Optional: which PricingRule this quotation was based on. See Quotation.pricingRuleId. */
        java.util.UUID pricingRuleId,
        /** Optional: service-line key for case-study matching (see Quotation.serviceCategory).
         *  Left null to let QuotationService auto-infer one from the source inquiry's
         *  free-text project type -- explicit value always wins when provided. */
        String serviceCategory
) {}
