package com.neelastack.controller;

import com.neelastack.dto.testimonial.TestimonialRequestPublicDto;
import com.neelastack.dto.testimonial.TestimonialSubmissionRequest;
import com.neelastack.service.TestimonialService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public, tokenized post-invoice testimonial flow (Client Acquisition & High-Ticket
 * Conversion Engine, module 4). No authentication — the token itself, generated
 * server-side and delivered only to the paying client's own email address, is the
 * access control. See TestimonialService for the atomicity guarantees.
 */
@RestController
@RequestMapping("/api/v1/public/testimonials")
@RequiredArgsConstructor
@Tag(name = "Public testimonials", description = "Tokenized post-invoice testimonial capture")
public class PublicTestimonialController {

    private final TestimonialService testimonialService;

    @GetMapping("/{token}")
    public TestimonialRequestPublicDto get(@PathVariable String token) {
        return testimonialService.getByToken(token);
    }

    @PostMapping("/{token}")
    public ResponseEntity<Void> submit(@PathVariable String token,
                                        @Valid @RequestBody TestimonialSubmissionRequest request) {
        testimonialService.submit(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
