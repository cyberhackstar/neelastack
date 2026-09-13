package com.neelastack.service;

import com.neelastack.dto.payment.PaymentHistoryItemDto;
import com.neelastack.entity.Invoice;
import com.neelastack.entity.PaymentSource;
import com.neelastack.entity.UpiPaymentSubmission;
import com.neelastack.entity.UpiSubmissionStatus;
import com.neelastack.repository.InvoiceRepository;
import com.neelastack.repository.UpiPaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

    private static final DateTimeFormatter EXCEL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InvoiceRepository invoiceRepository;
    private final UpiPaymentSubmissionRepository upiPaymentSubmissionRepository;

    @Transactional(readOnly = true)
    public List<PaymentHistoryItemDto> list() {
        List<Invoice> invoices = invoiceRepository.findPaidWithEngagementAndClient();

        Map<UUID, UpiPaymentSubmission> verifiedUpiByInvoice = new HashMap<>();
        for (UpiPaymentSubmission submission : upiPaymentSubmissionRepository
                .findByStatusWithInvoiceAndMethodOrderByCreatedAtAsc(UpiSubmissionStatus.VERIFIED)) {
            // Keep the most recently verified claim for a paid invoice. In normal operation
            // there can be only one verified claim because an invoice becomes PAID after approval.
            verifiedUpiByInvoice.put(submission.getInvoice().getId(), submission);
        }

        return invoices.stream().map(invoice -> {
            PaymentSource source = invoice.getPaymentSource();
            UpiPaymentSubmission upi = source == PaymentSource.MANUAL_UPI
                    ? verifiedUpiByInvoice.get(invoice.getId())
                    : null;

            String method = upi != null ? upi.getUpiMethod().getLabel() : "Razorpay";
            String reference = upi != null ? upi.getUtrReference() : invoice.getRazorpayPaymentId();
            String sourceLabel = source == null ? "UNKNOWN" : source.name();

            return new PaymentHistoryItemDto(
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    invoice.getDescription(),
                    invoice.getEngagement().getId(),
                    invoice.getEngagement().getTitle(),
                    invoice.getEngagement().getClient().getFullName(),
                    invoice.getEngagement().getClient().getEmail(),
                    invoice.getAmount(),
                    invoice.getCurrency(),
                    sourceLabel,
                    method,
                    reference,
                    invoice.getPaidAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx() {
        List<PaymentHistoryItemDto> history = list();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Payment History");
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 12));

            CellStyle header = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);

            String[] headers = {
                    "Invoice", "Description", "Project", "Client", "Client Email", "Amount", "Currency",
                    "Source", "Payment Method", "Reference", "Paid At", "Invoice ID", "Engagement ID"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(header);
            }

            for (int r = 0; r < history.size(); r++) {
                PaymentHistoryItemDto p = history.get(r);
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(value(p.invoiceNumber()));
                row.createCell(1).setCellValue(value(p.description()));
                row.createCell(2).setCellValue(value(p.projectTitle()));
                row.createCell(3).setCellValue(value(p.clientName()));
                row.createCell(4).setCellValue(value(p.clientEmail()));
                row.createCell(5).setCellValue(p.amount() == null ? 0d : p.amount().doubleValue());
                row.createCell(6).setCellValue(value(p.currency()));
                row.createCell(7).setCellValue(value(p.paymentSource()));
                row.createCell(8).setCellValue(value(p.paymentMethod()));
                row.createCell(9).setCellValue(value(p.paymentReference()));
                row.createCell(10).setCellValue(p.paidAt() == null ? "" : p.paidAt().format(EXCEL_DATE));
                row.createCell(11).setCellValue(p.id().toString());
                row.createCell(12).setCellValue(p.engagementId().toString());
            }

            int[] widths = {16, 36, 32, 24, 34, 14, 10, 18, 20, 26, 22, 38, 38};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate payment history Excel export", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
