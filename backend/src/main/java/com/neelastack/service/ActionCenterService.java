package com.neelastack.service;

import com.neelastack.dto.actioncenter.ActionItemDto;
import com.neelastack.entity.InstallmentStatus;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.Milestone;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.entity.PaymentScheduleInstallment;
import com.neelastack.entity.ProjectTask;
import com.neelastack.entity.ProjectTaskStatus;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.repository.PaymentScheduleInstallmentRepository;
import com.neelastack.repository.ProjectTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P0 #6 (client-workspace review): the client "Action Required" center — a single flat list
 * of everything this project's client needs to personally act on, aggregated from milestones,
 * tasks, invoices and payment-schedule installments. Deliberately computed fresh on every
 * call rather than persisted, so it can never drift from the underlying records.
 */
@Service
@RequiredArgsConstructor
public class ActionCenterService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentScheduleInstallmentRepository installmentRepository;
    private final EngagementService engagementService;

    @Transactional(readOnly = true)
    public List<ActionItemDto> forEngagement(UUID engagementId) {
        var engagement = engagementService.getEntityWithAccessCheck(engagementId);
        String base = "/dashboard/" + engagementId;
        List<ActionItemDto> items = new ArrayList<>();

        for (Milestone m : milestoneRepository.findByEngagementIdOrderByDisplayOrderAsc(engagementId)) {
            if (m.getStatus() == MilestoneStatus.AWAITING_APPROVAL) {
                items.add(ActionItemDto.builder()
                        .kind("MILESTONE_APPROVAL")
                        .relatedId(m.getId())
                        .title("Approve \"" + m.getTitle() + "\"")
                        .description("This milestone is ready for your review and approval.")
                        .dueDate(m.getDueDate())
                        .deepLink(base + "?tab=milestones")
                        .build());
            }
        }

        for (ProjectTask t : projectTaskRepository.findByEngagementId(engagementId)) {
            if (t.isClientActionRequired() && t.getStatus() != ProjectTaskStatus.DONE) {
                items.add(ActionItemDto.builder()
                        .kind("TASK_ACTION")
                        .relatedId(t.getId())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .dueDate(t.getDueDate())
                        .deepLink(base + "?tab=tasks")
                        .build());
            }
        }

        LocalDate today = LocalDate.now();
        for (var invoice : invoiceRepository.findByEngagementIdOrderByCreatedAtDesc(engagementId)) {
            if (invoice.getStatus() == InvoiceStatus.PENDING) {
                boolean overdue = invoice.getDueDate() != null && invoice.getDueDate().isBefore(today);
                items.add(ActionItemDto.builder()
                        .kind(overdue ? "INVOICE_OVERDUE" : "INVOICE_DUE")
                        .relatedId(invoice.getId())
                        .title((overdue ? "Overdue: " : "Pay ") + "invoice " + invoice.getInvoiceNumber())
                        .description(invoice.getCurrency() + " " + invoice.getAmount() + " — " + invoice.getDescription())
                        .dueDate(invoice.getDueDate())
                        .deepLink(base + "?tab=invoices")
                        .build());
            }
        }

        // Payment-schedule installments are deliberately not listed separately here: once
        // raised, an installment *is* an Invoice (see PaymentScheduleService#raiseInvoice),
        // so it already appears in the invoice loop above — listing it twice under two
        // different "kind" values would just duplicate the same action for the client.

        return items;
    }
}
