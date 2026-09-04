package com.neelastack.controller;

import com.neelastack.dto.inquiry.ArchitectureReviewRequest;
import com.neelastack.dto.inquiry.InquiryDto;
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

/**
 * The "Already have an application?" free architecture review lead magnet
 * (master prompt section 22).
 */
@RestController
@RequestMapping("/api/v1/public/architecture-review")
@RequiredArgsConstructor
@Tag(name = "Architecture review", description = "Public architecture-review lead magnet")
public class PublicArchitectureReviewController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<InquiryDto> submit(@Valid @RequestBody ArchitectureReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inquiryService.submitArchitectureReview(request));
    }
}
