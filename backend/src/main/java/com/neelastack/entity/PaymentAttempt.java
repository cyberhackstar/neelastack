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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per Razorpay order created for an invoice. Exists so concurrent checkout
 * attempts (two browser tabs, a double-click, a client retry) against the same invoice
 * are visible to each other at the DB level, instead of silently overwriting
 * {@code invoices.razorpay_order_id} -- see InvoiceService#createOrder for how this is used.
 */
// See BlogPost/Engagement for why @Data is avoided here: `invoice` is a lazy
// @ManyToOne, and @Data's generated toString()/equals()/hashCode() would touch it.
@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@ToString(exclude = "invoice")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAttempt {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 64)
    private String razorpayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentAttemptStatus status = PaymentAttemptStatus.CREATED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
