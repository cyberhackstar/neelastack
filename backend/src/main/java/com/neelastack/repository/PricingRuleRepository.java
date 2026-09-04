package com.neelastack.repository;

import com.neelastack.entity.PricingRule;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    Optional<PricingRule> findFirstByServiceKeyAndActiveTrueOrderByVersionDesc(String serviceKey);

    List<PricingRule> findByServiceKeyOrderByVersionDesc(String serviceKey);

    default List<PricingRule> findAllOrdered() {
        return findAll(Sort.by("serviceKey").ascending().and(Sort.by("version").descending()));
    }
}
