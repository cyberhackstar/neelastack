package com.neelastack.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.neelastack.entity.Inquiry;
import com.neelastack.entity.Project;
import com.neelastack.entity.Quotation;
import com.neelastack.repository.ProjectRepository;
import com.neelastack.repository.QuotationRepository;
import com.neelastack.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Branded, client-ready proposal PDF. Built on demand so the web proposal and attachment stay in sync. */
@Service
@RequiredArgsConstructor
public class QuotationPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final Color INK = new Color(18, 18, 20);
    private static final Color MUTED = new Color(105, 103, 97);
    private static final Color AMBER = new Color(156, 122, 27);
    private static final Color TEAL = new Color(63, 107, 100);
    private static final Color RULE = new Color(226, 223, 215);
    private static final Color SOFT = new Color(247, 245, 239);

    private final QuotationRepository quotationRepository;
    private final ProjectRepository projectRepository;

    @Value("${app.site.frontend-url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public byte[] generateById(java.util.UUID id) {
        Quotation q = quotationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found"));
        return generate(q);
    }

    @Transactional(readOnly = true)
    public byte[] generateByToken(String token) {
        Quotation q = quotationRepository.findByPublicToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Quotation not found"));
        if (q.getStatus().name().equals("DRAFT")) {
            throw new ResourceNotFoundException("Quotation not found");
        }
        return generate(q);
    }

    private byte[] generate(Quotation q) {
        try {
            Document document = new Document(PageSize.A4, 48, 48, 52, 58);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font brand = new Font(Font.HELVETICA, 21, Font.BOLD, INK);
            Font eyebrow = new Font(Font.HELVETICA, 8.5f, Font.BOLD, AMBER);
            Font h1 = new Font(Font.HELVETICA, 20, Font.BOLD, INK);
            Font h2 = new Font(Font.HELVETICA, 12.5f, Font.BOLD, INK);
            Font body = new Font(Font.HELVETICA, 10.5f, Font.NORMAL, INK);
            Font muted = new Font(Font.HELVETICA, 8.7f, Font.NORMAL, MUTED);
            Font small = new Font(Font.HELVETICA, 7.8f, Font.NORMAL, MUTED);
            Font amount = new Font(Font.HELVETICA, 11, Font.BOLD, INK);
            Font total = new Font(Font.HELVETICA, 18, Font.BOLD, TEAL);

            Paragraph brandP = new Paragraph("neelastack", brand);
            brandP.setSpacingAfter(2);
            document.add(brandP);
            Paragraph strap = new Paragraph("Software · Product · Engineering", muted);
            strap.setSpacingAfter(7);
            document.add(strap);
            document.add(new Chunk(new LineSeparator(0.7f, 100, RULE, Element.ALIGN_LEFT, -2)));

            Paragraph label = new Paragraph("PRIVATE PROPOSAL", eyebrow);
            label.setSpacingBefore(18);
            label.setSpacingAfter(7);
            document.add(label);

            Paragraph title = new Paragraph(q.getTitle(), h1);
            title.setSpacingAfter(6);
            document.add(title);

            Inquiry inquiry = q.getInquiry();
            String preparedFor = "Prepared for " + safe(inquiry.getName())
                    + (inquiry.getCompany() != null && !inquiry.getCompany().isBlank() ? " · " + inquiry.getCompany() : "");
            Paragraph meta = new Paragraph(preparedFor + " · " + LocalDate.now().format(DATE), muted);
            meta.setSpacingAfter(18);
            document.add(meta);

            addSection(document, "01  Executive summary", h2, q.getExecutiveSummary(), body);
            addSection(document, "02  Scope of engagement", h2, q.getScopeSummary(), body);
            addSection(document, "03  Deliverables", h2, q.getDeliverables(), body);
            addSection(document, "04  Delivery roadmap", h2, q.getTimeline(), body);

            Paragraph invest = new Paragraph("05  Investment", h2);
            invest.setSpacingBefore(15);
            invest.setSpacingAfter(8);
            document.add(invest);

            PdfPTable table = new PdfPTable(new float[]{4.8f, 1.6f});
            table.setWidthPercentage(100);
            table.setSpacingAfter(7);
            for (var item : q.getLineItems()) {
                addCell(table, safe(item.getDescription()), body, false);
                addCell(table, safe(q.getCurrency()) + " " + money(item.getAmount()), amount, true);
            }
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total project investment", body));
            totalLabel.setBorder(PdfPCell.NO_BORDER);
            totalLabel.setPaddingTop(10);
            totalLabel.setBackgroundColor(SOFT);
            PdfPCell totalValue = new PdfPCell(new Phrase(safe(q.getCurrency()) + " " + money(q.getTotalAmount()), total));
            totalValue.setBorder(PdfPCell.NO_BORDER);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValue.setPaddingTop(7);
            totalValue.setBackgroundColor(SOFT);
            table.addCell(totalLabel);
            table.addCell(totalValue);
            document.add(table);

            addSection(document, "06  Payment terms", h2, q.getPaymentTerms(), body);
            addSection(document, "07  Assumptions", h2, q.getAssumptions(), body);
            addSection(document, "08  Exclusions", h2, q.getExclusions(), body);
            addSection(document, "09  Next steps", h2, q.getNextSteps(), body);

            String category = q.getServiceCategory();
            if (category != null && !category.isBlank()) {
                List<Project> proof = projectRepository.findBestMatchesForCategory(category);
                if (!proof.isEmpty()) {
                    Paragraph p = new Paragraph("Selected relevant work: " + proof.get(0).getTitle(), muted);
                    p.setSpacingBefore(12);
                    document.add(p);
                }
            }

            if (q.getValidUntil() != null) {
                Paragraph validity = new Paragraph("Proposal validity · " + q.getValidUntil().format(DATE), small);
                validity.setSpacingBefore(14);
                document.add(validity);
            }

            document.add(new Chunk(new LineSeparator(0.5f, 100, RULE, Element.ALIGN_LEFT, -2)));
            Paragraph footer = new Paragraph("Next step: review the secure proposal page at " + frontendUrl + "/quote/[secure-link]  ·  Neelastack", small);
            footer.setSpacingBefore(10);
            document.add(footer);
            Paragraph note = new Paragraph("This proposal is confidential and intended for the named recipient. Scope, timing, and commercial terms are those stated in this document.", small);
            note.setSpacingBefore(4);
            document.add(note);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate quotation PDF", e);
        }
    }

    private void addSection(Document d, String title, Font h2, String text, Font body) throws DocumentException {
        if (text == null || text.isBlank()) return;
        Paragraph heading = new Paragraph(title, h2);
        heading.setSpacingBefore(13);
        heading.setSpacingAfter(5);
        d.add(heading);
        for (String block : text.replace("\r", "").split("\\n\\s*\\n")) {
            if (block.isBlank()) continue;
            if (block.trim().matches("^[•*-].*")) {
                for (String item : block.split("\\n")) {
                    if (!item.isBlank()) {
                        Paragraph bullet = new Paragraph("• " + item.replaceFirst("^[•*-]\\s*", ""), body);
                        bullet.setIndentationLeft(10);
                        bullet.setSpacingAfter(3);
                        d.add(bullet);
                    }
                }
            } else {
                Paragraph p = new Paragraph(block.trim(), body);
                p.setLeading(14);
                p.setSpacingAfter(5);
                d.add(p);
            }
        }
    }

    private void addCell(PdfPTable table, String text, Font font, boolean right) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setPadding(7);
        if (right) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private String money(java.math.BigDecimal value) { return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
}
