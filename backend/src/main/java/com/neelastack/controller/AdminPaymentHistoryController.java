package com.neelastack.controller;

import com.neelastack.dto.payment.PaymentHistoryItemDto;
import com.neelastack.service.PaymentHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payments/history")
@RequiredArgsConstructor
@Tag(name = "Admin — payment history", description = "Completed payment history and Excel export")
public class AdminPaymentHistoryController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PaymentHistoryService paymentHistoryService;

    @GetMapping
    public List<PaymentHistoryItemDto> list() {
        return paymentHistoryService.list();
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export() {
        byte[] bytes = paymentHistoryService.exportXlsx();
        String filename = "neelastack-payment-history-" + LocalDate.now() + ".xlsx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
