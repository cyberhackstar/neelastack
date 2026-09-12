package com.neelastack.dto.health;

import lombok.Builder;

@Builder
public record ProjectOperationsSummaryDto(
        long atRiskProjects,
        long criticalProjects,
        long awaitingClientProjects,
        long overdueTasks,
        long overdueInvoices,
        long pendingMilestoneApprovals,
        long pendingUpiVerifications,
        long deadlinesThisWeek
) {}
