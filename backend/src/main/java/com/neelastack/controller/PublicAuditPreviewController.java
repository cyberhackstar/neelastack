package com.neelastack.controller;

import com.neelastack.dto.inquiry.AuditPreviewRequest;
import com.neelastack.dto.inquiry.AuditPreviewResultDto;
import com.neelastack.dto.inquiry.AuditUnlockRequest;
import com.neelastack.dto.inquiry.AuditUnlockResultDto;
import com.neelastack.service.ArchitectureRiskScoringService;
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
 * The "Instant Architecture Risk Score" lead magnet at /audit-preview (module 1 of
 * the Client Acquisition & High-Ticket Conversion Engine). Two deliberately separate
 * endpoints: /score is free and anonymous (no Inquiry created, nothing persisted);
 * /unlock requires name/email/company and is what actually feeds InquiryService.
 */
@RestController
@RequestMapping("/api/v1/public/audit-preview")
@RequiredArgsConstructor
@Tag(name = "Audit preview", description = "Instant architecture risk score lead magnet")
public class PublicAuditPreviewController {

    private final ArchitectureRiskScoringService architectureRiskScoringService;
    private final InquiryService inquiryService;

    @PostMapping("/score")
    public AuditPreviewResultDto score(@Valid @RequestBody AuditPreviewRequest request) {
        ArchitectureRiskScoringService.PreviewResult result =
                architectureRiskScoringService.preview(request.techStack(), request.bottlenecks());

        return AuditPreviewResultDto.builder()
                .riskScore(result.riskScore())
                .riskLevel(result.riskLevel())
                .teaserFindings(result.teaserFindings())
                .lockedFindingsCount(result.lockedFindingsCount())
                .disclaimer("A self-assessment score based on what you selected, not a scan of your actual "
                        + "code. Unlock the full breakdown with your work email for the complete findings and "
                        + "recommendations.")
                .build();
    }

    @PostMapping("/unlock")
    public ResponseEntity<AuditUnlockResultDto> unlock(@Valid @RequestBody AuditUnlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inquiryService.submitAuditPreview(request));
    }
}
