package com.neelastack.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaqRequest(
        @NotBlank @Size(max = 300) String question,
        @NotBlank String answer,
        Integer displayOrder
) {}
