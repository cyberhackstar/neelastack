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
            // Flush immediately: without this, Hibernate's default flush ordering runs pending
            // INSERTs before pending UPDATEs in the same flush, which would attempt this new
            // active row's INSERT before the old row's "deactivate" UPDATE has actually run --
            // transiently violating the new partial unique index (V31) even though the end
            // state is correct. Flushing the deactivation first guarantees the old row is
            // already inactive by the time this method's INSERT executes.
            deactivateOtherVersions(request.serviceKey(), null);
            pricingRuleRepository.flush();
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
        PricingRule saved;
        try {
            saved = pricingRuleRepository.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Belt-and-braces: only reachable if another admin's transaction activated the same
            // service_key between this method's deactivation flush and this INSERT.
            throw new com.neelastack.exception.BadRequestException(
                    "Another active pricing rule for '" + request.serviceKey() + "' was just created — reload and try again.");
        }
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
            // See create()'s comment: flush the deactivation before this method's own UPDATE
            // (which may flip this same row active) so the new partial unique index (V31) never
            // transiently sees two active rows for the same service_key.
            deactivateOtherVersions(request.serviceKey(), id);
            pricingRuleRepository.flush();
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
        PricingRule saved;
        try {
            saved = pricingRuleRepository.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new com.neelastack.exception.BadRequestException(
                    "Another active pricing rule for '" + request.serviceKey() + "' was just created — reload and try again.");
        }
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
     * Only one active rule per service key is meaningful at a time. Application-layer check —
     * this is what gives a clean, immediate "which rows to deactivate" list; the DB-level
     * partial unique index (V31 migration) is what actually guarantees the invariant can't be
     * violated by a concurrent write slipping past this application logic.
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
