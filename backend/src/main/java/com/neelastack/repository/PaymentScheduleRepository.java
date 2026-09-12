package com.neelastack.repository;

import com.neelastack.entity.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {
    Optional<PaymentSchedule> findByEngagementId(UUID engagementId);
    List<PaymentSchedule> findAll();
}
