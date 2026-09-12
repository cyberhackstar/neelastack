package com.neelastack.service;

import com.neelastack.dto.upi.UpiPaymentMethodDto;
import com.neelastack.dto.upi.UpiPaymentMethodRequest;
import com.neelastack.dto.upi.UpiSubmissionDto;
import com.neelastack.dto.upi.UpiSubmissionRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.NotificationPriority;
import com.neelastack.entity.NotificationType;
import com.neelastack.entity.PaymentSource;
import com.neelastack.entity.UpiPaymentMethod;
import com.neelastack.entity.UpiPaymentSubmission;
import com.neelastack.entity.UpiSubmissionStatus;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.UpiPaymentMethodRepository;
import com.neelastack.repository.UpiPaymentSubmissionRepository;
import com.neelastack.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * P0 #1 (this session's request): direct UPI QR "pay me, not a gateway" flow.
 *
 * There is no programmatic webhook for a P2P UPI transfer the way there is for Razorpay --
 * the client scans an admin-uploaded QR (or pays to the shown VPA directly), then submits
 * the UTR/reference number they got from their own payment app as a claim. An admin then
 * manually cross-checks that reference against the actual bank statement/UPI app before
 * approving it, at which point (and only then) the linked invoice flips to PAID via
 * {@link InvoiceService#markPaidByAdmin}. This is deliberately a human-verified path, not an
 * automatic one -- there is nothing else that could prove the money actually landed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpiPaymentService {

    private final UpiPaymentMethodRepository upiPaymentMethodRepository;
    private final UpiPaymentSubmissionRepository upiPaymentSubmissionRepository;
    private final InvoiceRepository invoiceRepository;
    private final FileStorageService fileStorageService;
    private final InvoiceService invoiceService;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    // ---------------- Admin: manage QR methods ----------------

    @Transactional
    public UpiPaymentMethodDto createMethod(UpiPaymentMethodRequest request, MultipartFile qrImage) {
        if (qrImage == null || qrImage.isEmpty()) {
            throw new BadRequestException("A QR code image is required");
        }
        FileStorageService.UploadResult upload = fileStorageService.upload(qrImage, "neelastack/upi-qr");

        UpiPaymentMethod method = UpiPaymentMethod.builder()
                .label(request.label())
                .vpa(request.vpa())
                .payeeName(request.payeeName())
                .qrImageUrl(upload.url())
                .qrImagePublicId(upload.publicId())
                .qrImageResourceType(upload.resourceType())
                .active(true)
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .build();

        UpiPaymentMethodDto dto = toMethodDto(upiPaymentMethodRepository.save(method));
        auditLogService.recordBestEffort(AuditAction.PRICING_RULE_UPDATED, "UpiPaymentMethod", dto.id().toString(),
                Map.of("action", "created", "label", request.label()));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<UpiPaymentMethodDto> listAllForAdmin() {
        return upiPaymentMethodRepository.findAllByOrderByDisplayOrderAsc().stream().map(this::toMethodDto).toList();
    }

    @Transactional(readOnly = true)
    public List<UpiPaymentMethodDto> listActive() {
        return upiPaymentMethodRepository.findByActiveTrueOrderByDisplayOrderAsc().stream().map(this::toMethodDto).toList();
    }

    @Transactional
    public UpiPaymentMethodDto setActive(UUID methodId, boolean active) {
        UpiPaymentMethod method = upiPaymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI method not found: " + methodId));
        method.setActive(active);
        return toMethodDto(upiPaymentMethodRepository.save(method));
    }

    @Transactional
    public void deleteMethod(UUID methodId) {
        UpiPaymentMethod method = upiPaymentMethodRepository.findById(methodId)
                .orElseThrow(() -> new ResourceNotFoundException("UPI method not found: " + methodId));
        if (method.getQrImagePublicId() != null) {
            fileStorageService.delete(method.getQrImagePublicId(), "image");
        }
        upiPaymentMethodRepository.delete(method);
    }

    // ---------------- Client: submit a payment claim ----------------

    @Transactional
    public UpiSubmissionDto submit(UUID invoiceId, UpiSubmissionRequest request, MultipartFile screenshot) {
        Invoice invoice = getInvoiceWithAccessCheck(invoiceId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("This invoice has already been paid");
        }

        UpiPaymentMethod method = upiPaymentMethodRepository.findById(request.upiMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("UPI method not found: " + request.upiMethodId()));
        if (!method.isActive()) {
            throw new BadRequestException("That payment method is no longer active — please choose another");
        }

        User client = currentUserProvider.get();

        String screenshotUrl = null;
        String screenshotPublicId = null;
        String screenshotResourceType = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            FileStorageService.UploadResult upload = fileStorageService.upload(screenshot, "neelastack/upi-proofs");
            screenshotUrl = upload.url();
            screenshotPublicId = upload.publicId();
            screenshotResourceType = upload.resourceType();
        }

        UpiPaymentSubmission submission = UpiPaymentSubmission.builder()
                .invoice(invoice)
                .upiMethod(method)
                .submittedBy(client)
                .utrReference(request.utrReference().trim())
                .payerUpiId(request.payerUpiId())
                .amountClaimed(request.amountClaimed())
                .screenshotUrl(screenshotUrl)
                .screenshotPublicId(screenshotPublicId)
                .screenshotResourceType(screenshotResourceType)
                .status(UpiSubmissionStatus.PENDING_VERIFICATION)
                .build();
        UpiSubmissionDto dto;
        try {
            dto = toSubmissionDto(upiPaymentSubmissionRepository.save(submission));
        } catch (DataIntegrityViolationException e) {
            // Unique (invoice_id, utr_reference) — almost certainly a double-click/double-submit
            // of the same payment rather than a genuine second attempt.
            throw new BadRequestException("A payment claim with this reference number has already been submitted for this invoice");
        }

        notifyAdminsOfSubmission(invoice, submission);

        return dto;
    }

    @Transactional(readOnly = true)
    public List<UpiSubmissionDto> listForInvoice(UUID invoiceId) {
        getInvoiceWithAccessCheck(invoiceId);
        return upiPaymentSubmissionRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream().map(this::toSubmissionDto).toList();
    }

    // ---------------- Admin: verification queue ----------------

    @Transactional(readOnly = true)
    public List<UpiSubmissionDto> listPending() {
        return upiPaymentSubmissionRepository.findByStatusOrderByCreatedAtAsc(UpiSubmissionStatus.PENDING_VERIFICATION)
                .stream().map(this::toSubmissionDto).toList();
    }

    @Transactional
    public UpiSubmissionDto approve(UUID submissionId, String adminNote) {
        UpiPaymentSubmission submission = requirePending(submissionId);
        User admin = currentUserProvider.get();

        submission.setStatus(UpiSubmissionStatus.VERIFIED);
        submission.setAdminNote(adminNote);
        submission.setVerifiedBy(admin);
        submission.setVerifiedAt(LocalDateTime.now());
        upiPaymentSubmissionRepository.save(submission);

        // Reuses the same row-locked, idempotent "mark paid" path Razorpay's webhook uses --
        // see InvoiceService#markPaidByAdmin for why this can never double-process or regress
        // an invoice a webhook/reconciliation sweep already settled through another path.
        invoiceService.markPaidByAdmin(submission.getInvoice().getId(), PaymentSource.MANUAL_UPI);

        auditLogService.recordBestEffort(AuditAction.PAYMENT_MARKED_PAID, "UpiPaymentSubmission", submission.getId().toString(),
                Map.of("source", "MANUAL_UPI", "utr", submission.getUtrReference()));

        Invoice invoice = submission.getInvoice();
        notificationService.notifyBestEffort(
                submission.getSubmittedBy(), invoice.getEngagement(), NotificationType.UPI_PAYMENT_VERIFIED,
                "Payment verified — " + invoice.getInvoiceNumber(),
                "Your UPI payment for invoice " + invoice.getInvoiceNumber() + " has been verified and marked as paid.",
                "/dashboard/" + invoice.getEngagement().getId());

        return toSubmissionDto(submission);
    }

    @Transactional
    public UpiSubmissionDto reject(UUID submissionId, String adminNote) {
        UpiPaymentSubmission submission = requirePending(submissionId);
        User admin = currentUserProvider.get();

        submission.setStatus(UpiSubmissionStatus.REJECTED);
        submission.setAdminNote(adminNote);
        submission.setVerifiedBy(admin);
        submission.setVerifiedAt(LocalDateTime.now());
        upiPaymentSubmissionRepository.save(submission);

        Invoice invoice = submission.getInvoice();
        notificationService.notifyBestEffort(
                submission.getSubmittedBy(), invoice.getEngagement(), NotificationType.UPI_PAYMENT_REJECTED,
                "Payment claim needs another look — " + invoice.getInvoiceNumber(),
                "We couldn't verify your UPI payment claim for invoice " + invoice.getInvoiceNumber()
                        + (adminNote != null && !adminNote.isBlank() ? ": " + adminNote : ". Please double-check the reference number and resubmit, or contact us."),
                "/dashboard/" + invoice.getEngagement().getId());

        return toSubmissionDto(submission);
    }

    private UpiPaymentSubmission requirePending(UUID submissionId) {
        UpiPaymentSubmission submission = upiPaymentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        if (submission.getStatus() != UpiSubmissionStatus.PENDING_VERIFICATION) {
            throw new BadRequestException("This submission has already been reviewed");
        }
        return submission;
    }

    private void notifyAdminsOfSubmission(Invoice invoice, UpiPaymentSubmission submission) {
        notificationService.notifyAllAdminsBestEffort(invoice.getEngagement(), NotificationType.UPI_PAYMENT_SUBMITTED,
                NotificationPriority.HIGH,
                "UPI payment claim — " + invoice.getInvoiceNumber(),
                submission.getSubmittedBy().getFullName() + " claims to have paid " + invoice.getCurrency()
                        + " " + submission.getAmountClaimed() + " via " + submission.getUpiMethod().getLabel()
                        + " (UTR: " + submission.getUtrReference() + "). Please verify against your bank statement.",
                "/admin/upi-verification");
    }

    private Invoice getInvoiceWithAccessCheck(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        invoiceService.checkAccess(invoice);
        return invoice;
    }

    private UpiPaymentMethodDto toMethodDto(UpiPaymentMethod m) {
        String url = m.getQrImagePublicId() != null
                ? fileStorageService.generateSignedUrl(m.getQrImagePublicId(), m.getQrImageResourceType())
                : m.getQrImageUrl();
        return UpiPaymentMethodDto.builder()
                .id(m.getId())
                .label(m.getLabel())
                .vpa(m.getVpa())
                .payeeName(m.getPayeeName())
                .qrImageUrl(url)
                .active(m.isActive())
                .displayOrder(m.getDisplayOrder())
                .build();
    }

    private UpiSubmissionDto toSubmissionDto(UpiPaymentSubmission s) {
        String screenshotUrl = s.getScreenshotPublicId() != null
                ? fileStorageService.generateSignedUrl(s.getScreenshotPublicId(), s.getScreenshotResourceType())
                : s.getScreenshotUrl();
        return UpiSubmissionDto.builder()
                .id(s.getId())
                .invoiceId(s.getInvoice().getId())
                .invoiceNumber(s.getInvoice().getInvoiceNumber())
                .engagementId(s.getInvoice().getEngagement().getId())
                .methodLabel(s.getUpiMethod().getLabel())
                .submittedByName(s.getSubmittedBy().getFullName())
                .utrReference(s.getUtrReference())
                .payerUpiId(s.getPayerUpiId())
                .amountClaimed(s.getAmountClaimed())
                .screenshotUrl(screenshotUrl)
                .status(s.getStatus())
                .adminNote(s.getAdminNote())
                .createdAt(s.getCreatedAt())
                .verifiedAt(s.getVerifiedAt())
                .build();
    }
}
