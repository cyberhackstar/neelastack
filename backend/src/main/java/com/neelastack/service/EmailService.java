package com.neelastack.service;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.LeadTier;
import com.neelastack.entity.Quotation;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Transactional email notifications.
 * Failures are logged, not thrown — a broken SMTP config should never fail
 * the request that triggered the email (e.g. submitting an inquiry).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.admin-notification-address}")
    private String adminAddress;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    @Async
    public void sendInquiryConfirmation(Inquiry inquiry) {
        String body = """
                Hi %s,

                Thanks for reaching out to Neelastack — this confirms your inquiry has been received.

                Project type: %s
                Budget range: %s

                Your message:
                %s

                I'll review the details and get back to you within one business day with next steps.

                — Neelastack
                """.formatted(
                inquiry.getName(),
                nullToDash(inquiry.getProjectType()),
                nullToDash(inquiry.getBudgetRange()),
                inquiry.getMessage()
        );

        send(inquiry.getEmail(), "We've received your project inquiry", body);
    }

    @Async
    public void sendAdminNewInquiryAlert(Inquiry inquiry) {
        String body = """
                New inquiry received. [%s lead — score %d/100, intent %s]

                Name: %s
                Email: %s
                Phone: %s
                Company: %s
                Project type: %s
                Budget range: %s

                Message:
                %s
                """.formatted(
                inquiry.getLeadTier(),
                inquiry.getLeadScore(),
                inquiry.getIntent(),
                inquiry.getName(),
                inquiry.getEmail(),
                nullToDash(inquiry.getPhone()),
                nullToDash(inquiry.getCompany()),
                nullToDash(inquiry.getProjectType()),
                nullToDash(inquiry.getBudgetRange()),
                inquiry.getMessage()
        );

        // A HOT lead surfaces in the subject line so it doesn't sit in an inbox unopened —
        // this is the entire "notification" half of follow-up automation (section 50)
        // that's achievable without a scheduling/queue system.
        String subjectPrefix = inquiry.getLeadTier() == LeadTier.HOT
                ? "🔥 HOT lead: " : "New inquiry: ";
        send(adminAddress, subjectPrefix + inquiry.getName(), body);
    }

    @Async
    public void sendQuotation(Quotation quotation) {
        StringBuilder lineItemsText = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;

        for (var item : quotation.getLineItems()) {
            lineItemsText.append(" - %s: %s %s%n".formatted(item.getDescription(), quotation.getCurrency(), item.getAmount()));
            total = total.add(item.getAmount());
        }

        String body = """
                Hi %s,

                Here's the quotation for your project: %s

                %s

                Total: %s %s
                %s

                %s

                Review and accept or decline it here:
                %s/quote/%s

                — Neelastack
                """.formatted(
                quotation.getInquiry().getName(),
                quotation.getTitle(),
                lineItemsText.toString().stripTrailing(),
                quotation.getCurrency(),
                quotation.getTotalAmount(),
                quotation.getValidUntil() != null ? "Valid until: " + quotation.getValidUntil() : "",
                quotation.getScopeSummary() != null ? quotation.getScopeSummary() : "",
                frontendUrl,
                quotation.getPublicToken()
        );

        send(quotation.getInquiry().getEmail(), "Your Neelastack quotation — " + quotation.getTitle(), body);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetUrl) {
        String body = """
                Hi %s,

                Someone requested a password reset for your Neelastack account. If this was you,
                click the link below to set a new password. This link expires in 30 minutes.

                %s

                If you didn't request this, you can safely ignore this email — your password
                hasn't been changed.

                — Neelastack
                """.formatted(fullName, resetUrl);

        send(toEmail, "Reset your Neelastack password", body);
    }

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String verifyUrl) {
        String body = """
                Hi %s,

                Please confirm your email address to finish setting up your Neelastack account:

                %s

                This link expires in 24 hours.

                — Neelastack
                """.formatted(fullName, verifyUrl);

        send(toEmail, "Confirm your email — Neelastack", body);
    }

    @Async
    public void sendQuotationResponseNotice(Quotation quotation, boolean accepted, String reason) {
        String body = """
                %s has %s the quotation "%s" (%s %s).
                %s

                View it in the admin dashboard for details.

                — Neelastack
                """.formatted(
                quotation.getInquiry().getName(),
                accepted ? "ACCEPTED" : "declined",
                quotation.getTitle(),
                quotation.getCurrency(),
                quotation.getTotalAmount(),
                (!accepted && reason != null && !reason.isBlank()) ? "\nReason given: " + reason : ""
        );

        send(adminAddress, "Quotation " + (accepted ? "accepted" : "declined") + ": " + quotation.getInquiry().getName(), body);
    }

    /**
     * Sends the executive PDF report generated by {@link ExecutiveReportPdfService} for an
     * Estimator or Architecture Review submission — the actual deliverable of the "10 Lakhs
     * a month" lead-magnet pillar. Same fire-and-forget failure handling as everything else
     * here: a broken SMTP config must never surface as an error to the visitor who just
     * submitted a form, and it must never block the request thread.
     */
    @Async
    public void sendExecutiveReport(Inquiry inquiry, byte[] pdfBytes, String reportFileName) {
        String reportLabel = inquiry.getIntent() != null && inquiry.getIntent().name().equals("AUDIT")
                ? "architecture brief" : "project brief";

        String body = """
                Hi %s,

                Thanks for the details — attached is your executive %s as a PDF, covering what
                you submitted, a preliminary read on scope and complexity, and recommended next
                steps.

                It's a starting point, not a final quotation. Reply to this email any time, or
                book a short call, and we'll take it from there.

                — Neelastack
                """.formatted(inquiry.getName(), reportLabel);

        sendWithAttachment(inquiry.getEmail(), "Your Neelastack executive brief", body,
                reportFileName, pdfBytes);
    }

    /**
     * Daily digest of proposals needing a nudge (master prompt section 2 — "automated
     * lead follow-up system"). Sent once/day by LeadFollowUpService; skipped entirely
     * (no email) when the candidate list is empty, so an empty pipeline doesn't spam an
     * empty digest every morning.
     */
    @Async
    public void sendFollowUpDigest(java.util.List<com.neelastack.dto.analytics.FollowUpTaskDto> tasks) {
        if (tasks.isEmpty()) return;

        StringBuilder body = new StringBuilder(
                "Proposals that need a follow-up today:\n\n");
        for (com.neelastack.dto.analytics.FollowUpTaskDto t : tasks) {
            String reasonLabel = t.reason() == com.neelastack.dto.analytics.FollowUpTaskDto.FollowUpReason.UNVIEWED_REMINDER
                    ? "Never opened" : "Opened, no response";
            body.append("- [%s, %d day(s)] %s — %s (%s) — %s%s\n".formatted(
                    reasonLabel,
                    t.daysSinceLastActivity(),
                    nullToDash(t.clientName()),
                    nullToDash(t.clientEmail()),
                    nullToDash(t.quotationTitle()),
                    t.totalAmount() == null ? "" : "₹" + t.totalAmount(),
                    ""
            ));
        }
        body.append("\nOpen /admin/inquiries to follow up, or GET /api/v1/admin/analytics/follow-ups for the same list.\n");

        send(adminAddress, "Follow-up digest: " + tasks.size() + " proposal(s) need attention", body.toString());
    }

    /**
     * Sent immediately after an invoice is confirmed PAID (module 4 of the Client
     * Acquisition & High-Ticket Conversion Engine). One-time link — the token is
     * consumed atomically by TestimonialService#submit on first successful use.
     */
    @Async
    public void sendTestimonialRequest(com.neelastack.entity.TestimonialRequest request, com.neelastack.entity.Invoice invoice) {
        String link = frontendUrl + "/testimonial/" + request.getToken();

        String body = """
                Hi %s,

                Thanks again for working with Neelastack — invoice %s is confirmed paid, and
                we'd really appreciate a couple of minutes of your time.

                Would you be willing to leave a short review of the project? It genuinely
                helps other teams evaluating us, and takes less than 2 minutes:

                %s

                If you'd rather not, no worries at all — just ignore this email.

                — Neelastack
                """.formatted(
                nullToDash(request.getClientName()),
                nullToDash(invoice.getInvoiceNumber()),
                link
        );

        send(request.getClientEmail(), "Quick favor — how was your project with Neelastack?", body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send email to {} — subject '{}': {}", to, subject, ex.getMessage());
        }
    }

    private void sendWithAttachment(String to, String subject, String body,
                                     String attachmentName, byte[] attachmentBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true is required for an attachment part to be added at all.
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachmentBytes));
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send email with attachment to {} — subject '{}': {}", to, subject, ex.getMessage());
        }
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
