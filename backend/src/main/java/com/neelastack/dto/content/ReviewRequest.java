package com.neelastack.dto.content;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotBlank @Size(max = 120) String authorName,
        @Size(max = 160) String authorTitle,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank String reviewBody,
        boolean published,
        Integer displayOrder
) {}
