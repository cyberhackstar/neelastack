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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_schedule_installments")
@Getter
@Setter
@ToString(exclude = {"paymentSchedule", "invoice"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentScheduleInstallment {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_schedule_id", nullable = false)
    private PaymentSchedule paymentSchedule;

    /** e.g. "Deposit", "Design", "Development", "Final". */
    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Informational only (what % of the total this represents) -- amount is the source of
     *  truth so rounding on odd totals never has to be reconciled against a stored percent. */
    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InstallmentStatus status = InstallmentStatus.PENDING;

    /** Set once an admin raises the actual Invoice for this installment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
