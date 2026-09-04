package com.neelastack.dto.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 180) String slug,
        @NotBlank @Size(max = 300) String summary,
        String problemStatement,
        String solution,
        String outcome,
        String coverImageUrl,
        List<String> techStack,
        String liveUrl,
        String repoUrl,
        boolean featured,
        boolean published,
        Integer displayOrder,
        List<String> serviceCategories,
        List<String> keyMetrics
) {}
