package com.neelastack.controller;

import com.neelastack.dto.payment.CreateOrderResponse;
import com.neelastack.dto.payment.InvoiceDto;
import com.neelastack.dto.payment.PaymentVerificationRequest;
import com.neelastack.service.InvoiceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
@Tag(name = "Client — invoices & payment", description = "Requires authentication; ownership enforced per engagement")
public class ClientInvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/engagements/{engagementId}/invoices")
    public List<InvoiceDto> list(@PathVariable UUID engagementId) {
        return invoiceService.listForEngagement(engagementId);
    }

    @PostMapping("/invoices/{invoiceId}/checkout")
    public CreateOrderResponse createOrder(@PathVariable UUID invoiceId) {
        return invoiceService.createOrder(invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/verify")
    public InvoiceDto verify(@PathVariable UUID invoiceId, @Valid @RequestBody PaymentVerificationRequest request) {
        return invoiceService.verifyAndConfirmPayment(invoiceId, request);
    }

    @GetMapping(value = "/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID invoiceId) {
        byte[] pdf = invoiceService.generatePdf(invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
