package com.neelastack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P0 #4 (client-workspace review): an enterprise engagement's total value split into a
 * deposit + milestone-linked installments, e.g. 20% deposit / 30% design / 30% dev / 20%
 * final. One engagement has at most one active schedule; each {@link PaymentScheduleInstallment}
 * turns into a real {@link Invoice} (via either Razorpay or the direct UPI path) once an
 * admin raises it.
 */
@Entity
@Table(name = "payment_schedules")
@Getter
@Setter
@ToString(exclude = "engagement")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSchedule {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engagement_id", nullable = false)
    private Engagement engagement;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String currency = "INR";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
