package com.neelastack.service;

import com.neelastack.dto.testimonial.TestimonialRequestPublicDto;
import com.neelastack.dto.testimonial.TestimonialSubmissionRequest;
import com.neelastack.entity.AuditAction;
import com.neelastack.entity.Engagement;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.Project;
import com.neelastack.entity.Review;
import com.neelastack.entity.ReviewSource;
import com.neelastack.entity.TestimonialRequest;
import com.neelastack.entity.TestimonialRequestStatus;
import com.neelastack.entity.User;
import com.neelastack.exception.BadRequestException;
import com.neelastack.exception.ResourceNotFoundException;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.ReviewRepository;
import com.neelastack.repository.TestimonialRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module 4 of the Client Acquisition & High-Ticket Conversion Engine: the closed
 * loop from "invoice confirmed PAID" to "testimonial request sent" to "review
 * captured", feeding {@code SchemaBuilderService}'s Review/AggregateRating
 * structured data on the frontend once an admin publishes it.
 *
 * Two correctness properties matter here, both handled the same way the rest of
 * this codebase handles one-time/idempotent operations:
 *   - Queueing is idempotent per invoice (a unique index on invoice_id plus an
 *     existence check) so a retried webhook or a duplicate call to
 *     {@code InvoiceService}'s PAID-transition code can never send two requests.
 *   - Submission is a one-time atomic token consumption (conditional UPDATE, not
 *     read-then-write) so a replayed or resubmitted link can never create two
 *     reviews from one request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestimonialService {

    private final TestimonialRequestRepository testimonialRequestRepository;
    private final ReviewRepository reviewRepository;
    private final ProjectRepository projectRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    /**
     * Called (best-effort) from InvoiceService immediately after an invoice's
     * status actually transitions to PAID. Never allowed to throw back into the
     * payment flow — a failure here must never fail, roll back, or retry a
     * successful payment confirmation.
     */
    @Transactional
    public void queueRequestForInvoice(Invoice invoice) {
        try {
            if (testimonialRequestRepository.existsByInvoiceId(invoice.getId())) {
                return; // Already queued -- webhook + browser confirmation both landed, or a retry.
            }

            Engagement engagement = invoice.getEngagement();
            User client = engagement == null ? null : engagement.getClient();
            if (client == null || client.getEmail() == null || client.getEmail().isBlank()) {
                log.warn("Skipping testimonial request for invoice {} -- no client email on engagement", invoice.getId());
                return;
            }

            Project project = engagement.getProject();

            TestimonialRequest request = TestimonialRequest.builder()
                    .invoiceId(invoice.getId())
                    .engagementId(engagement.getId())
                    .projectId(project != null ? project.getId() : null)
                    .clientEmail(client.getEmail())
                    .clientName(client.getFullName())
                    .token(UUID.randomUUID().toString())
                    .status(TestimonialRequestStatus.PENDING)
                    .requestedAt(LocalDateTime.now())
                    .build();

            TestimonialRequest saved = testimonialRequestRepository.save(request);

            attemptSend(saved, invoice);

            auditLogService.recordBestEffort(AuditAction.TESTIMONIAL_REQUEST_QUEUED, "Invoice", invoice.getId().toString(),
                    Map.of("testimonialRequestId", saved.getId().toString(), "clientEmail", client.getEmail()));
        } catch (Exception ex) {
            // Deliberately swallowed (with logging): this is a growth/marketing side-effect
            // of a payment event, never allowed to compromise the payment transaction that
            // triggered it. See InvoiceService#verifyAndConfirmPayment / #markPaidFromWebhook.
            log.error("Failed to queue testimonial request for invoice {}: {}", invoice.getId(), ex.getMessage());
        }
    }

    /** Max send attempts (initial + retries) before a testimonial invite is given up on. */
    private static final int MAX_EMAIL_ATTEMPTS = 6;
    private static final int RETRY_BATCH_SIZE = 50;

    /**
     * One synchronous send attempt. Records the outcome on the row either way (sent timestamp
     * on success; attempt count, error, and next-retry backoff on failure) so the outcome is
     * never silently lost the way it previously was behind {@code @Async} + a swallowed
     * exception. Never throws -- both the initial call from {@link #queueRequestForInvoice}
     * and the scheduled retry worker need this to be a pure best-effort operation.
     */
    private void attemptSend(TestimonialRequest request, Invoice invoice) {
        try {
            emailService.sendTestimonialRequestOrThrow(request, invoice);
            request.setEmailSentAt(LocalDateTime.now());
            request.setLastEmailError(null);
        } catch (Exception ex) {
            request.setEmailAttempts(request.getEmailAttempts() + 1);
            request.setLastEmailError(truncate(ex.getMessage(), 500));
            request.setNextEmailAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes(request.getEmailAttempts())));
            log.warn("Testimonial invite email attempt {} failed for request {}: {}",
                    request.getEmailAttempts(), request.getId(), ex.getMessage());
        }
        testimonialRequestRepository.save(request);
    }

    /** 2, 4, 8, 16, 32, 64 minutes -- caps the last (6th) attempt's wait at a bit over an hour. */
    private long backoffMinutes(int attemptNumber) {
        return 1L << Math.min(attemptNumber, 6);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Outbox-style retry sweep for testimonial invite emails that failed their initial send
     * (transient SMTP outage, provider rate limit, etc.) — see V32 migration and
     * {@link #attemptSend}. Runs frequently and cheaply: each row not yet due for retry is
     * simply skipped by the repository query, and a row that has exhausted MAX_EMAIL_ATTEMPTS
     * is no longer returned as a candidate at all (it stays PENDING/un-sent, visible to an
     * admin via the testimonial-requests admin view, rather than silently retried forever).
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000) // every 5 minutes
    @Transactional
    public void retryFailedTestimonialEmails() {
        List<TestimonialRequest> candidates = testimonialRequestRepository.findRetryCandidates(
                MAX_EMAIL_ATTEMPTS, LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE));

        for (TestimonialRequest request : candidates) {
            Invoice invoice = invoiceRepository.findById(request.getInvoiceId()).orElse(null);
            if (invoice == null) {
                // Shouldn't happen (invoices aren't deleted), but don't let a missing parent
                // row spin this request's retry counter forever.
                log.warn("Testimonial request {} references missing invoice {} -- skipping retry",
                        request.getId(), request.getInvoiceId());
                continue;
            }
            attemptSend(request, invoice);
        }

        if (!candidates.isEmpty()) {
            log.info("Testimonial email retry sweep: attempted {} request(s)", candidates.size());
        }
    }

    @Transactional(readOnly = true)
    public TestimonialRequestPublicDto getByToken(String token) {
        TestimonialRequest request = testimonialRequestRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial request not found"));

        String projectTitle = request.getProjectId() == null
                ? null
                : projectRepository.findById(request.getProjectId()).map(Project::getTitle).orElse(null);

        return TestimonialRequestPublicDto.builder()
                .clientName(request.getClientName())
                .projectTitle(projectTitle)
                .status(request.getStatus())
                .build();
    }

    /**
     * Atomically creates the Review and consumes the token. If two requests race
     * on the same link, exactly one succeeds; the loser's Review row is rolled
     * back rather than left as an orphaned duplicate.
     */
    @Transactional
    public void submit(String token, TestimonialSubmissionRequest submission) {
        TestimonialRequest request = testimonialRequestRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Testimonial request not found"));

        if (request.getStatus() != TestimonialRequestStatus.PENDING) {
            throw new BadRequestException("This testimonial link has already been used.");
        }

        Review review = Review.builder()
                .projectId(request.getProjectId())
                .authorName(request.getClientName())
                .authorTitle(submission.authorTitle())
                .rating(submission.rating())
                .reviewBody(submission.reviewBody())
                .videoUrl(submission.videoUrl())
                .published(false) // Always moderated before publish -- see Review#published.
                .submittedVia(ReviewSource.CLIENT_TESTIMONIAL)
                .build();
        Review saved = reviewRepository.save(review);

        int updated = testimonialRequestRepository.consumeIfPending(
                token, TestimonialRequestStatus.SUBMITTED, LocalDateTime.now(), saved.getId());

        if (updated == 0) {
            // Lost the race to a concurrent submit on the same token -- don't leave a
            // second, unreachable Review row behind.
            reviewRepository.delete(saved);
            throw new BadRequestException("This testimonial link has already been used.");
        }

        auditLogService.recordBestEffort(AuditAction.TESTIMONIAL_SUBMITTED, "Review", saved.getId().toString(),
                Map.of("testimonialRequestId", request.getId().toString()));
    }
}
