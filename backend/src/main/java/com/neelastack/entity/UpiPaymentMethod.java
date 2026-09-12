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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An admin-configured "pay me directly" UPI destination -- a GPay/PhonePe/Paytm QR code
 * whose underlying VPA settles straight into Bhawesh's own bank account, with zero
 * payment-gateway commission. This is deliberately independent of the Razorpay flow
 * (Invoice.razorpayOrderId etc.): a client can pay a given invoice through either path,
 * and {@link UpiPaymentSubmission} is how a scan-and-pay claim gets reconciled back to an
 * invoice by an admin (there is no programmatic webhook for a P2P UPI transfer -- someone
 * has to eyeball the bank statement / UPI app and confirm the UTR).
 */
@Entity
@Table(name = "upi_payment_methods")
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiPaymentMethod {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    /** e.g. "GPay", "PhonePe", "Paytm" -- shown to the client as a tab/button label. */
    @Column(nullable = false, length = 60)
    private String label;

    /** The UPI ID itself (e.g. bhawesh@okhdfcbank), shown as text under the QR so a client
     *  can also pay by typing it into their own UPI app if scanning isn't convenient. */
    @Column(name = "vpa", length = 100)
    private String vpa;

    /** Payee display name to show alongside the VPA (defaults to the business name). */
    @Column(name = "payee_name", length = 120)
    private String payeeName;

    /** Cloudinary (free-tier) URL for the QR code image the admin uploaded. Regenerated at
     *  read time from qrImagePublicId (see UpiPaymentService#toMethodDto) rather than trusted
     *  as a permanent value — same convention as ProjectFileService for authenticated
     *  Cloudinary assets, since a signed delivery URL isn't meant to be persisted forever. */
    @Column(name = "qr_image_url", nullable = false, length = 500)
    private String qrImageUrl;

    @Column(name = "qr_image_public_id", length = 200)
    private String qrImagePublicId;

    @Column(name = "qr_image_resource_type", length = 20)
    private String qrImageResourceType;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
