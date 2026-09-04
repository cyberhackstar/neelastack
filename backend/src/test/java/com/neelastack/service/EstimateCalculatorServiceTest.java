package com.neelastack.service;

import com.neelastack.dto.inquiry.EstimateDto;
import com.neelastack.dto.pricing.PricingRuleDto;
import com.neelastack.entity.InquiryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstimateCalculatorServiceTest {

    @Mock
    private PricingRuleService pricingRuleService;

    private EstimateCalculatorService service;

    @BeforeEach
    void setUp() {
        service = new EstimateCalculatorService(pricingRuleService);
    }

    private PricingRuleDto rule(String key, long low, Long high) {
        return PricingRuleDto.builder()
                .serviceKey(key)
                .baseLow(BigDecimal.valueOf(low))
                .baseHigh(high == null ? null : BigDecimal.valueOf(high))
                .complexityFactor(BigDecimal.valueOf(0.2))
                .scaleFactor(BigDecimal.valueOf(0.25))
                .integrationFactor(BigDecimal.valueOf(0.15))
                .urgencyFactor(BigDecimal.valueOf(0.1))
                .active(true)
                .version(1)
                .build();
    }

    @Test
    void enterpriseScope_returnsNullRangeWithCustomDisclaimer() {
        when(pricingRuleService.getActiveRule(eq("enterprise-platform")))
                .thenReturn(Optional.of(rule("enterprise-platform", 500_000, null)));

        EstimateDto estimate = service.calculate(
                InquiryIntent.BUILD, "Enterprise platform", null, List.of(), "6 months");

        assertThat(estimate.low()).isNull();
        assertThat(estimate.high()).isNull();
        assertThat(estimate.disclaimer()).contains("scoped conversation");
    }

    @Test
    void noActiveRuleConfigured_returnsScopedConversationRatherThanAGuess() {
        when(pricingRuleService.getActiveRule(eq("audit-review"))).thenReturn(Optional.empty());

        EstimateDto estimate = service.calculate(
                InquiryIntent.MODERNIZE, "Code audit / performance review", null, List.of(), "2 weeks");

        assertThat(estimate.low()).isNull();
        assertThat(estimate.high()).isNull();
        assertThat(estimate.disclaimer()).contains("scoped conversation");
    }

    @Test
    void auditRequest_usesConfiguredRange() {
        when(pricingRuleService.getActiveRule(eq("audit-review")))
                .thenReturn(Optional.of(rule("audit-review", 25_000, 75_000L)));

        // Timeline deliberately non-urgent ("3 months", matching the "normal" baseline used
        // elsewhere in this suite, e.g. urgentRequest_bumpsBothBoundsByConfiguredUrgencyFactor) —
        // isUrgent() treats strings like "2 week"/"1 week" as a rush signal and bumps both
        // bounds, which this test isn't exercising.
        EstimateDto estimate = service.calculate(
                InquiryIntent.MODERNIZE, "Code audit / performance review", null, List.of(), "3 months");

        assertThat(estimate.low()).isEqualByComparingTo(BigDecimal.valueOf(25_000));
        assertThat(estimate.high()).isEqualByComparingTo(BigDecimal.valueOf(75_000));
    }

    @Test
    void manyIntegrations_bumpsUpperBoundOnlyByConfiguredFactor() {
        when(pricingRuleService.getActiveRule(eq("full-stack")))
                .thenReturn(Optional.of(rule("full-stack", 150_000, 500_000L)));

        EstimateDto base = service.calculate(
                InquiryIntent.BUILD, "New web application", null, List.of(), "3 months");
        EstimateDto withIntegrations = service.calculate(
                InquiryIntent.BUILD, "New web application", null,
                List.of("Razorpay", "Google OAuth", "Cloudinary", "Sentry"), "3 months");

        assertThat(withIntegrations.low()).isEqualByComparingTo(base.low());
        assertThat(withIntegrations.high()).isGreaterThan(base.high());
    }

    @Test
    void modernizeWithExistingSystem_addsConfiguredComplexityBuffer() {
        when(pricingRuleService.getActiveRule(eq("full-stack")))
                .thenReturn(Optional.of(rule("full-stack", 150_000, 500_000L)));

        EstimateDto withoutExisting = service.calculate(
                InquiryIntent.MODERNIZE, "New web application", null, List.of(), "3 months");
        EstimateDto withExisting = service.calculate(
                InquiryIntent.MODERNIZE, "New web application", "10-year-old PHP monolith",
                List.of(), "3 months");

        assertThat(withExisting.high()).isGreaterThan(withoutExisting.high());
        assertThat(withExisting.low()).isEqualByComparingTo(withoutExisting.low());
    }

    @Test
    void urgentRequest_bumpsBothBoundsByConfiguredUrgencyFactor() {
        when(pricingRuleService.getActiveRule(eq("fix")))
                .thenReturn(Optional.of(rule("fix", 25_000, 150_000L)));

        EstimateDto normal = service.calculate(
                InquiryIntent.FIX, "Existing app — fixes or features", null, List.of(), "3 months", null, "Flexible");
        EstimateDto urgent = service.calculate(
                InquiryIntent.FIX, "Existing app — fixes or features", null, List.of(), "3 months", null, "ASAP");

        assertThat(urgent.high()).isGreaterThan(normal.high());
        assertThat(urgent.low()).isGreaterThan(normal.low());
    }

    @Test
    void largeScale_bumpsUpperBoundByConfiguredScaleFactor() {
        when(pricingRuleService.getActiveRule(eq("full-stack")))
                .thenReturn(Optional.of(rule("full-stack", 150_000, 500_000L)));

        EstimateDto normal = service.calculate(
                InquiryIntent.BUILD, "New web application", null, List.of(), "3 months", null, null);
        EstimateDto largeScale = service.calculate(
                InquiryIntent.BUILD, "New web application", null, List.of(), "3 months", "500,000 users", null);

        assertThat(largeScale.high()).isGreaterThan(normal.high());
    }

    @Test
    void everyRange_carriesTheBindingQuotationDisclaimer() {
        when(pricingRuleService.getActiveRule(eq("fix")))
                .thenReturn(Optional.of(rule("fix", 25_000, 150_000L)));

        EstimateDto estimate = service.calculate(
                InquiryIntent.FIX, "Existing app — fixes or features", null, List.of(), "ASAP");

        assertThat(estimate.disclaimer()).contains("not a binding quotation");
    }
}
