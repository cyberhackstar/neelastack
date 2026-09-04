package com.neelastack.controller;

import com.neelastack.dto.pricing.PricingRuleDto;
import com.neelastack.dto.pricing.PricingRuleRequest;
import com.neelastack.service.PricingRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for {@code pricing_rules} — the "Configurable Pricing Model" piece of the
 * P0 pricing fix. This is the only place that changes what the public estimator
 * (/api/v1/public/estimator, via EstimateCalculatorService) quotes; there is no
 * pricing literal left in application code for these categories.
 */
@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@RequiredArgsConstructor
@Tag(name = "Admin pricing", description = "Dynamic pricing rules — requires ROLE_ADMIN")
public class AdminPricingController {

    private final PricingRuleService pricingRuleService;

    @GetMapping
    public List<PricingRuleDto> listAll() {
        return pricingRuleService.listAll();
    }

    @GetMapping("/{id}")
    public PricingRuleDto getById(@PathVariable UUID id) {
        return pricingRuleService.getById(id);
    }

    @PostMapping
    public ResponseEntity<PricingRuleDto> create(@Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingRuleService.create(request));
    }

    @PutMapping("/{id}")
    public PricingRuleDto update(@PathVariable UUID id, @Valid @RequestBody PricingRuleRequest request) {
        return pricingRuleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pricingRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
