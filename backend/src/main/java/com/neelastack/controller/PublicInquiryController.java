package com.neelastack.controller;

import com.neelastack.dto.inquiry.InquiryDto;
import com.neelastack.dto.inquiry.InquiryRequest;
import com.neelastack.service.InquiryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiries", description = "Public lead-capture endpoint")
public class PublicInquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<InquiryDto> submit(@Valid @RequestBody InquiryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inquiryService.submit(request));
    }
}
