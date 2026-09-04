package com.neelastack.controller;

import com.neelastack.dto.inquiry.PublicQuotationDto;
import com.neelastack.dto.inquiry.QuotationResponseRequest;
import com.neelastack.service.QuotationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/quotations")
@RequiredArgsConstructor
@Tag(name = "Public quotations", description = "Secure-link access for clients to review and accept/reject a quotation — no login required")
public class PublicQuotationController {

    private final QuotationService quotationService;

    @GetMapping("/{token}")
    public PublicQuotationDto get(@PathVariable String token) {
        return quotationService.getByPublicToken(token);
    }

    @PostMapping("/{token}/respond")
    public PublicQuotationDto respond(@PathVariable String token, @Valid @RequestBody QuotationResponseRequest request) {
        return quotationService.respondToQuotation(token, request.accept(), request.reason());
    }
}
