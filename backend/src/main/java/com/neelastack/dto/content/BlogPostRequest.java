package com.neelastack.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BlogPostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 220) String slug,
        @NotBlank @Size(max = 320) String excerpt,
        @NotBlank String content,
        String coverImageUrl,
        String authorName,
        String category,
        List<String> tags,
        @NotBlank @Size(max = 160) String metaTitle,
        @NotBlank @Size(max = 320) String metaDescription,
        boolean published
) {}
