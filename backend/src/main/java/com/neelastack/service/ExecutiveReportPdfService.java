package com.neelastack.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.InquiryIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates the branded "Executive Brief" PDF sent after an Estimator or Architecture
 * Review submission — the lead magnet's actual deliverable (master prompt sections
 * 21/22: "10 Lakhs/Month" conversion pillar).
 *
 * Deliberately honest about what this is: a preliminary, rule-based read of what the
 * visitor typed into a form, not a completed audit. No fabricated savings percentages,
 * no invented "risk score" out of 100 — this codebase's existing structured-data
 * services (see {@code SchemaBuilderService}) already establish the house rule that
 * unverifiable numbers don't get manufactured to look more authoritative, and the same
 * rule applies here: qualitative signals only, every one traceable to an actual answer
 * the visitor gave, with the estimate range carrying the same non-binding disclaimer
 * used everywhere else in the app ({@code EstimateDto}).
 */
@Service
public class ExecutiveReportPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private static final Color INK = new Color(13, 17, 23);
    private static final Color MUTED = new Color(120, 120, 120);
    private static final Color ACCENT = new Color(180, 130, 40); // amber, matches site accent
    private static final Color RULE = new Color(225, 225, 225);

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    public byte[] generate(Inquiry inquiry) {
        try {
            Document document = new Document(PageSize.A4, 50, 50, 60, 60);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font brandFont = new Font(Font.HELVETICA, 20, Font.BOLD, INK);
            Font taglineFont = new Font(Font.HELVETICA, 9, Font.NORMAL, MUTED);
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, INK);
            Font metaLabelFont = new Font(Font.HELVETICA, 8, Font.BOLD, MUTED);
            Font metaValueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, INK);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD, ACCENT);
            Font bodyFont = new Font(Font.HELVETICA, 10.5f, Font.NORMAL, INK);
            Font bulletFont = new Font(Font.HELVETICA, 10.5f, Font.NORMAL, INK);
            Font mutedSmall = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, MUTED);
            Font estimateFont = new Font(Font.HELVETICA, 20, Font.BOLD, INK);

            // --- Header ---
            document.add(new Paragraph("neelastack", brandFont));
            Paragraph tagline = new Paragraph("Engineering that ships.", taglineFont);
            tagline.setSpacingAfter(4);
            document.add(tagline);

            LineSeparator rule = new LineSeparator(0.5f, 100, RULE, Element.ALIGN_LEFT, -2);
            document.add(new Chunk(rule));

            Paragraph title = new Paragraph(reportTitle(inquiry), titleFont);
            title.setSpacingBefore(18);
            title.setSpacingAfter(4);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                    "Prepared for " + inquiry.getName()
                            + (inquiry.getCompany() != null && !inquiry.getCompany().isBlank()
                                    ? " · " + inquiry.getCompany() : "")
                            + "  ·  " + inquiry.getCreatedAt().format(DATE_FORMAT),
                    mutedSmall);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // --- Submission summary ---
            document.add(sectionHeading("Submission summary", sectionFont));
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingBefore(8);
            metaTable.setSpacingAfter(18);

            addMetaCell(metaTable, "PROJECT TYPE", dash(inquiry.getProjectType()), metaLabelFont, metaValueFont);
            addMetaCell(metaTable, "TIMELINE", dash(inquiry.getTimeline()), metaLabelFont, metaValueFont);
            addMetaCell(metaTable, "EXISTING SYSTEM", inquiry.getExistingSystem() != null && !inquiry.getExistingSystem().isBlank() ? "Yes — modernization/extension" : "None — greenfield build", metaLabelFont, metaValueFont);
            addMetaCell(metaTable, "SCALE", dash(inquiry.getUsersScale()), metaLabelFont, metaValueFont);
            document.add(metaTable);

            // --- Preliminary investment range ---
            if (inquiry.getEstimateLow() != null && inquiry.getEstimateHigh() != null) {
                document.add(sectionHeading("Preliminary investment range", sectionFont));
                Paragraph range = new Paragraph(
                        inquiry.getEstimateCurrency() + " " + format(inquiry.getEstimateLow())
                                + " – " + inquiry.getEstimateCurrency() + " " + format(inquiry.getEstimateHigh()),
                        estimateFont);
                range.setSpacingBefore(8);
                document.add(range);
                Paragraph disclaimer = new Paragraph(
                        "Non-binding, based on the scope described in your submission. A written, itemized "
                                + "quotation follows a short scoping call and is the number you'd actually approve.",
                        mutedSmall);
                disclaimer.setSpacingBefore(4);
                disclaimer.setSpacingAfter(18);
                document.add(disclaimer);
            }

            // --- Complexity & risk signals (qualitative, traceable to real inputs only) ---
            List<String> signals = buildSignals(inquiry);
            if (!signals.isEmpty()) {
                document.add(sectionHeading("What this tells us so far", sectionFont));
                Paragraph intro = new Paragraph(
                        "Preliminary signals from your submission — not a completed audit. A full architecture "
                                + "review validates or revises every one of these against the actual codebase.",
                        mutedSmall);
                intro.setSpacingBefore(6);
                intro.setSpacingAfter(10);
                document.add(intro);
                document.add(bulletList(signals, bulletFont));
            }

            // --- Recommended next steps ---
            document.add(sectionHeading("Recommended next steps", sectionFont));
            List<String> steps = buildNextSteps(inquiry);
            Paragraph stepsPara = bulletList(steps, bulletFont);
            stepsPara.setSpacingBefore(8);
            document.add(stepsPara);

            // --- CTA / footer ---
            Paragraph ctaHeading = new Paragraph("\nReady for the next step?", sectionFont);
            ctaHeading.setSpacingBefore(20);
            document.add(ctaHeading);

            Paragraph cta = new Paragraph(
                    "Reply directly to the email this report was attached to, or book a short scoping "
                            + "call: " + frontendUrl + "/contact\n\n"
                            + "This brief is preliminary and generated from the information you submitted. "
                            + "It is not a binding quotation, audit report, or SLA.",
                    mutedSmall);
            cta.setSpacingBefore(6);
            document.add(cta);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate executive report PDF", e);
        }
    }

    private String reportTitle(Inquiry inquiry) {
        return inquiry.getIntent() == InquiryIntent.AUDIT
                ? "Executive Architecture Brief"
                : "Executive Project Brief";
    }

    private List<String> buildSignals(Inquiry inquiry) {
        List<String> signals = new ArrayList<>();

        if (inquiry.getExistingSystem() != null && !inquiry.getExistingSystem().isBlank()) {
            signals.add("An existing system is in place — the work involves modernization or extension, "
                    + "which typically needs a discovery pass before implementation starts.");
        } else {
            signals.add("Greenfield build — no legacy system to work around, which generally shortens the "
                    + "path from scoping to a first shippable version.");
        }

        int integrationCount = inquiry.getIntegrations() == null || inquiry.getIntegrations().isBlank()
                ? 0 : inquiry.getIntegrations().split(",").length;
        if (integrationCount >= 2) {
            signals.add(integrationCount + " third-party integrations noted — each one adds its own testing "
                    + "surface and failure modes, and is worth scoping individually rather than as a single line item.");
        } else if (integrationCount == 1) {
            signals.add("One third-party integration noted — straightforward to scope alongside the core build.");
        }

        String urgency = inquiry.getUrgency() == null ? "" : inquiry.getUrgency().toLowerCase(Locale.ROOT);
        String timeline = inquiry.getTimeline() == null ? "" : inquiry.getTimeline().toLowerCase(Locale.ROOT);
        if (urgency.contains("urgent") || urgency.contains("critical") || urgency.contains("production down")) {
            signals.add("Flagged as urgent — worth a same-week call rather than waiting on the standard reply window.");
        } else if (timeline.isBlank() || timeline.contains("not sure")) {
            signals.add("No firm timeline yet — the scoping call is a good place to pressure-test a realistic one "
                    + "against the stated scope.");
        }

        return signals;
    }

    private List<String> buildNextSteps(Inquiry inquiry) {
        List<String> steps = new ArrayList<>();
        if (inquiry.getIntent() == InquiryIntent.AUDIT) {
            steps.add("A short call to walk through the current codebase and confirm access for a deeper review.");
            steps.add("A written review covering architecture, scaling headroom, and security posture, with concrete, prioritized fixes.");
            steps.add("A fixed-scope proposal for any remediation or modernization work that comes out of it — no obligation to proceed.");
        } else {
            steps.add("A short scoping call to confirm requirements against what you submitted here.");
            steps.add("A written, itemized quotation with a fixed price and fixed scope — the number you approve is the number you pay.");
            steps.add("A milestone-based delivery plan so progress is visible from the first week, not just at handoff.");
        }
        return steps;
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(14);
        return p;
    }

    private Paragraph bulletList(List<String> items, Font font) {
        Paragraph p = new Paragraph();
        for (String item : items) {
            p.add(new Chunk("•  ", font));
            p.add(new Phrase(item, font));
            p.add(Chunk.NEWLINE);
            p.add(Chunk.NEWLINE);
        }
        return p;
    }

    private void addMetaCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(10);
        Paragraph p = new Paragraph();
        p.add(new Phrase(label + "\n", labelFont));
        p.add(new Phrase(value, valueFont));
        cell.addElement(p);
        table.addCell(cell);
    }

    private String dash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    private String format(java.math.BigDecimal value) {
        return String.format(Locale.US, "%,.0f", value);
    }
}
