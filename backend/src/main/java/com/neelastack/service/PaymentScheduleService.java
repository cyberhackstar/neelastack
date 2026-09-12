package com.neelastack.service;

import com.neelastack.dto.paymentschedule.InstallmentRequest;
import com.neelastack.dto.paymentschedule.PaymentScheduleDto;
import com.neelastack.dto.paymentschedule.PaymentScheduleInstallmentDto;
import com.neelastack.dto.paymentschedule.PaymentScheduleRequest;
import com.neelastack.dto.payment.InvoiceRequest;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.InstallmentStatus;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.NotificationType;
import com.neelastack.entity.PaymentSchedule;
import com.neelastack.entity.PaymentScheduleInstallment;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.PaymentScheduleInstallmentRepository;
import com.neelastack.repository.PaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * P0 #3 (client-workspace review): a deposit + milestone-linked payment plan for an
 * engagement (e.g. 20% deposit / 30% design / 30% dev / 20% final), instead of only ad-hoc
 * invoices. Raising an installment reuses {@link InvoiceService#create} so every downstream
 * behavior an invoice already has (Razorpay checkout, the direct UPI path, PDF generation,
 * the client activity timeline) works unchanged for schedule-driven invoices too -- this
 * service only owns the plan and the linkage, never a second payment implementation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentScheduleService {

    private final PaymentScheduleRepository paymentScheduleRepository;
    private final PaymentScheduleInstallmentRepository installmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final EngagementService engagementService;
    private final InvoiceService invoiceService;
    private final NotificationService notificationService;

    @Transactional
    public PaymentScheduleDto create(PaymentScheduleRequest request) {
        Engagement engagement = engagementService.getEntityWithAccessCheck(request.engagementId());

        if (paymentScheduleRepository.findByEngagementId(engagement.getId()).isPresent()) {
            throw new BadRequestException("This project already has a payment schedule");
        }

        BigDecimal installmentsTotal = request.installments().stream()
                .map(InstallmentRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (installmentsTotal.compareTo(request.totalAmount()) > 0) {
            throw new BadRequestException("Installment amounts (" + installmentsTotal + ") exceed the total project value (" + request.totalAmount() + ")");
        }

        PaymentSchedule schedule = PaymentSchedule.builder()
                .engagement(engagement)
                .totalAmount(request.totalAmount())
                .currency(request.currency() != null ? request.currency() : "INR")
                .build();
        schedule = paymentScheduleRepository.save(schedule);

        int order = 0;
        for (InstallmentRequest ir : request.installments()) {
            installmentRepository.save(PaymentScheduleInstallment.builder()
                    .paymentSchedule(schedule)
                    .label(ir.label())
                    .amount(ir.amount())
                    .percentage(ir.percentage())
                    .dueDate(ir.dueDate())
                    .status(InstallmentStatus.PENDING)
                    .displayOrder(ir.displayOrder() != null ? ir.displayOrder() : order)
                    .build());
            order++;
        }

        return toDto(schedule);
    }

    @Transactional(readOnly = true)
    public PaymentScheduleDto getForEngagement(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        PaymentSchedule schedule = paymentScheduleRepository.findByEngagementId(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment schedule for this project yet"));
        return toDto(schedule);
    }

    /**
     * Client-facing read that distinguishes an expected empty state from an error while
     * preserving the same engagement ownership check as the normal getter.
     */
    @Transactional(readOnly = true)
    public PaymentScheduleDto getForEngagementOrNull(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return paymentScheduleRepository.findByEngagementId(engagementId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean existsForEngagement(UUID engagementId) {
        return paymentScheduleRepository.findByEngagementId(engagementId).isPresent();
    }

    /** Turns a planned installment into a real Invoice (visible/payable via Razorpay and the
     *  direct UPI path, same as any other invoice), and links the two together. */
    @Transactional
    public PaymentScheduleInstallmentDto raiseInvoice(UUID installmentId) {
        PaymentScheduleInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Installment not found: " + installmentId));

        if (installment.getStatus() != InstallmentStatus.PENDING) {
            throw new BadRequestException("This installment has already been invoiced");
        }

        Engagement engagement = installment.getPaymentSchedule().getEngagement();
        engagementService.getEntityWithAccessCheck(engagement.getId());

        var invoiceDto = invoiceService.create(new InvoiceRequest(
                engagement.getId(),
                installment.getLabel() + " — " + engagement.getTitle(),
                installment.getAmount(),
                installment.getPaymentSchedule().getCurrency(),
                installment.getDueDate()
        ));

        // getReferenceById gives Hibernate an uninitialized proxy it can resolve to the FK
        // column without an extra SELECT and, critically, without Hibernate mistaking it for
        // a transient entity the way a plain `new Invoice()` with just the id set would --
        // the invoice was just created in the call above, so it's not lazily missing anything
        // this association needs.
        installment.setInvoice(invoiceRepository.getReferenceById(invoiceDto.id()));
        installment.setStatus(InstallmentStatus.INVOICED);
        installmentRepository.save(installment);

        return toInstallmentDto(installment);
    }

    /**
     * Daily sweep: nudges a client a few days before a raised (but unpaid) installment falls
     * due. Deliberately only reminds installments that have actually been turned into a real
     * Invoice (INVOICED) -- a still-PENDING installment is just a plan, not something to chase
     * a client about yet.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendUpcomingDueReminders() {
        LocalDate today = LocalDate.now();
        LocalDate in3Days = today.plusDays(3);
        List<PaymentScheduleInstallment> dueSoon = installmentRepository.findDueBetween(today, in3Days);
        for (PaymentScheduleInstallment installment : dueSoon) {
            // The stored status stays INVOICED forever once raised (see deriveDisplayStatus) --
            // skip anything whose linked invoice has actually already been paid through any
            // path (Razorpay, UPI, reconciliation) so a settled installment doesn't keep
            // generating "payment due soon" reminders.
            if (installment.getInvoice() == null || installment.getInvoice().getStatus() == InvoiceStatus.PAID) {
                continue;
            }
            Engagement engagement = installment.getPaymentSchedule().getEngagement();
            notificationService.notifyBestEffort(engagement.getClient(), engagement,
                    NotificationType.PAYMENT_SCHEDULE_INSTALLMENT_DUE,
                    "Payment due soon — " + installment.getLabel(),
                    "The \"" + installment.getLabel() + "\" installment (" + installment.getPaymentSchedule().getCurrency()
                            + " " + installment.getAmount() + ") is due " + installment.getDueDate() + ".",
                    "/dashboard/" + engagement.getId());
        }
    }

    private PaymentScheduleDto toDto(PaymentSchedule s) {
        List<PaymentScheduleInstallment> installments =
                installmentRepository.findByPaymentScheduleIdOrderByDisplayOrderAsc(s.getId());

        // paidAmount is derived from each installment's *linked invoice* status, not a stored
        // installment field -- see deriveDisplayStatus(). Invoice.status is the single source
        // of truth for "did this actually get paid"; an installment never carries its own
        // duplicate copy of that fact for a webhook/reconciliation/UPI-verification path to
        // forget to update and drift out of sync.
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (PaymentScheduleInstallment i : installments) {
            if (deriveDisplayStatus(i) == InstallmentStatus.PAID) {
                paidAmount = paidAmount.add(i.getAmount());
            }
        }

        return PaymentScheduleDto.builder()
                .id(s.getId())
                .engagementId(s.getEngagement().getId())
                .totalAmount(s.getTotalAmount())
                .currency(s.getCurrency())
                .paidAmount(paidAmount)
                .outstandingAmount(s.getTotalAmount().subtract(paidAmount))
                .installments(installments.stream().map(this::toInstallmentDto).toList())
                .build();
    }

    private PaymentScheduleInstallmentDto toInstallmentDto(PaymentScheduleInstallment i) {
        return PaymentScheduleInstallmentDto.builder()
                .id(i.getId())
                .label(i.getLabel())
                .amount(i.getAmount())
                .percentage(i.getPercentage())
                .dueDate(i.getDueDate())
                .status(deriveDisplayStatus(i))
                .invoiceId(i.getInvoice() != null ? i.getInvoice().getId() : null)
                .invoiceNumber(i.getInvoice() != null ? i.getInvoice().getInvoiceNumber() : null)
                .displayOrder(i.getDisplayOrder())
                .build();
    }

    /**
     * The persisted {@link InstallmentStatus} only ever transitions PENDING -> INVOICED
     * (see {@link #raiseInvoice}) -- PAID and OVERDUE are display states computed here from
     * the linked invoice's real status and due date, never written back to the row. This is
     * what {@link InstallmentStatus#OVERDUE}'s javadoc refers to.
     */
    private InstallmentStatus deriveDisplayStatus(PaymentScheduleInstallment i) {
        if (i.getInvoice() == null) {
            return InstallmentStatus.PENDING;
        }
        if (i.getInvoice().getStatus() == InvoiceStatus.PAID) {
            return InstallmentStatus.PAID;
        }
        if (i.getDueDate() != null && i.getDueDate().isBefore(LocalDate.now())) {
            return InstallmentStatus.OVERDUE;
        }
        return InstallmentStatus.INVOICED;
    }
}
