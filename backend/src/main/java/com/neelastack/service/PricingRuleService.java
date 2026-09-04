package com.neelastack.service;

import com.neelastack.dto.pricing.PricingRuleDto;
import com.neelastack.dto.pricing.PricingRuleRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.PricingRule;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin CRUD + the cached lookup {@link EstimateCalculatorService} uses on every
 * estimator submission. This is the "Configurable Pricing Model" piece of the P0
 * pricing fix — numbers live here (database), never as literals in calculator code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PricingRuleService {

    private final PricingRuleRepository pricingRuleRepository;
    private final AuditLogService auditLogService;

    /**
     * The one number {@link EstimateCalculatorService} actually needs. Cached because
     * it's read on every estimator submission; evicted on any admin write below.
     */
    @Cacheable("pricingRules")
    public Optional<PricingRuleDto> getActiveRule(String serviceKey) {
        return pricingRuleRepository.findFirstByServiceKeyAndActiveTrueOrderByVersionDesc(serviceKey)
                .map(this::toDto);
    }

    public List<PricingRuleDto> listAll() {
        return pricingRuleRepository.findAllOrdered().stream().map(this::toDto).toList();
    }

    public PricingRuleDto getById(UUID id) {
        return pricingRuleRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found: " + id));
    }

    @CacheEvict(value = "pricingRules", allEntries = true)
    @Transactional
    public PricingRuleDto create(PricingRuleRequest request) {
        int nextVersion = pricingRuleRepository.findByServiceKeyOrderByVersionDesc(request.serviceKey())
                .stream().findFirst().map(r -> r.getVersion() + 1).orElse(1);

        if (request.active()) {
            deactivateOtherVersions(request.serviceKey(), null);
        }

        PricingRule entity = PricingRule.builder()
                .serviceKey(request.serviceKey())
                .baseLow(request.baseLow())
                .baseHigh(request.baseHigh())
                .complexityFactor(request.complexityFactor())
                .scaleFactor(request.scaleFactor())
                .integrationFactor(request.integrationFactor())
                .urgencyFactor(request.urgencyFactor())
                .active(request.active())
                .version(nextVersion)
                .notes(request.notes())
                .build();

        log.info("Pricing rule created for '{}' (v{}, active={})", request.serviceKey(), nextVersion, request.active());
        PricingRule saved = pricingRuleRepository.save(entity);
        auditLogService.recordBestEffort(AuditAction.PRICING_RULE_UPDATED, "PricingRule", saved.getId().toString(),
                Map.of("op", "create", "serviceKey", saved.getServiceKey(), "version", String.valueOf(saved.getVersion()), "active", String.valueOf(saved.isActive())));
        return toDto(saved);
    }

    @CacheEvict(value = "pricingRules", allEntries = true)
    @Transactional
    public PricingRuleDto update(UUID id, PricingRuleRequest request) {
        PricingRule entity = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found: " + id));

        if (request.active()) {
            deactivateOtherVersions(request.serviceKey(), id);
        }

        entity.setServiceKey(request.serviceKey());
        entity.setBaseLow(request.baseLow());
        entity.setBaseHigh(request.baseHigh());
        entity.setComplexityFactor(request.complexityFactor());
        entity.setScaleFactor(request.scaleFactor());
        entity.setIntegrationFactor(request.integrationFactor());
        entity.setUrgencyFactor(request.urgencyFactor());
        entity.setActive(request.active());
        entity.setNotes(request.notes());

        log.info("Pricing rule {} updated for '{}' (active={})", id, request.serviceKey(), request.active());
        PricingRule saved = pricingRuleRepository.save(entity);
        auditLogService.recordBestEffort(AuditAction.PRICING_RULE_UPDATED, "PricingRule", saved.getId().toString(),
                Map.of("op", "update", "serviceKey", saved.getServiceKey(), "version", String.valueOf(saved.getVersion()), "active", String.valueOf(saved.isActive())));
        return toDto(saved);
    }

    @CacheEvict(value = "pricingRules", allEntries = true)
    @Transactional
    public void delete(UUID id) {
        if (!pricingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pricing rule not found: " + id);
        }
        pricingRuleRepository.deleteById(id);
        auditLogService.recordBestEffort(AuditAction.PRICING_RULE_UPDATED, "PricingRule", id.toString(), Map.of("op", "delete"));
    }

    /**
     * Only one active rule per service key is meaningful at a time (see the migration
     * comment) — enforced here rather than a DB constraint so drafts can be staged.
     */
    private void deactivateOtherVersions(String serviceKey, UUID exceptId) {
        pricingRuleRepository.findByServiceKeyOrderByVersionDesc(serviceKey).stream()
                .filter(r -> r.isActive() && !r.getId().equals(exceptId))
                .forEach(r -> {
                    r.setActive(false);
                    pricingRuleRepository.save(r);
                });
    }

    private PricingRuleDto toDto(PricingRule r) {
        return PricingRuleDto.builder()
                .id(r.getId())
                .serviceKey(r.getServiceKey())
                .baseLow(r.getBaseLow())
                .baseHigh(r.getBaseHigh())
                .complexityFactor(r.getComplexityFactor())
                .scaleFactor(r.getScaleFactor())
                .integrationFactor(r.getIntegrationFactor())
                .urgencyFactor(r.getUrgencyFactor())
                .active(r.isActive())
                .version(r.getVersion())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
