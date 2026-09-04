package com.neelastack.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.neelastack.entity.Invoice;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Renders a plain, professional invoice PDF using OpenPDF (LGPL/MPL —
 * permissively licensed, unlike iText's AGPL terms which would require
 * open-sourcing this whole codebase or buying a commercial license).
 */
@Service
public class PdfInvoiceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public byte[] generate(Invoice invoice) {
        try {
            Document document = new Document(PageSize.A4, 50, 50, 60, 60);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(13, 17, 23));
            Font headingFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(90, 90, 90));
            Font bodyFont = new Font(Font.HELVETICA, 11, Font.NORMAL, Color.BLACK);
            Font mutedFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(120, 120, 120));
            Font totalFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(13, 17, 23));

            // Header
            Paragraph brand = new Paragraph("neelastack", titleFont);
            document.add(brand);
            Paragraph tagline = new Paragraph("Engineering that ships.", mutedFont);
            tagline.setSpacingAfter(24);
            document.add(tagline);

            // Invoice meta table
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(24);

            addMetaCell(metaTable, "INVOICE NUMBER", invoice.getInvoiceNumber(), headingFont, bodyFont);
            addMetaCell(metaTable, "STATUS", invoice.getStatus().name(), headingFont, bodyFont);
            addMetaCell(metaTable, "BILLED TO", invoice.getEngagement().getClient().getFullName(), headingFont, bodyFont);
            addMetaCell(metaTable, "DATE ISSUED", invoice.getCreatedAt().format(DATE_FORMAT), headingFont, bodyFont);

            if (invoice.getDueDate() != null) {
                addMetaCell(metaTable, "DUE DATE", invoice.getDueDate().format(DATE_FORMAT), headingFont, bodyFont);
            }
            if (invoice.getPaidAt() != null) {
                addMetaCell(metaTable, "PAID ON", invoice.getPaidAt().format(DATE_FORMAT), headingFont, bodyFont);
            }

            document.add(metaTable);

            // Line item table (single line for now — one invoice = one description + amount)
            PdfPTable itemsTable = new PdfPTable(2);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{4, 1.2f});
            itemsTable.setSpacingBefore(10);

            PdfPCell descHeader = new PdfPCell(new Phrase("Description", headingFont));
            PdfPCell amountHeader = new PdfPCell(new Phrase("Amount", headingFont));
            styleHeaderCell(descHeader);
            styleHeaderCell(amountHeader);
            amountHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            itemsTable.addCell(descHeader);
            itemsTable.addCell(amountHeader);

            PdfPCell descCell = new PdfPCell(new Phrase(invoice.getDescription(), bodyFont));
            PdfPCell amountCell = new PdfPCell(new Phrase(invoice.getCurrency() + " " + invoice.getAmount(), bodyFont));
            styleBodyCell(descCell);
            styleBodyCell(amountCell);
            amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            itemsTable.addCell(descCell);
            itemsTable.addCell(amountCell);

            document.add(itemsTable);

            // Total
            Paragraph totalLine = new Paragraph(
                    "Total: " + invoice.getCurrency() + " " + invoice.getAmount(), totalFont
            );
            totalLine.setAlignment(Element.ALIGN_RIGHT);
            totalLine.setSpacingBefore(16);
            document.add(totalLine);

            // Footer
            Paragraph footer = new Paragraph(
                    "\n\nThank you for working with Neelastack. Questions about this invoice? " +
                    "Reply to the email it was sent with, or contact hello@neelastack.com.",
                    mutedFont
            );
            footer.setSpacingBefore(40);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
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

    private void styleHeaderCell(PdfPCell cell) {
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(200, 200, 200));
        cell.setPaddingBottom(8);
    }

    private void styleBodyCell(PdfPCell cell) {
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(230, 230, 230));
        cell.setPaddingTop(10);
        cell.setPaddingBottom(10);
    }
}
