package com.neelastack.service;

import com.neelastack.dto.analytics.FollowUpTaskDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Automated lead follow-up system (master prompt section 2). Runs once a day and emails
 * the admin a digest of proposals that need a nudge — reusing AnalyticsService's
 * followUpTasks() so the scheduled digest and the on-demand
 * GET /api/v1/admin/analytics/follow-ups endpoint always agree on the same candidate set.
 *
 * Deliberately not a persisted "task" entity with its own completion-tracking workflow —
 * the candidate set is fully derivable at read time from quotation state (status,
 * viewCount, sentAt, lastViewedAt), so there's nothing to go stale or need reconciling.
 * Once a quotation is opened, responded to, or expires, it naturally drops out of the
 * next day's digest without any task-completion bookkeeping.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadFollowUpService {

    private final AnalyticsService analyticsService;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 8 * * *") // 8 AM daily — ahead of the working day, after the 1 AM expiry sweep
    public void sendDailyDigest() {
        List<FollowUpTaskDto> tasks = analyticsService.followUpTasks();
        if (tasks.isEmpty()) {
            log.debug("Follow-up digest: nothing due today");
            return;
        }
        log.info("Follow-up digest: {} proposal(s) need attention", tasks.size());
        emailService.sendFollowUpDigest(tasks);
    }
}
