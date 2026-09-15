package com.neelastack.controller;

import com.neelastack.dto.inquiry.BusinessAuditPreviewRequest;
import com.neelastack.dto.inquiry.BusinessAuditPreviewResultDto;
import com.neelastack.dto.inquiry.BusinessAuditUnlockRequest;
import com.neelastack.dto.inquiry.BusinessAuditUnlockResultDto;
import com.neelastack.service.BusinessAuditService;
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
@RequestMapping("/api/v1/public/business-audit")
@RequiredArgsConstructor
@Tag(name = "Business audit", description = "Free business digital readiness lead magnet")
public class PublicBusinessAuditController {
    private final BusinessAuditService businessAuditService;

    @PostMapping("/score")
    public BusinessAuditPreviewResultDto score(@Valid @RequestBody BusinessAuditPreviewRequest request) {
        return businessAuditService.preview(request);
    }

    @PostMapping("/unlock")
    public ResponseEntity<BusinessAuditUnlockResultDto> unlock(@Valid @RequestBody BusinessAuditUnlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessAuditService.unlock(request));
    }
}
