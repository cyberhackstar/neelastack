package com.neelastack.dto.testimonial;

import com.neelastack.entity.TestimonialRequestStatus;
import lombok.Builder;

/** What the unauthenticated /testimonial/{token} page needs to render the form
 *  (or an "already used" state) — no invoice amounts, IDs, or other internal data. */
@Builder
public record TestimonialRequestPublicDto(
        String clientName,
        String projectTitle,
        TestimonialRequestStatus status
) {}
