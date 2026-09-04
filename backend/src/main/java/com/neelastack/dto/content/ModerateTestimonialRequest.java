package com.neelastack.dto.content;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Admin decision on a client-submitted testimonial (see AdminContentController
 *  #moderateTestimonial / ProjectService#moderateTestimonial). projectId is
 *  optional -- omit it to publish without (yet) attaching to a case study. */
public record ModerateTestimonialRequest(
        UUID projectId,
        @NotNull Boolean published
) {}
