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
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.ReviewRepository;
import com.neelastack.repository.TestimonialRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

            emailService.sendTestimonialRequest(saved, invoice);

            auditLogService.recordBestEffort(AuditAction.TESTIMONIAL_REQUEST_QUEUED, "Invoice", invoice.getId().toString(),
                    Map.of("testimonialRequestId", saved.getId().toString(), "clientEmail", client.getEmail()));
        } catch (Exception ex) {
            // Deliberately swallowed (with logging): this is a growth/marketing side-effect
            // of a payment event, never allowed to compromise the payment transaction that
            // triggered it. See InvoiceService#verifyAndConfirmPayment / #markPaidFromWebhook.
            log.error("Failed to queue testimonial request for invoice {}: {}", invoice.getId(), ex.getMessage());
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
