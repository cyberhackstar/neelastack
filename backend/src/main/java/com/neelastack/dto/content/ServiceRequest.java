package com.neelastack.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 160) String slug,
        @NotBlank @Size(max = 300) String summary,
        String description,
        String icon,
        String startingPrice,
        Integer displayOrder,
        boolean published
) {}
