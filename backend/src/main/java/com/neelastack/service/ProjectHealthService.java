package com.neelastack.service;

import com.neelastack.dto.health.ProjectHealthDto;
import com.neelastack.dto.health.ProjectHealthStatus;
import com.neelastack.dto.health.ProjectOperationsSummaryDto;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.EngagementStatus;
import com.neelastack.entity.InstallmentStatus;
import com.neelastack.entity.InvoiceStatus;
import com.neelastack.entity.MilestoneStatus;
import com.neelastack.entity.ProjectTaskStatus;
import com.neelastack.entity.UpiSubmissionStatus;
import com.neelastack.repository.EngagementRepository;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.MilestoneRepository;
import com.neelastack.repository.PaymentScheduleInstallmentRepository;
import com.neelastack.repository.ProjectTaskRepository;
import com.neelastack.repository.UpiPaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P0 #5 (client-workspace review): turns "milestone progress" into a real delivery-health
 * signal — 🟢 Healthy / 🟡 At Risk / 🔴 Critical — computed live from overdue tasks,
 * overdue milestones, pending client approvals and overdue invoices, rather than a single
 * admin-set status field that can go stale. Thresholds are deliberately simple constants
 * rather than a scoring model: this is meant to be a quick, explainable signal an admin can
 * act on, not a black-box score.
 */
@Service
@RequiredArgsConstructor
public class ProjectHealthService {

    private final EngagementRepository engagementRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentScheduleInstallmentRepository installmentRepository;
    private final UpiPaymentSubmissionRepository upiPaymentSubmissionRepository;
    private final EngagementService engagementService;

    @Transactional(readOnly = true)
    public ProjectHealthDto computeForEngagement(UUID engagementId) {
        engagementService.getEntityWithAccessCheck(engagementId);
        return compute(engagementId);
    }

    private ProjectHealthDto compute(UUID engagementId) {
        LocalDate today = LocalDate.now();

        long overdueTasks = projectTaskRepository.countByMilestoneEngagementIdAndStatusNotAndDueDateBefore(
                engagementId, ProjectTaskStatus.DONE, today);
        long overdueMilestones = milestoneRepository.countByEngagementIdAndStatusNotAndDueDateBefore(
                engagementId, MilestoneStatus.DONE, today);
        long pendingApprovals = milestoneRepository.countByEngagementIdAndStatus(
                engagementId, MilestoneStatus.AWAITING_APPROVAL);
        long clientActionTasks = projectTaskRepository.countByMilestoneEngagementIdAndClientActionRequiredTrueAndStatusNot(
                engagementId, ProjectTaskStatus.DONE);
        long overdueInvoices = invoiceRepository.countByEngagementIdAndStatusAndDueDateBefore(
                engagementId, InvoiceStatus.PENDING, today);
        long overdueInstallments = installmentRepository.findByStatusAndDueDateBefore(InstallmentStatus.INVOICED, today)
                .stream()
                .filter(i -> i.getPaymentSchedule().getEngagement().getId().equals(engagementId))
                .filter(i -> i.getInvoice() == null || i.getInvoice().getStatus() != InvoiceStatus.PAID)
                .count();

        List<String> reasons = new ArrayList<>();
        if (overdueInvoices > 0) reasons.add(overdueInvoices + " overdue invoice" + plural(overdueInvoices));
        if (overdueInstallments > 0) reasons.add(overdueInstallments + " overdue payment installment" + plural(overdueInstallments));
        if (overdueMilestones > 0) reasons.add(overdueMilestones + " overdue milestone" + plural(overdueMilestones));
        if (overdueTasks > 0) reasons.add(overdueTasks + " overdue task" + plural(overdueTasks));
        if (pendingApprovals > 0) reasons.add(pendingApprovals + " milestone" + plural(pendingApprovals) + " awaiting your approval");
        if (clientActionTasks > 0) reasons.add(clientActionTasks + " task" + plural(clientActionTasks) + " needing client action");

        ProjectHealthStatus status;
        // CRITICAL: money or a hard deadline has already slipped.
        if (overdueInvoices > 0 || overdueInstallments > 0 || overdueMilestones > 0 || overdueTasks >= 3) {
            status = ProjectHealthStatus.CRITICAL;
        // AT_RISK: nothing has slipped yet, but the project is waiting on someone.
        } else if (overdueTasks > 0 || pendingApprovals > 0 || clientActionTasks > 0) {
            status = ProjectHealthStatus.AT_RISK;
        } else {
            status = ProjectHealthStatus.HEALTHY;
        }

        if (reasons.isEmpty()) {
            reasons.add("On track — no overdue items or pending approvals");
        }

        return ProjectHealthDto.builder()
                .engagementId(engagementId)
                .status(status)
                .reasons(reasons)
                .build();
    }

    /** Admin project-operations command center: a system-wide rollup rather than a per-project
     *  drill-down. Iterates active (non-completed, non-on-hold) engagements to tally health --
     *  a per-engagement scan is the simplest correct implementation at the scale a solo/small
     *  agency operates at, and avoids a second, divergent "global health" query to keep in
     *  sync with {@link #compute}. */
    @Transactional(readOnly = true)
    public ProjectOperationsSummaryDto summary() {
        List<Engagement> active = engagementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> e.getStatus() != EngagementStatus.COMPLETED)
                .toList();

        long atRisk = 0;
        long critical = 0;
        long awaitingClient = 0;
        for (Engagement e : active) {
            ProjectHealthDto health = compute(e.getId());
            if (health.status() == ProjectHealthStatus.AT_RISK) atRisk++;
            if (health.status() == ProjectHealthStatus.CRITICAL) critical++;
            if (health.status() != ProjectHealthStatus.HEALTHY) awaitingClient++;
        }

        LocalDate today = LocalDate.now();
        LocalDate weekOut = today.plusDays(7);

        return ProjectOperationsSummaryDto.builder()
                .atRiskProjects(atRisk)
                .criticalProjects(critical)
                .awaitingClientProjects(awaitingClient)
                .overdueTasks(projectTaskRepository.countByStatusNotAndDueDateBefore(ProjectTaskStatus.DONE, today))
                .overdueInvoices(invoiceRepository.countByStatusAndDueDateBefore(InvoiceStatus.PENDING, today))
                .pendingMilestoneApprovals(milestoneRepository.countByStatus(MilestoneStatus.AWAITING_APPROVAL))
                .pendingUpiVerifications(upiPaymentSubmissionRepository.countByStatus(UpiSubmissionStatus.PENDING_VERIFICATION))
                .deadlinesThisWeek(milestoneRepository.countByStatusNotAndDueDateBetween(MilestoneStatus.DONE, today, weekOut))
                .build();
    }

    private String plural(long count) {
        return count == 1 ? "" : "s";
    }
}
