package com.neelastack.controller;

import com.neelastack.dto.inquiry.InquiryDto;
import com.neelastack.dto.inquiry.InquiryStatusUpdateRequest;
import com.neelastack.dto.inquiry.QuotationDto;
import com.neelastack.dto.inquiry.QuotationRequest;
import com.neelastack.service.InquiryService;
import com.neelastack.service.QuotationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin — leads & quotations", description = "Requires ROLE_ADMIN")
public class AdminInquiryController {

    private final InquiryService inquiryService;
    private final QuotationService quotationService;

    @GetMapping("/inquiries")
    public Page<InquiryDto> listInquiries(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return inquiryService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/inquiries/{id}")
    public InquiryDto getInquiry(@PathVariable UUID id) {
        return inquiryService.get(id);
    }

    @PatchMapping("/inquiries/{id}/status")
    public InquiryDto updateStatus(@PathVariable UUID id, @Valid @RequestBody InquiryStatusUpdateRequest request) {
        return inquiryService.updateStatus(id, request.status());
    }

    @GetMapping("/inquiries/{id}/quotations")
    public List<QuotationDto> quotationsForInquiry(@PathVariable UUID id) {
        return quotationService.listForInquiry(id);
    }

    /** Same PDF that was emailed to the lead — regenerated on demand for the dashboard,
     *  never stored, so it's always built from the current state of the inquiry row. */
    @GetMapping("/inquiries/{id}/executive-report")
    public ResponseEntity<byte[]> executiveReport(@PathVariable UUID id) {
        byte[] pdf = inquiryService.generateExecutiveReportPdf(id);
        String fileName = "neelastack-executive-brief-" + id + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(pdf);
    }

    @PostMapping("/quotations")
    public ResponseEntity<QuotationDto> createQuotation(@Valid @RequestBody QuotationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quotationService.create(request));
    }

    @PostMapping("/quotations/{id}/send")
    public QuotationDto sendQuotation(@PathVariable UUID id) {
        return quotationService.send(id);
    }
}
