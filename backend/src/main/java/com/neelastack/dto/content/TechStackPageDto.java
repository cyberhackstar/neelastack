package com.neelastack.dto.content;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record TechStackPageDto(
        UUID id,
        String slug,
        String h1Title,
        String metaTitle,
        String metaDescription,
        String intro,
        String bodyContent,
        String primaryStack,
        String secondaryStack,
        String targetIndustry,
        List<String> useCases,
        String startingPrice,
        Integer displayOrder,
        boolean published
) {}
