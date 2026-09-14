package com.neelastack.controller;

import com.neelastack.dto.inquiry.PublicQuotationDto;
import com.neelastack.dto.inquiry.QuotationResponseRequest;
import com.neelastack.service.QuotationService;
import com.neelastack.service.QuotationPdfService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/quotations")
@RequiredArgsConstructor
@Tag(name = "Public quotations", description = "Secure-link access for clients to review and accept/reject a quotation — no login required")
public class PublicQuotationController {

    private final QuotationService quotationService;
    private final QuotationPdfService quotationPdfService;

    @GetMapping("/{token}")
    public PublicQuotationDto get(@PathVariable String token) {
        return quotationService.getByPublicToken(token);
    }

    @PostMapping("/{token}/respond")
    public PublicQuotationDto respond(@PathVariable String token, @Valid @RequestBody QuotationResponseRequest request) {
        return quotationService.respondToQuotation(token, request.accept(), request.reason());
    }

    @GetMapping(value = "/{token}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        byte[] pdf = quotationPdfService.generateByToken(token);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("neelastack-proposal.pdf").build().toString())
                .body(pdf);
    }
}
