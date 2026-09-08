package com.neelastack.service;

import com.neelastack.entity.Inquiry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Dispatches the executive lead brief off the request thread. Public lead-capture endpoints
 * must acknowledge a valid submission promptly; PDF generation and SMTP are downstream
 * side effects and must never add their latency to the visitor-facing request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutiveReportDispatchService {

    private final ExecutiveReportPdfService executiveReportPdfService;
    private final EmailService emailService;

    @Async
    public void dispatch(Inquiry inquiry) {
        try {
            byte[] pdf = executiveReportPdfService.generate(inquiry);
            String fileName = "neelastack-executive-brief-" + inquiry.getId() + ".pdf";
            emailService.sendExecutiveReport(inquiry, pdf, fileName);
        } catch (Exception ex) {
            log.error("Failed to generate/send executive report for inquiry {}: {}",
                    inquiry.getId(), ex.getMessage(), ex);
        }
    }
}
