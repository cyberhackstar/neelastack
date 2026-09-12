package com.neelastack.repository;

import com.neelastack.entity.InstallmentStatus;
import com.neelastack.entity.PaymentScheduleInstallment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentScheduleInstallmentRepository extends JpaRepository<PaymentScheduleInstallment, UUID> {
    List<PaymentScheduleInstallment> findByPaymentScheduleIdOrderByDisplayOrderAsc(UUID paymentScheduleId);

    List<PaymentScheduleInstallment> findByStatusAndDueDateBefore(InstallmentStatus status, LocalDate cutoff);

    @org.springframework.data.jpa.repository.Query(
            "SELECT i FROM PaymentScheduleInstallment i WHERE i.status = 'INVOICED' AND i.dueDate BETWEEN :from AND :to")
    List<PaymentScheduleInstallment> findDueBetween(java.time.LocalDate from, java.time.LocalDate to);

    long countByPaymentScheduleEngagementIdAndStatus(UUID engagementId, InstallmentStatus status);
}
