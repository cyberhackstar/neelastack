package com.neelastack.controller;

import com.neelastack.dto.inquiry.EstimatorRequest;
import com.neelastack.dto.inquiry.EstimatorResponseDto;
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
 * The project estimator (master prompt section 21). Distinct from
 * {@link PublicInquiryController} because it carries a much richer, structured payload —
 * both create an {@code Inquiry}, but this one also returns a preliminary estimate range.
 */
@RestController
@RequestMapping("/api/v1/public/estimator")
@RequiredArgsConstructor
@Tag(name = "Estimator", description = "Public multi-step project estimator")
public class PublicEstimatorController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<EstimatorResponseDto> submit(@Valid @RequestBody EstimatorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inquiryService.submitEstimator(request));
    }
}
