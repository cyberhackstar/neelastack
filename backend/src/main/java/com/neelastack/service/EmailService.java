package com.neelastack.service;

import com.neelastack.entity.Inquiry;
import com.neelastack.entity.LeadTier;
import com.neelastack.entity.Quotation;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Transactional email notifications.
 *
 * All user-facing mail is sent as responsive HTML with a simple Neelastack
 * visual system: dark header, blue accent, clean cards, and mobile-safe layout.
 *
 * Failures are logged, not thrown, for ordinary notifications. The testimonial
 * request intentionally propagates MailException so its outbox-style retry
 * logic can record the actual delivery failure.
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

    private static final String BRAND = "Neelastack";
    private static final String ACCENT = "#4f8cff";
    private static final String DARK = "#0d1117";
    private static final String TEXT = "#172033";
    private static final String MUTED = "#667085";
    private static final String BORDER = "#e6eaf0";
    private static final String PAGE_BG = "#f5f7fb";
    private static final String CARD_BG = "#ffffff";

    @Async
    public void sendInquiryConfirmation(Inquiry inquiry) {
        String title = "We've received your project inquiry";
        String content = """
                <p>Hi %s,</p>
                <p>Thanks for reaching out to Neelastack. Your inquiry has been received and is now with our team.</p>

                <div class="card">
                  <div class="label">PROJECT DETAILS</div>
                  <table class="details">
                    <tr><td>Project type</td><td>%s</td></tr>
                    <tr><td>Budget range</td><td>%s</td></tr>
                  </table>
                </div>

                <div class="card">
                  <div class="label">YOUR MESSAGE</div>
                  <p class="pre">%s</p>
                </div>

                <p>I'll review the details and get back to you within one business day with next steps.</p>
                """.formatted(
                esc(inquiry.getName()),
                esc(nullToDash(inquiry.getProjectType())),
                esc(nullToDash(inquiry.getBudgetRange())),
                esc(inquiry.getMessage())
        );

        sendHtml(inquiry.getEmail(), title, htmlEmail(title, content, "Thanks for getting in touch."));
    }

    @Async
    public void sendAdminNewInquiryAlert(Inquiry inquiry) {
        String subjectPrefix = inquiry.getLeadTier() == LeadTier.HOT
                ? "🔥 HOT lead: " : "New inquiry: ";

        String title = subjectPrefix + nullToDash(inquiry.getName());
        String content = """
                <p>A new inquiry has been received.</p>

                <div class="card">
                  <div class="label">LEAD SIGNAL</div>
                  <table class="details">
                    <tr><td>Tier</td><td><strong>%s</strong></td></tr>
                    <tr><td>Lead score</td><td>%d / 100</td></tr>
                    <tr><td>Intent</td><td>%s</td></tr>
                  </table>
                </div>

                <div class="card">
                  <div class="label">CONTACT</div>
                  <table class="details">
                    <tr><td>Name</td><td>%s</td></tr>
                    <tr><td>Email</td><td>%s</td></tr>
                    <tr><td>Phone</td><td>%s</td></tr>
                    <tr><td>Company</td><td>%s</td></tr>
                    <tr><td>Project type</td><td>%s</td></tr>
                    <tr><td>Budget range</td><td>%s</td></tr>
                  </table>
                </div>

                <div class="card">
                  <div class="label">MESSAGE</div>
                  <p class="pre">%s</p>
                </div>

                <p><a class="button" href="%s/admin/inquiries">Open admin dashboard</a></p>
                """.formatted(
                esc(inquiry.getLeadTier()),
                inquiry.getLeadScore(),
                esc(inquiry.getIntent()),
                esc(inquiry.getName()),
                esc(inquiry.getEmail()),
                esc(nullToDash(inquiry.getPhone())),
                esc(nullToDash(inquiry.getCompany())),
                esc(nullToDash(inquiry.getProjectType())),
                esc(nullToDash(inquiry.getBudgetRange())),
                esc(inquiry.getMessage()),
                escAttr(frontendUrl)
        );

        sendHtml(adminAddress, title, htmlEmail(title, content, "A new opportunity is ready for review."));
    }

    @Async
    public void sendQuotation(Quotation quotation) {
        StringBuilder items = new StringBuilder();
        BigDecimal total = BigDecimal.ZERO;

        for (var item : quotation.getLineItems()) {
            items.append("""
                    <tr>
                      <td>%s</td>
                      <td class="amount">%s %s</td>
                    </tr>
                    """.formatted(
                    esc(item.getDescription()),
                    esc(quotation.getCurrency()),
                    esc(item.getAmount())
            ));
            total = total.add(item.getAmount());
        }

        BigDecimal displayTotal = quotation.getTotalAmount() != null
                ? quotation.getTotalAmount()
                : total;

        String validity = quotation.getValidUntil() != null
                ? "Valid until " + quotation.getValidUntil()
                : "";

        String scope = quotation.getScopeSummary() != null
                ? quotation.getScopeSummary()
                : "";

        String title = "Your Neelastack quotation";
        String content = """
                <p>Hi %s,</p>
                <p>Thanks for the conversation. Here's the quotation for <strong>%s</strong>.</p>

                <div class="card">
                  <div class="label">QUOTATION</div>
                  <h2 class="quote-title">%s</h2>
                  <table class="items">
                    %s
                    <tr class="total">
                      <td>Total</td>
                      <td class="amount">%s %s</td>
                    </tr>
                  </table>
                  %s
                </div>

                %s

                <p><a class="button" href="%s/quote/%s">Review quotation</a></p>
                <p class="small">You can review, accept, or decline the quotation securely from this page.</p>
                """.formatted(
                esc(quotation.getInquiry().getName()),
                esc(quotation.getTitle()),
                esc(quotation.getTitle()),
                items,
                esc(quotation.getCurrency()),
                esc(displayTotal),
                validity.isBlank() ? "" : "<p class=\"small\">" + esc(validity) + "</p>",
                scope.isBlank() ? "" : "<div class=\"card subtle\"><div class=\"label\">SCOPE</div><p>" + esc(scope) + "</p></div>",
                escAttr(frontendUrl),
                escAttr(quotation.getPublicToken())
        );

        sendHtml(
                quotation.getInquiry().getEmail(),
                "Your Neelastack quotation — " + quotation.getTitle(),
                htmlEmail(title, content, "A clear scope, price, and next step.")
        );
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetUrl) {
        String title = "Reset your Neelastack password";
        String content = """
                <p>Hi %s,</p>
                <p>Someone requested a password reset for your Neelastack account.</p>
                <p>This secure link expires in <strong>30 minutes</strong>.</p>

                <p><a class="button" href="%s">Reset password</a></p>

                <div class="card subtle">
                  <p class="small">If the button doesn't work, copy this link into your browser:</p>
                  <p class="mono">%s</p>
                </div>

                <p class="small">If you didn't request this, you can safely ignore this email. Your password hasn't been changed.</p>
                """.formatted(esc(fullName), escAttr(resetUrl), esc(resetUrl));

        sendHtml(toEmail, title, htmlEmail(title, content, "Secure account recovery from Neelastack."));
    }

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String verifyUrl) {
        String title = "Confirm your email — Neelastack";
        String content = """
                <p>Hi %s,</p>
                <p>Please confirm your email address to finish setting up your Neelastack account.</p>

                <p><a class="button" href="%s">Confirm email address</a></p>

                <div class="card subtle">
                  <p class="small">This link expires in <strong>24 hours</strong>.</p>
                </div>

                <p class="small">If you didn't create this account, you can ignore this email.</p>
                """.formatted(esc(fullName), escAttr(verifyUrl));

        sendHtml(toEmail, title, htmlEmail(title, content, "One quick step to finish setting up your account."));
    }

    @Async
    public void sendQuotationResponseNotice(Quotation quotation, boolean accepted, String reason) {
        String action = accepted ? "accepted" : "declined";
        String title = "Quotation " + action;
        String reasonBlock = (!accepted && reason != null && !reason.isBlank())
                ? """
                  <div class="card subtle">
                    <div class="label">CLIENT RESPONSE</div>
                    <p class="pre">%s</p>
                  </div>
                  """.formatted(esc(reason))
                : "";

        String content = """
                <p><strong>%s</strong> has %s the quotation <strong>“%s”</strong>.</p>

                <div class="card">
                  <div class="label">QUOTATION</div>
                  <table class="details">
                    <tr><td>Title</td><td>%s</td></tr>
                    <tr><td>Amount</td><td>%s %s</td></tr>
                    <tr><td>Status</td><td><strong>%s</strong></td></tr>
                  </table>
                </div>

                %s

                <p><a class="button" href="%s/admin/quotations">Open admin dashboard</a></p>
                """.formatted(
                esc(quotation.getInquiry().getName()),
                action,
                esc(quotation.getTitle()),
                esc(quotation.getTitle()),
                esc(quotation.getCurrency()),
                esc(quotation.getTotalAmount()),
                esc(action.toUpperCase()),
                reasonBlock,
                escAttr(frontendUrl)
        );

        sendHtml(adminAddress, title, htmlEmail(title, content, "Quotation activity from the Neelastack client pipeline."));
    }

    @Async
    public void sendExecutiveReport(Inquiry inquiry, byte[] pdfBytes, String reportFileName) {
        String reportLabel = inquiry.getIntent() != null && inquiry.getIntent().name().equals("AUDIT")
                ? "architecture brief" : "project brief";

        String title = "Your Neelastack executive brief";
        String content = """
                <p>Hi %s,</p>
                <p>Thanks for the details. Attached is your executive <strong>%s</strong> as a PDF.</p>

                <div class="card">
                  <div class="label">WHAT'S INSIDE</div>
                  <ul>
                    <li>Your submitted requirements</li>
                    <li>A preliminary read on scope and complexity</li>
                    <li>Recommended next steps</li>
                  </ul>
                </div>

                <p>It's a starting point, not a final quotation. Reply to this email any time, or book a short call, and we'll take it from there.</p>
                """.formatted(esc(inquiry.getName()), esc(reportLabel));

        sendHtmlWithAttachment(
                inquiry.getEmail(),
                title,
                htmlEmail(title, content, "Your requested executive brief is attached."),
                reportFileName,
                pdfBytes
        );
    }

    @Async
    public void sendFollowUpDigest(List<com.neelastack.dto.analytics.FollowUpTaskDto> tasks) {
        if (tasks.isEmpty()) return;

        StringBuilder rows = new StringBuilder();
        for (var task : tasks) {
            String reasonLabel = task.reason()
                    == com.neelastack.dto.analytics.FollowUpTaskDto.FollowUpReason.UNVIEWED_REMINDER
                    ? "Never opened" : "Opened, no response";

            String amount = task.totalAmount() == null
                    ? "—" : "₹" + task.totalAmount();

            rows.append("""
                    <tr>
                      <td>%s<br><span class="small">%d day(s)</span></td>
                      <td>%s<br><span class="small">%s</span></td>
                      <td>%s</td>
                      <td class="amount">%s</td>
                    </tr>
                    """.formatted(
                    esc(reasonLabel),
                    task.daysSinceLastActivity(),
                    esc(nullToDash(task.clientName())),
                    esc(nullToDash(task.clientEmail())),
                    esc(nullToDash(task.quotationTitle())),
                    esc(amount)
            ));
        }

        String title = "Follow-up digest";
        String content = """
                <p>Here are the proposals that need attention today.</p>

                <div class="card">
                  <table class="items">
                    <thead>
                      <tr>
                        <th>Reason</th>
                        <th>Client</th>
                        <th>Quotation</th>
                        <th>Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                </div>

                <p><a class="button" href="%s/admin/inquiries">Open follow-ups</a></p>
                """.formatted(rows, escAttr(frontendUrl));

        sendHtml(
                adminAddress,
                "Follow-up digest: " + tasks.size() + " proposal(s) need attention",
                htmlEmail(title, content, "A concise view of proposals that need a nudge.")
        );
    }

    /**
     * Sent immediately after an invoice is confirmed PAID.
     * This method intentionally propagates MailException so TestimonialService
     * can persist the failure and retry it later.
     */
    public void sendTestimonialRequestOrThrow(
            com.neelastack.entity.TestimonialRequest request,
            com.neelastack.entity.Invoice invoice
    ) {
        String link = frontendUrl + "/testimonial/" + request.getToken();
        String title = "A quick favor about your Neelastack project";
        String content = """
                <p>Hi %s,</p>
                <p>Thanks again for working with Neelastack. Invoice <strong>%s</strong> is confirmed paid, and we'd really appreciate a couple of minutes of your time.</p>

                <div class="card">
                  <p>Would you be willing to leave a short review of the project?</p>
                  <p class="small">It genuinely helps other teams evaluating us and takes less than 2 minutes.</p>
                  <p><a class="button" href="%s">Leave a review</a></p>
                </div>

                <p class="small">If you'd rather not, no worries at all — just ignore this email.</p>
                """.formatted(
                esc(nullToDash(request.getClientName())),
                esc(nullToDash(invoice.getInvoiceNumber())),
                escAttr(link)
        );

        sendHtmlOrThrow(
                request.getClientEmail(),
                title,
                htmlEmail(title, content, "Your feedback helps Neelastack improve.")
        );
    }

    private void sendHtml(String to, String subject, String html) {
        try {
            sendMimeMessage(to, subject, html, null, null);
        } catch (Exception ex) {
            log.error(
                    "Failed to send email to {} — subject '{}': {}",
                    to, subject, ex.getMessage(), ex
            );
        }
    }

    private void sendHtmlWithAttachment(
            String to,
            String subject,
            String html,
            String attachmentName,
            byte[] attachmentBytes
    ) {
        try {
            sendMimeMessage(to, subject, html, attachmentName, attachmentBytes);
        } catch (Exception ex) {
            log.error(
                    "Failed to send email with attachment to {} — subject '{}': {}",
                    to, subject, ex.getMessage(), ex
            );
        }
    }

    private void sendHtmlOrThrow(String to, String subject, String html) {
        sendMimeMessage(to, subject, html, null, null);
    }

    private void sendMimeMessage(
            String to,
            String subject,
            String html,
            String attachmentName,
            byte[] attachmentBytes
    ) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    attachmentBytes != null
            );
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (attachmentBytes != null) {
                helper.addAttachment(
                        attachmentName,
                        new ByteArrayResource(attachmentBytes)
                );
            }

            mailSender.send(message);
        } catch (MailException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to construct outgoing email", ex);
        }
    }

    private String htmlEmail(String title, String content, String preheader) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <meta name="color-scheme" content="light">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:Arial,Helvetica,sans-serif;color:%s;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background:%s;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;">
                          <tr>
                            <td style="background:%s;border-radius:18px 18px 0 0;padding:22px 28px;">
                              <span style="font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.02em;">Neelastack</span>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:%s;border:1px solid %s;border-top:0;border-radius:0 0 18px 18px;padding:32px 28px;">
                              <div style="font-size:13px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:%s;margin-bottom:10px;">NEELASTACK</div>
                              <h1 style="margin:0 0 20px;font-size:28px;line-height:1.2;color:%s;letter-spacing:-.025em;">%s</h1>
                              <div class="email-content">%s</div>
                              <div style="height:1px;background:%s;margin:28px 0 18px;"></div>
                              <p style="margin:0;color:%s;font-size:13px;line-height:1.6;">
                                © Neelastack · Software, product and engineering
                              </p>
                              <p style="margin:6px 0 0;color:%s;font-size:12px;line-height:1.5;">
                                This is an automated email. Please reply to this message if you need help.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>

                  <style>
                    .email-content p { margin: 0 0 16px; font-size: 15px; line-height: 1.7; color: %s; }
                    .email-content .small { margin: 0 0 10px; font-size: 12px; line-height: 1.6; color: %s; }
                    .email-content .card { margin: 20px 0; padding: 20px; background: #fff; border: 1px solid %s; border-radius: 14px; }
                    .email-content .subtle { background: #f8faff; }
                    .email-content .label { margin-bottom: 10px; font-size: 11px; font-weight: 700; letter-spacing: .08em; color: %s; }
                    .email-content .details, .email-content .items { width: 100%%; border-collapse: collapse; }
                    .email-content .details td { padding: 9px 0; border-bottom: 1px solid #eef1f5; vertical-align: top; font-size: 14px; }
                    .email-content .details td:first-child { width: 35%%; color: %s; }
                    .email-content .details td:last-child { color: %s; font-weight: 600; }
                    .email-content .items th { padding: 8px 0; text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: %s; }
                    .email-content .items td { padding: 11px 0; border-top: 1px solid #eef1f5; font-size: 14px; color: %s; }
                    .email-content .items .total td { font-weight: 700; font-size: 16px; }
                    .email-content .amount { text-align: right; white-space: nowrap; }
                    .email-content .quote-title { margin: 0 0 8px; font-size: 20px; line-height: 1.3; color: %s; }
                    .email-content .pre { white-space: pre-wrap; word-break: break-word; }
                    .email-content .mono { padding: 10px; background: #f4f6f8; border-radius: 8px; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 11px; word-break: break-all; color: %s; }
                    .email-content .button { display: inline-block; padding: 12px 18px; background: %s; color: #fff !important; text-decoration: none; border-radius: 10px; font-size: 14px; font-weight: 700; }
                    .email-content ul { margin: 0 0 16px; padding-left: 20px; color: %s; font-size: 14px; line-height: 1.7; }
                    @media only screen and (max-width: 520px) {
                      .email-content .card { padding: 16px; }
                      .email-content .details td:first-child { width: 42%%; }
                      .email-content .button { display: block; text-align: center; }
                    }
                  </style>
                </body>
                </html>
                """.formatted(
                esc(title),
                PAGE_BG,
                TEXT,
                esc(preheader),
                PAGE_BG,
                DARK,
                CARD_BG,
                BORDER,
                ACCENT,
                TEXT,
                esc(title),
                content,
                BORDER,
                MUTED,
                MUTED,
                TEXT,
                MUTED,
                TEXT,
                MUTED,
                TEXT,
                ACCENT,
                TEXT
        );
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String esc(Object value) {
        if (value == null) return "—";
        return esc(String.valueOf(value));
    }

    private String esc(String value) {
        if (value == null) return "—";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escAttr(String value) {
        return esc(value);
    }
}
