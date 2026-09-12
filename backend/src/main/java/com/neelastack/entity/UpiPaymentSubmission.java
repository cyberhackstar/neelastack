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

// `invoice`, `upiMethod`, `submittedBy`, `verifiedBy` are all lazy @ManyToOne associations --
// excluded from @ToString for the same LazyInitializationException reasons documented on
// Invoice/Engagement in this package.
@Entity
@Table(name = "upi_payment_submissions")
@Getter
@Setter
@ToString(exclude = {"invoice", "upiMethod", "submittedBy", "verifiedBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiPaymentSubmission {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upi_method_id", nullable = false)
    private UpiPaymentMethod upiMethod;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    /** The UPI transaction/UTR reference number the client copied from their payment app --
     *  this is the only thing an admin can actually cross-check against a bank statement. */
    @Column(name = "utr_reference", nullable = false, length = 60)
    private String utrReference;

    @Column(name = "payer_upi_id", length = 100)
    private String payerUpiId;

    @Column(name = "amount_claimed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountClaimed;

    /** Optional screenshot of the payment confirmation, stored the same way as any other
     *  project file (Cloudinary, authenticated delivery) -- see FileStorageService. */
    @Column(name = "screenshot_url", length = 500)
    private String screenshotUrl;

    @Column(name = "screenshot_public_id", length = 200)
    private String screenshotPublicId;

    @Column(name = "screenshot_resource_type", length = 20)
    private String screenshotResourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UpiSubmissionStatus status = UpiSubmissionStatus.PENDING_VERIFICATION;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
