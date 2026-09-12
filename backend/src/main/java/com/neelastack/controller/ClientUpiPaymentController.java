package com.neelastack.controller;

import com.neelastack.dto.upi.UpiPaymentMethodDto;
import com.neelastack.dto.upi.UpiSubmissionDto;
import com.neelastack.dto.upi.UpiSubmissionRequest;
import com.neelastack.service.UpiPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Authenticated client — ownership of the target invoice is enforced inside
 *  UpiPaymentService via InvoiceService#checkAccess, same pattern as every other
 *  client-facing engagement-scoped endpoint in this codebase. */
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
@Tag(name = "Client — direct UPI payments", description = "Pay an invoice directly by UPI QR, no gateway commission")
public class ClientUpiPaymentController {

    private final UpiPaymentService upiPaymentService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping("/upi-methods")
    public List<UpiPaymentMethodDto> listActiveMethods() {
        return upiPaymentService.listActive();
    }

    @GetMapping("/invoices/{invoiceId}/upi-submissions")
    public List<UpiSubmissionDto> submissionsForInvoice(@PathVariable UUID invoiceId) {
        return upiPaymentService.listForInvoice(invoiceId);
    }

    @SneakyThrows
    @PostMapping(value = "/invoices/{invoiceId}/upi-submissions", consumes = "multipart/form-data")
    public ResponseEntity<UpiSubmissionDto> submit(@PathVariable UUID invoiceId,
                                                    @RequestPart("request") String requestJson,
                                                    @RequestPart(value = "screenshot", required = false) MultipartFile screenshot) {
        UpiSubmissionRequest request = objectMapper.readValue(requestJson, UpiSubmissionRequest.class);
        Set<ConstraintViolation<UpiSubmissionRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(upiPaymentService.submit(invoiceId, request, screenshot));
    }
}
