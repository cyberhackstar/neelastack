package com.neelastack.dto.testimonial;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Client-submitted testimonial content. Submitting this request IS the client's
 * consent to be considered for publication — the form must say so plainly; the
 * review still lands unpublished (Review.published = false) pending an admin's
 * explicit moderation decision, so nothing goes live without a second human check.
 */
public record TestimonialSubmissionRequest(
        @Size(max = 160) String authorTitle,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = 4000) String reviewBody,
        @Size(max = 300) String videoUrl
) {}
