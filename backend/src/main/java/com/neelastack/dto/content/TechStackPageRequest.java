package com.neelastack.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TechStackPageRequest(
        @NotBlank @Size(max = 160) String slug,
        @NotBlank @Size(max = 160) String h1Title,
        @NotBlank @Size(max = 160) String metaTitle,
        @NotBlank @Size(max = 300) String metaDescription,
        @NotBlank String intro,
        @NotBlank String bodyContent,
        @NotBlank @Size(max = 200) String primaryStack,
        @Size(max = 200) String secondaryStack,
        @Size(max = 120) String targetIndustry,
        List<@Size(max = 200) String> useCases,
        @Size(max = 40) String startingPrice,
        Integer displayOrder,
        boolean published
) {}
