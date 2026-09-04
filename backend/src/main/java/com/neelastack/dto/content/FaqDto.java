package com.neelastack.dto.content;

import lombok.Builder;

import java.util.UUID;

@Builder
public record FaqDto(
        UUID id,
        String question,
        String answer,
        Integer displayOrder
) {}
