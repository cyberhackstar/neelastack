package com.neelastack.repository;

import com.neelastack.entity.FollowUpDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowUpDismissalRepository extends JpaRepository<FollowUpDismissal, UUID> {
    Optional<FollowUpDismissal> findByQuotationId(UUID quotationId);

    List<FollowUpDismissal> findAll();

    void deleteByQuotationId(UUID quotationId);
}
